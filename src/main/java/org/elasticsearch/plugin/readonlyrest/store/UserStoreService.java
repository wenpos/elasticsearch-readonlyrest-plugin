package org.elasticsearch.plugin.readonlyrest.store;

import com.carrotsearch.hppc.ObjectHashSet;
import com.carrotsearch.hppc.ObjectLongHashMap;
import com.carrotsearch.hppc.ObjectLongMap;
import com.carrotsearch.hppc.cursors.ObjectCursor;
import com.carrotsearch.hppc.cursors.ObjectLongCursor;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.ValidationException;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Provider;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.AbstractRunnable;
import org.elasticsearch.gateway.GatewayService;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.engine.DocumentMissingException;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.plugin.readonlyrest.builder.request.user.PutUserRequest;
import org.elasticsearch.plugin.readonlyrest.client.ClientWithUser;
import org.elasticsearch.plugin.readonlyrest.util.JsonUtil;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.RemoteTransportException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class UserStoreService extends AbstractComponent implements ClusterStateListener {

    private final AtomicReference<State> state = new AtomicReference(State.INITIALIZED);
    private final ObjectLongHashMap<String> versionMap = new ObjectLongHashMap();
    private final List<ChangeListener> listeners = new CopyOnWriteArrayList();
    private Client client;
    private final Provider<Client> clientProvider;
    private int scrollSize;
    private TimeValue scrollKeepAlive;
    private volatile boolean gateIndexExists = false;
    private ThreadPool.Cancellable userPoller;
    private final ThreadPool threadPool;

    @Inject
    public UserStoreService(Settings settings, Provider<Client> clientProvider, ThreadPool threadPool) {
        super(settings);
        this.clientProvider = clientProvider;
        this.threadPool = threadPool;
    }

    public void putUser(PutUserRequest request, ActionListener<Boolean> listener) {
        try {
            if (request.getPassword() == null) {

                updateUserWithoutPassword(request, listener);
            } else {
                indexUser(request, listener);
            }
        } catch (Exception e) {
            this.logger.error("unable to put user [{}]", e, new Object[]{request.getUsername()});
            listener.onFailure(e);
        }
    }

    private void indexUser(final PutUserRequest request, final ActionListener<Boolean> listener) {
        assert (request.getPassword() != null);
        IndexRequestBuilder userIndexBuilder = this.client.prepareIndex(".security", "user", request.getUsername())
                .setSource(new Object[]{"username",request.getUsername(), "roles",request.getRoles(), "password",request.getPassword()})
                .setRefresh(request.isRefresh());
        userIndexBuilder.execute(new ActionListener<IndexResponse>() {
                    public void onResponse(IndexResponse indexResponse) {
                        if (indexResponse.isCreated()) {
                            System.out.println("****是否创***: yes" + indexResponse.isCreated() );
                            listener.onResponse(Boolean.valueOf(indexResponse.isCreated()));
                            return;
                        }
                        else {
                            System.out.println("****是否创***: no");
                            listener.onResponse(Boolean.valueOf(false));
                            return;
                        }
                        // TODO: 2016/11/5 清理缓存的用户信息
//                UserStoreService.this.clearRealmCache(request.getUsername(), listener, Boolean.valueOf(indexResponse.isCreated()));
                    }

                    public void onFailure(Throwable e) {
                        listener.onFailure(e);
                    }
                });
    }

    //更新用户信息
    private void updateUserWithoutPassword(final PutUserRequest request, final ActionListener<Boolean> listener) {

        assert (request.getPassword() == null);

        this.client.prepareUpdate(".security", "user", request.getUsername())
                .setDoc(new Object[]{"zte", "zte456", "all"})
                .setRefresh(request.isRefresh())
                .execute(new ActionListener<UpdateResponse>() {
                    public void onResponse(UpdateResponse updateResponse) {
                        assert (!(updateResponse.isCreated()));
                        if (updateResponse.isCreated()) {
                            listener.onResponse(Boolean.valueOf(updateResponse.isCreated()));
                        }
                        // TODO: 2016/11/5 更新成功，清理用户缓存，或重新加载用户信息，创建缓存
//                        UserStoreService.this.clearRealmCache(this.val$putUserRequest.username(), this.val$listener, Boolean.valueOf(false));
                    }

                    public void onFailure(Throwable e) {
                        Throwable cause = e;
                        if (e instanceof RemoteTransportException) {
                            cause = ExceptionsHelper.unwrapCause(e);
                            if ((!(cause instanceof IndexNotFoundException)) && (!(cause instanceof DocumentMissingException))) {
                                listener.onFailure(e);
                                return;
                            }

                        }

                        UserStoreService.this.logger.error("failed to update user document with username [{}]", cause, new Object[]{request.getUsername()});
                        ValidationException validationException = new ValidationException();
                        validationException.addValidationError("password must be specified unless you are updating an existing user");
                        listener.onFailure(validationException);
                    }
                });

    }

    public State state() {
        return ((State) this.state.get());
    }

    public static void createIndex(Client client,String index) {
        if (isIndexExist(client, index)) {
            return;
        }
        client.admin().indices().prepareCreate(index).execute().actionGet();
    }

    private static boolean isIndexExist(Client client, String index) {
        return client.admin().indices().prepareExists(index).execute().actionGet().isExists();
    }

    public void start() {
        try {
            //如果本服务状态初始化完成
            if (this.state.compareAndSet(State.INITIALIZED, State.STARTING)) {
                // TODO: 2016/11/4 用户操作权限的校验，该用户只能操作自己的用户信息
                this.client = new ClientWithUser((Client) this.clientProvider.get());
                this.logger.info("####初始化CLIENT####");
                // TODO: 2016/11/4 这两个参数的作用？--用于scroll查询，参见方法collectUsersAndVersions
                //两个配置参数，配置则取配置的值，否则取默认值
                this.scrollSize = this.settings.getAsInt("shield.authc.native.scroll.size", Integer.valueOf(1000)).intValue();
                this.scrollKeepAlive = this.settings.getAsTime("shield.authc.native.scroll.keep_alive", TimeValue.timeValueSeconds(10L));

                //创建index .security
                createIndex(this.client,".security");

                UserStorePoller poller = new UserStorePoller();
                try {
                    //启动轮询，同步User缓存和数据库
                    poller.doRun();
                } catch (Exception e) {
                    this.logger.warn("failed to do initial poll of users", e, new Object[0]);
                }
                this.userPoller = this.threadPool.scheduleWithFixedDelay(poller, this.settings.getAsTime("shield.authc.native.reload.interval", TimeValue.timeValueSeconds(30L)), "generic");
                this.state.set(State.STARTED);
            }
        } catch (Exception e) {
            this.logger.error("failed to start native user store", e, new Object[0]);
            this.state.set(State.FAILED);
        }
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        //监听.security这个index的状态
        boolean exists = event.state().metaData().indices().get(".security") != null;

        if ((exists) && (event.state().routingTable().index(".security").allPrimaryShardsActive())) {
            this.logger.debug("security index [{}] all primary shards started, so polling can start", new Object[]{".security"});

            this.gateIndexExists = true;
        } else {
            this.gateIndexExists = false;
        }
    }

    public void stop() {
        if (!(this.state.compareAndSet(State.STARTED, State.STOPPING))) return;
        try {
            this.userPoller.cancel();
        } catch (Throwable t) {
            throw t;
        } finally {
            this.state.set(State.STOPPED);
        }
    }

    public boolean canStart(ClusterState state, boolean master) {
        if (state() != State.INITIALIZED) {
            return false;
        }

        if (state.blocks().hasGlobalBlock(GatewayService.STATE_NOT_RECOVERED_BLOCK)) {
            this.logger.debug("native users store waiting until gateway has recovered from disk", new Object[0]);
            return false;
        }

        // TODO: 2016/11/9 这里的模板有什么用？
//        if (state.metaData().templates().get("security-index-template") == null) {
//            this.logger.debug("native users template [{}] does not exist, so service cannot start", new Object[]{"security-index-template"});
//            return false;
//        }

        IndexMetaData metaData = state.metaData().index(".security");
        if (metaData == null) {
            this.logger.debug("security index [{}] does not exist, so service can start", new Object[]{".security"});
            return true;
        }

        if (state.routingTable().index(".security").allPrimaryShardsActive()) {
            this.logger.debug("security index [{}] all primary shards started, so service can start", new Object[]{".security"});
            return true;
        }
        return false;
    }

    public static enum State {
        INITIALIZED, STARTING, STARTED, STOPPING, STOPPED, FAILED;
    }

    public class UserStorePoller extends AbstractRunnable {

        // TODO: 2016/11/5 这的线程用于同步缓存versionMap和.securit中的index信息
        @Override
        protected void doRun() throws Exception {
            Client client = UserStoreService.this.client;
            if (isStopped()) {
                return;
            }
            if (!(UserStoreService.this.gateIndexExists)) {
                UserStoreService.this.logger.error("cannot poll for user changes since security index [{}] does not exist", new Object[]{".security"});
                return;
            }

            UserStoreService.this.logger.trace("starting polling of user index to check for changes", new Object[0]);

            ObjectHashSet knownUsers = new ObjectHashSet(UserStoreService.this.versionMap.keys());
            List changedUsers = new ArrayList();

            ObjectLongMap userName2Version = collectUsersAndVersions(client);
            Iterator iterator = userName2Version.iterator();
            while (iterator.hasNext()) {
                ObjectLongCursor cursor = (ObjectLongCursor) iterator.next();
                String username = (String) cursor.key;
                long latestVersion = cursor.value;

                if (knownUsers.contains(username)) {
                    long cachedVersion = UserStoreService.this.versionMap.get(username);
                    if (latestVersion != cachedVersion) {
                        //如果.security中的用户version版本号大于缓存区的版本号，就更新缓存区的版本号
                        assert (!(latestVersion <= cachedVersion));
                        UserStoreService.this.versionMap.put(username, latestVersion);
                        changedUsers.add(username);
                    }
                    //如果版本没有变化，则将其移出比较序列knownUsers，剩余的knownUsers将User缓存清理
                    knownUsers.remove(username);
                } else {
                    //缓存区包含数据库.security中存储的user，则将该user信息加入到缓存区
                    UserStoreService.this.versionMap.put(username, latestVersion);
                }

            }

            if (isStopped()) {
                return;
            }

            //剩下的user列表中，如果还有，表示缓存中存在某些，数据库中.security中不存在，
            // 则表示该用户已经删除了，则清除缓存区的该用户信息
            Iterator userIter = knownUsers.iterator();
            while (userIter.hasNext()) {
                String user = (String) ((ObjectCursor) userIter.next()).value;
                UserStoreService.this.versionMap.remove(user);
                changedUsers.add(user);
            }

            if (changedUsers.isEmpty()) {
                return;
            }

            // TODO: 2016/11/5 监听用户变更有什么用？
//            changedUsers = Collections.unmodifiableList(changedUsers);
//            if (UserStoreService.this.logger.isDebugEnabled()) {
//                UserStoreService.this.logger.debug("changes detected for users [{}]", new Object[]{changedUsers});
//            }
//
//            Throwable th = null;
//            for (UserStoreService.ChangeListener listener : UserStoreService.this.listeners) {
//                try {
//                    listener.onUsersChanged(changedUsers);
//                } catch (Throwable t) {
//                    th = ExceptionsHelper.useOrSuppress(th, t);
//                }
//            }

//            ExceptionsHelper.reThrowIfNotNull(th);
        }

        @Override
        public void onFailure(Throwable t) {
            UserStoreService.this.logger.error("error occurred while checking the native users for changes", t, new Object[0]);
        }

        private boolean isStopped() {
            UserStoreService.State state = UserStoreService.this.state();
            return ((state == UserStoreService.State.STOPPED) || (state == UserStoreService.State.STOPPING));
        }

        //从库.security查询出所有user2version的信息，返回map
        private ObjectLongMap<String> collectUsersAndVersions(Client client) {
            // TODO: 2016/11/5 这里采用的scroll查询，为什么？有什么优势？
            ClearScrollRequest clearScrollRequest;
            ObjectLongMap map = new ObjectLongHashMap();
            SearchResponse response = null;
            try {
                client.admin().indices().prepareRefresh(new String[]{".security"}).get();
                SearchRequest request = client.prepareSearch(new String[]{".security"})
                        .setScroll(UserStoreService.this.scrollKeepAlive)
                        .setQuery(QueryBuilders.typeQuery("user"))
                        .setSize(UserStoreService.this.scrollSize)
                        .setVersion(true)
                        .setFetchSource(false)
                        .request();

                response = (SearchResponse) client.search(request).actionGet();

                boolean keepScrolling = response.getHits().getHits().length > 0;
                while (keepScrolling) {
                    if (isStopped()) {
                        ObjectLongHashMap localObjectLongHashMap = new ObjectLongHashMap();
                        return localObjectLongHashMap;
                    }
                    for (SearchHit hit : response.getHits().getHits()) {
                        String username = hit.id();
                        long version = hit.version();
                        map.put(username, version);
                    }
                    SearchScrollRequest scrollRequest = (SearchScrollRequest) client.prepareSearchScroll(response.getScrollId()).setScroll(UserStoreService.this.scrollKeepAlive).request();
                    response = (SearchResponse) client.searchScroll(scrollRequest).actionGet();
                    keepScrolling = response.getHits().getHits().length > 0;
                }

            } catch (IndexNotFoundException e) {
                UserStoreService.this.logger.error("security index does not exist", e, new Object[0]);
            } finally {
                if (response != null) {
                    clearScrollRequest = (ClearScrollRequest) client.prepareClearScroll().addScrollId(response.getScrollId()).request();
                    client.clearScroll(clearScrollRequest).actionGet();
                }
            }
            return map;
        }
    }

    static abstract interface ChangeListener {
        public abstract void onUsersChanged(List<String> paramList);
    }

}
