package org.elasticsearch.plugin.readonlyrest.builder.action;

import org.elasticsearch.action.Action;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.plugin.readonlyrest.builder.PutKeyRequestBuilder;
import org.elasticsearch.plugin.readonlyrest.builder.request.PutKeyRequest;
import org.elasticsearch.plugin.readonlyrest.builder.response.PutKeyResponse;

public class PutKeyAction extends Action<PutKeyRequest, PutKeyResponse, PutKeyRequestBuilder> {
    public static final PutKeyAction INSTANCE = new PutKeyAction();

    protected PutKeyAction() {
        super("cluster:admin/gate/security/key/put");
    }

    @Override
    public PutKeyRequestBuilder newRequestBuilder(ElasticsearchClient elasticsearchClient) {
        return null;
    }

    @Override
    public PutKeyResponse newResponse() {
        return null;
    }
}
