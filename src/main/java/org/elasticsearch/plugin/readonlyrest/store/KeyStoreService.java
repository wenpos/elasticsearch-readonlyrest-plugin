package org.elasticsearch.plugin.readonlyrest.store;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.LatchedActionListener;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Provider;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.AbstractRunnable;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.gateway.GatewayService;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.get.GetResult;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.plugin.readonlyrest.builder.request.PutKeyRequest;
import org.elasticsearch.plugin.readonlyrest.client.ClientWithUser;
import org.elasticsearch.plugin.readonlyrest.client.GateClient;
import org.elasticsearch.plugin.readonlyrest.key.KeyDescriptor;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.threadpool.ThreadPool;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class KeyStoreService extends AbstractComponent implements ClusterStateListener {

    private final AtomicReference<State> state = new AtomicReference(State.INITIALIZED);
    private final Provider<Client> clientProvider;
    private GateClient gateClient;
    private Client client;
    private int scrollSize;
    private TimeValue scrollKeepAlive;
    private volatile boolean shieldIndexExists = false;
    private final ThreadPool threadPool;
    private ThreadPool.Cancellable keysPoller;
    private final ConcurrentMap<String, KeyAndVersion> keyCache = new ConcurrentHashMap();

    public KeyStoreService(Settings settings, Provider<Client> clientProvider, ThreadPool threadPool) {
        super(settings);
        this.clientProvider = clientProvider;
        this.threadPool = threadPool;
    }

    public State state() {
        return ((State) this.state.get());
    }

    public boolean canStart(ClusterState clusterState, boolean master) {
        if (state() != State.INITIALIZED) {
            return false;
        }

        if (clusterState.blocks().hasGlobalBlock(GatewayService.STATE_NOT_RECOVERED_BLOCK)) {
            this.logger.debug("native Keys store waiting until gateway has recovered from disk", new Object[0]);
            return false;
        }

        if (clusterState.metaData().templates().get("security-index-template") == null) {
            this.logger.debug("native Keys template [{}] does not exist, so service cannot start", new Object[]{"security-index-template"});
            return false;
        }
        return true;
    }

    private KeyAndVersion getRoleAndVersion(final String keyName) {
        KeyAndVersion roleAndVersion = null;
        final AtomicReference getRef = new AtomicReference();
        CountDownLatch latch = new CountDownLatch(1);
        try {
            roleAndVersion = (KeyAndVersion) this.keyCache.get(keyName);
            if (roleAndVersion == null) {
                this.logger.debug("attempting to load role [{}] from index", new Object[]{keyName});
                executeGetRoleRequest(keyName, new LatchedActionListener(new ActionListener<GetResponse>() {
                    public void onResponse(GetResponse role) {
                        getRef.set(role);
                    }

                    public void onFailure(Throwable t) {
                        if (t instanceof IndexNotFoundException)
                            KeyStoreService.this.logger.trace("failed to retrieve role [{}] since security index does not exist", t, new Object[]{keyName});
                        else
                            KeyStoreService.this.logger.error("failed to retrieve role [{}]", t, new Object[]{keyName});
                    }
                }
                        , latch));
                try {
                    latch.await(30L, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    this.logger.error("timed out retrieving role [{}]", new Object[]{keyName});
                }

                GetResponse response = (GetResponse) getRef.get();
                if (response == null) {
                    return null;
                }

                KeyDescriptor descriptor = transformRole(response);
                if (descriptor == null) {
                    return null;
                }
                this.logger.debug("loaded role [{}] from index with version [{}]", new Object[]{keyName, Long.valueOf(response.getVersion())});
                roleAndVersion = new KeyAndVersion(descriptor, response.getVersion());

                KeyAndVersion existing = (KeyAndVersion) this.keyCache.putIfAbsent(keyName, roleAndVersion);
                if (existing == null) {
                    return roleAndVersion;
                }
                while (true) {
                    if (roleAndVersion.getVersion() <= existing.getVersion()) {
                        break;
                    }
                    this.logger.trace("role [{}] is in cache with version [{}], trying to update to version [{}]", new Object[]{keyName, Long.valueOf(existing.getVersion()), Long.valueOf(roleAndVersion.getVersion())});

                    if (this.keyCache.replace(keyName, existing, roleAndVersion)) {
                        return roleAndVersion;
                    }

                    existing = (KeyAndVersion) this.keyCache.get(keyName);
                    if (existing == null)
                        break;
                    this.logger.trace("failed to replace role [{}] in cache. replacement version [{}] existing version [{}]", new Object[]{keyName, Long.valueOf(roleAndVersion.getVersion()), Long.valueOf(existing.getVersion())});
                }

                this.logger.trace("failed to replace role [{}]. it was removed from the cache", new Object[]{keyName});
//                return roleAndVersion;

                if (roleAndVersion.getVersion() == existing.getVersion()) {
                    this.logger.trace("role [{}] is already in cache with version [{}]", new Object[]{keyName, Long.valueOf(roleAndVersion.getVersion())});
                    return roleAndVersion;
                }

                assert (!(existing.getVersion() <= roleAndVersion.getVersion()));
                this.logger.trace("role [{}] has cached value with version [{}] newer than the retrieved version [{}]", new Object[]{keyName, Long.valueOf(existing.getVersion()), Long.valueOf(roleAndVersion.getVersion())});

                return existing;
            }
        } catch (RuntimeException e) {
            this.logger.error("could not get or load value from cache for role [{}]", e, new Object[]{keyName});
        }

        return roleAndVersion;
    }

    private void executeGetRoleRequest(String role, ActionListener<GetResponse> listener) {
        try {
            GetRequest request = (GetRequest) this.client.prepareGet(".security", "role", role).request();
            this.client.get(request, listener);
        } catch (IndexNotFoundException e) {
            this.logger.trace("unable to retrieve role [{}] since security index does not exist", e, new Object[]{role});
            listener.onResponse(new GetResponse(new GetResult(".security", "role", role, -1L, false, null, null)));
        } catch (Exception e) {
            this.logger.error("unable to retrieve role", e, new Object[0]);
            listener.onFailure(e);
        }
    }

    @Nullable
    private KeyDescriptor transformRole(GetResponse response) {
        if (!(response.isExists())) {
            return null;
        }
        return transformRole(response.getId(), response.getSourceAsBytesRef());
    }

    @Nullable
    private KeyDescriptor transformRole(String name, BytesReference sourceBytes) {
        try {
            return KeyDescriptor.parse(name, sourceBytes);
        } catch (Exception e) {
            this.logger.error("error in the format of data for role [{}]", e, new Object[]{name});
        }
        return null;
    }

    public void clusterChanged(ClusterChangedEvent event) {
        boolean exists = event.state().metaData().indices().get(".security") != null;

        if ((exists) && (event.state().routingTable().index(".security").allPrimaryShardsActive())) {
            this.logger.debug("security index [{}] all primary shards started, so polling can start", new Object[]{".security"});

            this.shieldIndexExists = true;
        } else {
            this.shieldIndexExists = false;
        }
    }

    public void putKey(final PutKeyRequest request, KeyDescriptor keyDescriptor, final ActionListener<Boolean> listener) {
        if (state() != State.STARTED) {
            this.logger.trace("attempted to put KEY [{}] before service was started", new Object[]{request.getKeyName()});
            listener.onResponse(Boolean.valueOf(false));
        }
        try {
            this.client.prepareIndex(".security", "key", keyDescriptor.getKeyname())
                    .setSource(keyDescriptor.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS))
                    .setRefresh(request.isRefresh())
                    .execute(new ActionListener<IndexResponse>() {
                        public void onResponse(IndexResponse indexResponse) {
                            if (indexResponse.isCreated()) {
                                listener.onResponse(Boolean.valueOf(indexResponse.isCreated()));
                                return;
                            }
//                            KeyStoreService.this.clearRoleCache(this.val$role.getName(), this.val$listener, Boolean.valueOf(indexResponse.isCreated()));
                        }

                        public void onFailure(Throwable e) {
                            KeyStoreService.this.logger.error("failed to put KEY [{}]", e, new Object[]{request.getKeyName()});
                            listener.onFailure(e);
                        }
                    });
        } catch (Exception e) {
            this.logger.error("unable to put KEY [{}]", e, new Object[]{request.getKeyName()});
            listener.onFailure(e);
        }
    }


    public void start() {
        try {
            if (this.state.compareAndSet(State.INITIALIZED, State.STARTING)) {
                this.client = new ClientWithUser((Client) this.clientProvider.get());
                this.gateClient = new GateClient(this.client);
                this.scrollSize = this.settings.getAsInt("shield.authc.native.scroll.size", Integer.valueOf(1000)).intValue();
                this.scrollKeepAlive = this.settings.getAsTime("shield.authc.native.scroll.keep_alive", TimeValue.timeValueSeconds(10L));
                TimeValue pollInterval = this.settings.getAsTime("shield.authc.native.reload.interval", TimeValue.timeValueSeconds(30L));
                this.keysPoller = this.threadPool.scheduleWithFixedDelay(new KeysStorePoller(), pollInterval, "generic");
                this.state.set(State.STARTED);
            }
        } catch (Exception e) {
            this.logger.error("failed to start ESNativeRolesStore", e, new Object[0]);
            this.state.set(State.FAILED);
        }
    }

    public void stop() {
        if (!(this.state.compareAndSet(State.STARTED, State.STOPPING))) return;
        try {
            this.keysPoller.cancel();
        } finally {
            this.state.set(State.STOPPED);
        }
    }


    private class KeysStorePoller extends AbstractRunnable {
        protected void doRun()
                throws Exception {
            ClearScrollRequest clearScrollRequest;
            Client client = KeyStoreService.this.client;
            if (isStopped()) {
                return;
            }
            if (!(KeyStoreService.this.shieldIndexExists)) {
                KeyStoreService.this.logger.trace("cannot poll for role changes since security index [{}] does not exist", new Object[]{".security"});
                return;
            }
            if (KeyStoreService.this.keyCache.isEmpty()) {
                KeyStoreService.this.logger.trace("role cache is empty. skipping execution of poller", new Object[0]);
                return;
            }

            KeyStoreService.this.logger.trace("starting polling of roles index to check for changes", new Object[0]);
            SearchResponse response = null;

            Set<String> existingRoles = new HashSet(KeyStoreService.this.keyCache.keySet());
            try {
                client.admin().indices().prepareRefresh(new String[]{".security"}).get();
                SearchRequest request = client.prepareSearch(new String[]{".security"}).setScroll(KeyStoreService.this.scrollKeepAlive).setQuery(QueryBuilders.typeQuery("role")).setSize(KeyStoreService.this.scrollSize).setFetchSource(false).setVersion(true).request();

                response = (SearchResponse) client.search(request).get();

                boolean keepScrolling = response.getHits().getHits().length > 0;
                while (keepScrolling) {
                    if (isStopped()) {
                        return;
                    }
                    for (SearchHit hit : response.getHits().getHits()) {
                        String roleName = hit.getId();
                        long version = hit.version();
                        boolean hadExisting = existingRoles.remove(roleName);

                        if (!(hadExisting)) {
                            KeyStoreService.this.logger.trace("role [{}] wasn't in cache at start of polling. skipping checks", new Object[]{roleName});
                        } else {
                            KeyAndVersion existing = (KeyAndVersion) KeyStoreService.this.keyCache.get(roleName);
                            if (existing == null) {
                                KeyStoreService.this.logger.trace("role [{}] was in the cache at the start of polling but has been removed. skipping checks", new Object[]{roleName});
                            } else if (version > existing.getVersion()) {
                                KeyStoreService.this.logger.trace("removing role [{}] from cache with version [{}] since version [{}] is in index", new Object[]{roleName, Long.valueOf(existing.getVersion()), Long.valueOf(version)});
                                KeyStoreService.this.keyCache.remove(roleName);
                            } else {
                                KeyStoreService.this.logger.trace("role [{}] does not need to be updated. retrieved version [{}] existing version [{}]", new Object[]{roleName, Long.valueOf(version), Long.valueOf(existing.getVersion())});
                            }
                        }
                    }
                    response = (SearchResponse) client.prepareSearchScroll(response.getScrollId()).setScroll(KeyStoreService.this.scrollKeepAlive).get();
                    keepScrolling = response.getHits().getHits().length > 0;
                }

                if (!(existingRoles.isEmpty())) {
                    KeyStoreService.this.logger.trace("removing roles {} from cache since they no longer exist in the index", new Object[]{existingRoles});
                    for (String roleName : existingRoles)
                        KeyStoreService.this.keyCache.remove(roleName);
                }
            } catch (IndexNotFoundException e) {
                KeyStoreService.this.logger.trace("security index does not exist", e, new Object[0]);
            } finally {
                if (response != null) {
                    clearScrollRequest = (ClearScrollRequest) client.prepareClearScroll().addScrollId(response.getScrollId()).request();
                    client.clearScroll(clearScrollRequest).actionGet();
                }
            }
            KeyStoreService.this.logger.trace("completed polling of roles index", new Object[0]);
        }


        public void onFailure(Throwable t) {
            KeyStoreService.this.logger.error("error occurred while checking the native roles for changes", t, new Object[0]);
        }


        private boolean isStopped() {
            KeyStoreService.State state = KeyStoreService.this.state();
            return ((state == KeyStoreService.State.STOPPED) || (state == KeyStoreService.State.STOPPING));
        }
    }


    private static class KeyAndVersion {
        private final KeyDescriptor keyDescriptor;
        private final long version;


        KeyAndVersion(KeyDescriptor roleDescriptor, long version) {
            Objects.requireNonNull(roleDescriptor);
            this.keyDescriptor = roleDescriptor;
            this.version = version;

        }

        KeyDescriptor getKeyDescriptor() {
            return this.keyDescriptor;
        }

        long getVersion() {
            return this.version;
        }


        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if ((o == null) || (super.getClass() != o.getClass())) {
                return false;
            }

            KeyAndVersion that = (KeyAndVersion) o;

            if (this.version != that.version) {
                return false;
            }
            return this.keyDescriptor.getKeyname().equals(that.keyDescriptor.getKeyname());

        }


        public int hashCode() {
            int result = this.keyDescriptor.hashCode();
            result = 31 * result + (int) (this.version ^ this.version >>> 32);
            return result;
        }

    }

    public static enum State {
        INITIALIZED, STARTING, STARTED, STOPPING, STOPPED, FAILED;
    }
}
