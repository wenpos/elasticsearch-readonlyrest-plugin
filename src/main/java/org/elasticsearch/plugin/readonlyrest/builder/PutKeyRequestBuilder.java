package org.elasticsearch.plugin.readonlyrest.builder;

import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.plugin.readonlyrest.builder.action.PutKeyAction;
import org.elasticsearch.plugin.readonlyrest.builder.request.PutKeyRequest;
import org.elasticsearch.plugin.readonlyrest.builder.response.PutKeyResponse;
import org.elasticsearch.plugin.readonlyrest.key.KeyDescriptor;
import org.elasticsearch.rest.RestRequest;

public class PutKeyRequestBuilder extends ActionRequestBuilder<PutKeyRequest, PutKeyResponse, PutKeyRequestBuilder> {

    public PutKeyRequestBuilder(ElasticsearchClient gateClient) {

        this(gateClient, PutKeyAction.INSTANCE);
    }

    public void refresh(boolean refresh) {

    }

    protected PutKeyRequestBuilder(ElasticsearchClient client, Action<PutKeyRequest, PutKeyResponse, PutKeyRequestBuilder> action) {
        super(client, action, new PutKeyRequest());
    }

    /* 
            {
            "indices": [
                 {
                    "names": [ "index1",  "index2" ],
                    "privileges": ["all"],
                    "fields": [ "title","body" ],
                    "methods":["GET"]
                }
            ]
        }
     */
    // TODO: 2016/11/11 解析上述json，包含cluster、indices权限、fields权限，HTTP方法等
    public PutKeyRequestBuilder source(RestRequest request) throws Exception {
        String keyname = request.param("keyname");
        KeyDescriptor descriptor = KeyDescriptor.parse(keyname, request.content());
        assert ((keyname.equals(descriptor.getKeyname())));
        ((PutKeyRequest) this.request).setKeyName(keyname);
        ((PutKeyRequest) this.request).setIndicesPrivileges(descriptor.getIndicesPrivileges());
        return this;
    }
}
