package org.elasticsearch.plugin.readonlyrest.builder.action;

import org.elasticsearch.action.Action;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.plugin.readonlyrest.builder.PutUserRequestBuilder;
import org.elasticsearch.plugin.readonlyrest.builder.request.user.PutUserRequest;
import org.elasticsearch.plugin.readonlyrest.builder.response.user.PutUserResponse;

public class PutUserAction extends Action<PutUserRequest, PutUserResponse, PutUserRequestBuilder> {
    public static final PutUserAction INSTANCE = new PutUserAction();

    protected PutUserAction() {
        super("cluster:admin/gate/security/user/put");
    }

    @Override
    public PutUserRequestBuilder newRequestBuilder(ElasticsearchClient client) {
        return new PutUserRequestBuilder(client, this);
    }

    @Override
    public PutUserResponse newResponse() {
        return null;
    }
}
