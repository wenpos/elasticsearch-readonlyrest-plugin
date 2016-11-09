package org.elasticsearch.plugin.readonlyrest.client;

import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.FilterClient;

public class ClientWithUser extends FilterClient {
    public ClientWithUser(Client in) {
        super(in);
    }

    // TODO: 2016/11/5 根据user鉴权过滤客户端连接
    @Override
    protected <Request extends ActionRequest, Response extends ActionResponse, RequestBuilder extends ActionRequestBuilder<Request, Response, RequestBuilder>> void doExecute(Action<Request, Response, RequestBuilder> action, Request request, ActionListener<Response> listener) {
        // TODO: 2016/11/4 增加权限校验：是否有权限执行User的相关操作，比如只有该用户才可以执行查询自己的用户信息的权利
        super.doExecute(action, request, listener);
    }
}
