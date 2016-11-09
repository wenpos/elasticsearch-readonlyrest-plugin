package org.elasticsearch.plugin.readonlyrest.rest.user;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.plugin.readonlyrest.builder.PutUserRequestBuilder;
import org.elasticsearch.plugin.readonlyrest.builder.response.user.PutUserResponse;
import org.elasticsearch.plugin.readonlyrest.client.GateClient;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.support.RestBuilderListener;

public class RestPutUserAction extends BaseRestHandler {
    @Inject
    public RestPutUserAction(Settings settings, RestController controller, Client client) {
        super(settings, controller, client);
        controller.registerHandler(RestRequest.Method.POST, "/_gate/user/{username}", this);
        controller.registerHandler(RestRequest.Method.PUT, "/_gate/user/{username}", this);

    }

    @Override
    protected void handleRequest(RestRequest request, RestChannel channel, Client client) throws Exception {
        PutUserRequestBuilder requestBuilder = new GateClient(client).preparePutUser(request);
        if (request.hasParam("refresh")) {
            requestBuilder.refresh(request.paramAsBoolean("refresh", true));
        }
        requestBuilder.execute(new RestBuilderListener<PutUserResponse>(channel) {
            public RestResponse buildResponse(PutUserResponse putUserResponse, XContentBuilder builder) throws Exception {
                return new BytesRestResponse(RestStatus.OK, builder.startObject().field("user", putUserResponse).endObject());
            }
        });
    }
}
