package org.elasticsearch.plugin.readonlyrest.user;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

public class GateUser implements ToXContent {
    private String userName;
    private String[] roles;

    public GateUser(String userName, String[] roles) {
        this.userName = userName;
        this.roles = roles;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        return null;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String[] getRoles() {
        return roles;
    }

    public void setRoles(String[] roles) {
        this.roles = roles;
    }

    public static abstract interface Fields {
        public static final ParseField USERNAME = new ParseField("username", new String[0]);
        public static final ParseField PASSWORD = new ParseField("password", new String[0]);
        public static final ParseField PASSWORD_HASH = new ParseField("password_hash", new String[0]);
        public static final ParseField ROLES = new ParseField("roles", new String[0]);
        public static final ParseField FULL_NAME = new ParseField("full_name", new String[0]);
        public static final ParseField EMAIL = new ParseField("email", new String[0]);
        public static final ParseField METADATA = new ParseField("metadata", new String[0]);

    }

}
