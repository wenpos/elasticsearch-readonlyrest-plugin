package org.elasticsearch.plugin.readonlyrest.builder.response;

import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

public class PutUserResponse extends ActionResponse implements ToXContent {

    private boolean created;

    public PutUserResponse(boolean created) {
        this.created = created;
    }

    public boolean isCreated() {
        return this.created;
    }


    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (this.created == true){
            builder.startObject().field("created", true).endObject();
        }else {
            builder.startObject().field("update", true).endObject();
        }
        return builder;
    }
}
