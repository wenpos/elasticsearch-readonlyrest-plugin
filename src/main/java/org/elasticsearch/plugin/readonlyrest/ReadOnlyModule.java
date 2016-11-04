package org.elasticsearch.plugin.readonlyrest;

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.plugin.readonlyrest.authc.DefaultAuthenticationFailureHandler;

public class ReadOnlyModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DefaultAuthenticationFailureHandler.class).asEagerSingleton();
    }
}
