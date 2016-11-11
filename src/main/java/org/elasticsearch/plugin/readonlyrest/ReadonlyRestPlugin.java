package org.elasticsearch.plugin.readonlyrest;

import org.elasticsearch.action.ActionModule;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.http.HttpServerModule;
import org.elasticsearch.plugin.readonlyrest.action.TransportPutUserAction;
import org.elasticsearch.plugin.readonlyrest.builder.action.PutUserAction;
import org.elasticsearch.plugin.readonlyrest.module.GateModule;
import org.elasticsearch.plugin.readonlyrest.rest.RestPutUserAction;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestModule;

import java.util.Arrays;
import java.util.Collection;

public class ReadonlyRestPlugin extends Plugin {

  @Override
  public String name() {
    return "readonlyrest";
  }

  @Override
  public String description() {
    return "Reject attempts to change data, so we can expose this REST API to clients";
  }

  public void onModule(RestModule module) {
    module.addRestAction(ReadonlyRestAction.class);
    module.addRestAction(RestPutUserAction.class);
  }

  public void onModule(HttpServerModule module) {
    module.setHttpServerTransport(SSLTransport.class, this.getClass().getSimpleName());
  }

  //Guice注入GateModule，node级别的
  @Override
  public Collection<Module> nodeModules() {
    return Arrays.asList(new Module[] { new GateModule()});
  }

  public void onModule(final ActionModule module) {
    module.registerFilter(IndexLevelActionFilter.class);
    module.registerAction(PutUserAction.INSTANCE, TransportPutUserAction.class, new Class[0]);
  }

}
