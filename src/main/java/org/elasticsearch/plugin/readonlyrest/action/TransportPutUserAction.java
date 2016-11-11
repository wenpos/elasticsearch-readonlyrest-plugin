package org.elasticsearch.plugin.readonlyrest.action;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugin.readonlyrest.builder.request.PutUserRequest;
import org.elasticsearch.plugin.readonlyrest.builder.response.PutUserResponse;
import org.elasticsearch.plugin.readonlyrest.store.UserStoreService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

public class TransportPutUserAction extends HandledTransportAction<PutUserRequest, PutUserResponse> {

    private final UserStoreService userStore;

    @Inject
    public TransportPutUserAction(Settings settings, ThreadPool threadPool, TransportService transportService, ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver, UserStoreService userStore) {
        super(settings, "cluster:admin/gate/security/user/put", threadPool, transportService, actionFilters, indexNameExpressionResolver, PutUserRequest.class);
        this.userStore = userStore;
    }

    @Override
    protected void doExecute(final PutUserRequest request, final ActionListener<PutUserResponse> listener) {
        this.userStore.putUser(request, new ActionListener<Boolean>() {
            public void onResponse(Boolean created) {
                if (created.booleanValue())
                    TransportPutUserAction.this.logger.info("added user [{}]", new Object[]{request.getUsername()});
                else {
                    TransportPutUserAction.this.logger.info("updated user [{}]", new Object[]{request.getUsername()});
                }
                listener.onResponse(new PutUserResponse(created.booleanValue()));
            }

            public void onFailure(Throwable e) {
                TransportPutUserAction.this.logger.error("failed to put user [{}]", e, new Object[]{request.getUsername()});
                listener.onFailure(e);
            }
        });
    }
}
