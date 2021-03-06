package org.elasticsearch.plugin.readonlyrest;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.plugin.readonlyrest.acl.ACL;
import org.elasticsearch.plugin.readonlyrest.acl.RequestContext;
import org.elasticsearch.plugin.readonlyrest.acl.blocks.Block;
import org.elasticsearch.plugin.readonlyrest.acl.blocks.BlockExitResult;
import org.elasticsearch.plugin.readonlyrest.acl.blocks.rules.RuleExitResult;
import org.elasticsearch.plugin.readonlyrest.acl.blocks.rules.impl.AuthKeyRule;
import org.elasticsearch.plugin.readonlyrest.authc.DefaultAuthenticationFailureHandler;
import org.elasticsearch.plugin.readonlyrest.authc.ReadOnlySettingParser;
import org.elasticsearch.rest.*;

/**
 * Readonly REST plugin. Adding some access control to the fast Netty based REST interface of Elasticsearch.
 * <p>
 * This plugin is configurable from $ES_HOME/conf/elasticsearch.yml. Example configuration:
 * <p>
 * <pre>
 * readonlyrest:
 *  enable: true
 *  auth_key: secretAuthKey // this can bypasses all other rules and allows for operation if matched
 *  allow_localhost: true
 *  whitelist: [192.168.1.144]
 *  forbidden_uri_re: .*bar_me_pls.*
 *  barred_reason_string: <h1>unauthorized</h1>
 * </pre>
 *
 * @author <a href="mailto:scarduzio@gmail.com">Simone Scarduzio</a>
 * @see <a href="https://github.com/sscarduzio/elasticsearch-readonlyrest-plugin/">Github Project</a>
 */

public class ReadonlyRestAction extends BaseRestHandler {
    @Inject
    public ReadonlyRestAction(final Settings settings, Client client, RestController controller, final DefaultAuthenticationFailureHandler authcHandler, final IndicesService indicesService) {
        super(settings, controller, client);

        controller.registerFilter(new RestFilter() {

            @Override
            public void process(RestRequest request, RestChannel channel, RestFilterChain filterChain) throws Exception {
                if (!new ReadOnlySettingParser(settings).checkUserAuth(request)) {
                    throw authcHandler.unsuccessfulAuthentication(request);
                }
                request.putInContext("request", request);
                request.putInContext("channel", channel);
                filterChain.continueProcessing(request, channel);
            }
        });
    }

    @Override
    protected void handleRequest(RestRequest restRequest, RestChannel restChannel, Client client) throws Exception {
        // We do everything in the constructor
    }

}
