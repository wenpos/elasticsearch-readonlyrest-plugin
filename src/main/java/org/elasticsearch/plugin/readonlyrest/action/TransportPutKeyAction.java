package org.elasticsearch.plugin.readonlyrest.action;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugin.readonlyrest.builder.request.PutKeyRequest;
import org.elasticsearch.plugin.readonlyrest.builder.request.PutUserRequest;
import org.elasticsearch.plugin.readonlyrest.builder.response.PutKeyResponse;
import org.elasticsearch.plugin.readonlyrest.store.KeyStoreService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

public class TransportPutKeyAction extends HandledTransportAction<PutKeyRequest, PutKeyResponse> {

    private final KeyStoreService keystore;

    @Inject
    public TransportPutKeyAction(Settings settings, ThreadPool threadPool, TransportService transportService, ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver, KeyStoreService keystore) {
        super(settings, "cluster:admin/gate/security/key/put", threadPool, transportService, actionFilters, indexNameExpressionResolver, PutKeyRequest.class);
        this.keystore = keystore;
    }


    @Override
    protected void doExecute(final PutKeyRequest request, final ActionListener<PutKeyResponse> listener) {
        this.keystore.putKey(request, request.keyDescriptor(), new ActionListener<Boolean>() {
            public void onResponse(Boolean created) {
                if (created.booleanValue())
                    TransportPutKeyAction.this.logger.info("added key [{}]", new Object[]{request.getKeyName()});
                else {
                    TransportPutKeyAction.this.logger.info("updated key [{}]", new Object[]{request.getKeyName()});
                }
                listener.onResponse(new PutKeyResponse(created.booleanValue(), created));
            }

            public void onFailure(Throwable t) {
                listener.onFailure(t);
            }
        });
    }
}
