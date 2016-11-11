package org.elasticsearch.plugin.readonlyrest.builder.request;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ValidateActions;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.plugin.readonlyrest.util.CharUtil;

import java.io.IOException;

public class PutUserRequest extends ActionRequest<PutUserRequest> {
    private String username;
    private String[] roles;
    private char[] password;
    private boolean refresh = true;

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (this.username == null) {
            validationException = ValidateActions.addValidationError("user is missing", validationException);
        }
        if (this.roles == null) {
            validationException = ValidateActions.addValidationError("roles are missing", validationException);
        }

        return validationException;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String[] getRoles() {
        return roles;
    }

    public void setRoles(String[] roles) {
        this.roles = roles;
    }

    @Nullable
    public char[] getPassword() {
        return password;
    }

    public void setPassword(@Nullable char[] password) {
        this.password = password;
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
        super.readFrom(in);
        this.username = in.readString();
        BytesReference passwordHashRef = in.readBytesReference();
        if (passwordHashRef == BytesArray.EMPTY)
            this.password = null;
        else {
            this.password = CharUtil.utf8BytesToChars(passwordHashRef.array());
        }
        this.roles = in.readStringArray();
        this.refresh = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        BytesReference passwordHashRef;
        out.writeString(this.username);

        if (this.password == null)
            passwordHashRef = null;
        else {
            passwordHashRef = new BytesArray(CharUtil.toUtf8Bytes(this.password));
        }
        out.writeBytesReference(passwordHashRef);
        out.writeStringArray(this.roles);
        out.writeBoolean(this.refresh);
    }
}
