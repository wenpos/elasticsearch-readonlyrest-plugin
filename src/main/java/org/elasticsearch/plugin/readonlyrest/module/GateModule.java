package org.elasticsearch.plugin.readonlyrest.module;

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.plugin.readonlyrest.life.GateLifeCycleService;
import org.elasticsearch.plugin.readonlyrest.store.UserStoreService;

public class GateModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(GateLifeCycleService.class).asEagerSingleton();
        bind(UserStoreService.class).asEagerSingleton();
    }
}
