package org.elasticsearch.plugin.readonlyrest.builder.request;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ValidateActions;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.plugin.readonlyrest.key.KeyDescriptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PutKeyRequest extends ActionRequest<PutKeyRequest> {
    private String keyname;
    private List<KeyDescriptor.IndicesPrivileges> indicesPrivileges = new ArrayList();
    private boolean refresh = true;

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (this.keyname == null) {
            validationException = ValidateActions.addValidationError("key name is missing", validationException);
        }
        return validationException;
    }


    public String getKeyName() {
        return keyname;
    }

    public void setKeyName(String keyname) {
        this.keyname = keyname;
    }

    public KeyDescriptor keyDescriptor(){
        return new KeyDescriptor(this.keyname, (KeyDescriptor.IndicesPrivileges[])this.indicesPrivileges.toArray(new KeyDescriptor.IndicesPrivileges[this.indicesPrivileges.size()]));
    }

    public List<KeyDescriptor.IndicesPrivileges> getIndicesPrivileges() {
        return indicesPrivileges;
    }

    public void setIndicesPrivileges(KeyDescriptor.IndicesPrivileges[] indicesPrivileges) {
        this.indicesPrivileges.addAll(Arrays.asList(indicesPrivileges));
    }

    public boolean isRefresh() {
        return refresh;
    }

    public void setRefresh(boolean refresh) {
        this.refresh = refresh;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        this.keyname = in.readString();
        int indicesSize = in.readVInt();
        this.indicesPrivileges = new ArrayList(indicesSize);
        for (int i = 0; i < indicesSize; ++i) {
            this.indicesPrivileges.add(KeyDescriptor.IndicesPrivileges.createFrom(in));
        }
        this.refresh = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.keyname);
        out.writeVInt(this.indicesPrivileges.size());
        for (KeyDescriptor.IndicesPrivileges index : this.indicesPrivileges) {
            index.writeTo(out);
        }
        out.writeBoolean(this.refresh);
    }


}
