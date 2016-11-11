package org.elasticsearch.plugin.readonlyrest.rest;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.plugin.readonlyrest.builder.PutKeyRequestBuilder;
import org.elasticsearch.plugin.readonlyrest.builder.PutUserRequestBuilder;
import org.elasticsearch.plugin.readonlyrest.builder.request.PutKeyRequest;
import org.elasticsearch.plugin.readonlyrest.builder.response.PutKeyResponse;
import org.elasticsearch.plugin.readonlyrest.builder.response.PutUserResponse;
import org.elasticsearch.plugin.readonlyrest.client.GateClient;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.support.RestBuilderListener;

public class RestPutKeyAction extends BaseRestHandler {

    @Inject
    public RestPutKeyAction(Settings settings, RestController controller, Client client) {
        super(settings, controller, client);
        controller.registerHandler(RestRequest.Method.POST, "/_gate/key/{keyname}", this);
        controller.registerHandler(RestRequest.Method.PUT, "/_gate/key/{keyname}", this);
    }

    @Override
    protected void handleRequest(RestRequest request, RestChannel channel, Client client) throws Exception {
        PutKeyRequestBuilder requestBuilder = new GateClient(client).preparePutKey(request);
        if (request.hasParam("refresh")) {
            requestBuilder.refresh(request.paramAsBoolean("refresh", true));
        }
        requestBuilder.execute(new RestBuilderListener<PutKeyResponse>(channel) {
            public RestResponse buildResponse(PutKeyResponse putUserResponse, XContentBuilder builder) throws Exception {
                return new BytesRestResponse(RestStatus.OK, builder.startObject().field("user", putUserResponse).endObject());
            }
        });
    }
}
