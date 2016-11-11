package org.elasticsearch.plugin.readonlyrest.builder.response;

import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

public class PutKeyResponse extends ActionResponse implements ToXContent {
    private boolean isCreated;

    public PutKeyResponse(boolean isCreated, boolean isCreated1) {
        this.isCreated = isCreated1;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        return null;
    }

    public boolean isCreated() {
        return isCreated;
    }

    public void setCreated(boolean created) {
        isCreated = created;
    }
}
