package org.elasticsearch.plugin.readonlyrest.client;

import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.plugin.readonlyrest.builder.PutKeyRequestBuilder;
import org.elasticsearch.plugin.readonlyrest.builder.PutUserRequestBuilder;
import org.elasticsearch.rest.RestRequest;

import java.io.IOException;

public class GateClient {
    private final ElasticsearchClient gateClient;
    public GateClient(ElasticsearchClient client) {
        this.gateClient = client;
    }

    public PutUserRequestBuilder preparePutUser(RestRequest request) throws IOException {
        return new PutUserRequestBuilder(gateClient).source(request);
    }

    public PutKeyRequestBuilder preparePutKey(RestRequest request) throws Exception {
        return new PutKeyRequestBuilder(gateClient).source(request);
    }
}
