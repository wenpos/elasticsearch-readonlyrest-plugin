package org.elasticsearch.plugin.readonlyrest.life;

import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.component.LifecycleListener;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.AbstractRunnable;
import org.elasticsearch.plugin.readonlyrest.store.KeyStoreService;
import org.elasticsearch.plugin.readonlyrest.store.UserStoreService;
import org.elasticsearch.threadpool.ThreadPool;

public class GateLifeCycleService extends AbstractComponent implements ClusterStateListener {
    private final Settings settings;
    private final ThreadPool threadPool;
    private final UserStoreService userStore;
    private final KeyStoreService keyStore;

    @Inject
    public GateLifeCycleService(Settings settings, ClusterService clusterService, Settings settings1, ThreadPool threadPool, UserStoreService userStore, KeyStoreService keyStore) {
        super(settings);
        this.settings = settings1;
        this.threadPool = threadPool;
        this.userStore = userStore;
        this.keyStore = keyStore;
        clusterService.add(this);
        clusterService.add(userStore);
        clusterService.addLifecycleListener(new LifecycleListener() {
            @Override
            public void beforeStop() {
                GateLifeCycleService.this.stop();
            }

            @Override
            public void beforeClose() {
                GateLifeCycleService.this.close();
            }
        });
    }

    private void close() {
        //nothing to do yet
    }

    private void stop() {
        try {
            this.userStore.stop();
        } catch (Exception e) {
            this.logger.error("failed to stop native user module", e, new Object[0]);
        }

        try {
            this.keyStore.stop();
        } catch (Exception e) {
            this.logger.error("failed to stop native keys module", e, new Object[0]);
        }
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        //启动userStore服务
        // TODO: 2016/11/5 本地节点不是master节点会有什么影响
        boolean master = event.localNodeMaster();
        try {
            if (this.userStore.canStart(event.state(), master)) {
                this.threadPool.generic().execute(new AbstractRunnable() {

                    public void onFailure(Throwable throwable) {
                        GateLifeCycleService.this.logger.error("failed to start native user store service", throwable, new Object[0]);
                        throw new AssertionError("shield lifecycle services startup failed");
                    }

                    public void doRun() {
                        GateLifeCycleService.this.userStore.start();
                    }
                });
            }
        } catch (Exception e) {
            this.logger.error("failed to start native user store", e, new Object[0]);
        }

        try {
            if (this.keyStore.canStart(event.state(), master))
                this.threadPool.generic().execute(new AbstractRunnable() {

                    public void onFailure(Throwable throwable) {
                        GateLifeCycleService.this.logger.error("failed to start native roles store services", throwable, new Object[0]);
                        throw new AssertionError("shield lifecycle services startup failed");
                    }

                    public void doRun() {
                        GateLifeCycleService.this.keyStore.start();
                    }
                });
        } catch (Exception e) {
            this.logger.error("failed to start native keys store", e, new Object[0]);
        }
    }
}
