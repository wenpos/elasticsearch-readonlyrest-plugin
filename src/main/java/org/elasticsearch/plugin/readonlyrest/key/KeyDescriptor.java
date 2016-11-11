package org.elasticsearch.plugin.readonlyrest.key;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.plugin.readonlyrest.util.XContentUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class KeyDescriptor implements ToXContent {
    private final String keyname;
    private final IndicesPrivileges[] indicesPrivileges;

    public KeyDescriptor(String keyname, IndicesPrivileges[] indicesPrivileges) {
        this.keyname = keyname;
        this.indicesPrivileges = ((indicesPrivileges != null) ? indicesPrivileges : IndicesPrivileges.NONE);
    }

    public String getKeyname() {
        return keyname;
    }


    public IndicesPrivileges[] getIndicesPrivileges() {
        return indicesPrivileges;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.field("indices", indicesPrivileges);
        return builder;
    }

    public static KeyDescriptor readFrom(StreamInput in) throws IOException {
        String name = in.readString();
        String[] clusterPrivileges = in.readStringArray();
        int size = in.readVInt();
        IndicesPrivileges[] indicesPrivileges = new IndicesPrivileges[size];
        for (int i = 0; i < size; ++i) {
            indicesPrivileges[i] = IndicesPrivileges.createFrom(in);
        }
        String[] runAs = in.readStringArray();
        return new KeyDescriptor(name, indicesPrivileges);
    }

    public static void writeTo(KeyDescriptor descriptor, StreamOutput out) throws IOException {
        out.writeString(descriptor.getKeyname());
        out.writeVInt(descriptor.indicesPrivileges.length);
        for (IndicesPrivileges group : descriptor.indicesPrivileges) {
            group.writeTo(out);
        }
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
    public static KeyDescriptor parse(String keyname, BytesReference source) throws Exception {
        assert (keyname != null);
        XContentParser parser = XContentHelper.createParser(source);
        Throwable localThrowable2 = null;
        KeyDescriptor localKeyDescriptor = null;
        try {
            localKeyDescriptor = parse(keyname, parser);
        } catch (Throwable localThrowable3) {
        } finally {
            if (parser != null) if (localThrowable2 != null) try {
                parser.close();
            } catch (Throwable x2) {
                localThrowable2.addSuppressed(x2);
            }
            else {
                parser.close();
            }
        }
        return localKeyDescriptor;

    }

    public static KeyDescriptor parse(String name, XContentParser parser) throws Exception {
        // TODO: 2016/11/11 keyname校验
//        Validation.Error validationError = Validation.Roles.validateRoleName(keyname);
//        if (validationError != null) {
//            ValidationException ve = new ValidationException();
//            ve.addValidationError(validationError.toString());
//            throw ve;
//        }

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

        XContentParser.Token token = (parser.currentToken() == null) ? parser.nextToken() : parser.currentToken();
        if (token != XContentParser.Token.START_OBJECT) {
            throw new ElasticsearchParseException("failed to parse role [{}]. expected an object but found [{}] instead", new Object[]{name, token});
        }

        String currentFieldName = null;
        IndicesPrivileges[] indicesPrivileges = null;
        while (true) {
            if ((token = parser.nextToken()) == XContentParser.Token.END_OBJECT) {
                break;
            }
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            }

            if (ParseFieldMatcher.STRICT.match(currentFieldName, Fields.INDICES)) {
                indicesPrivileges = parseIndices(name, parser);
            }
        }
        // TODO: 2016/11/11 非定义字段解析时抛出异常
//        throw new ElasticsearchParseException("failed to parse role [{}]. unexpected field [{}]", new Object[]{name, currentFieldName});

        return new KeyDescriptor(name, indicesPrivileges);
    }


    public static IndicesPrivileges[] parseIndices(String roleName, XContentParser parser) throws Exception {
        if (parser.currentToken() != XContentParser.Token.START_ARRAY) {
            throw new ElasticsearchParseException("failed to parse indices privileges for role [{}]. expected field [{}] value to be an array, but found [{}] instead", new Object[]{roleName, parser.currentName(), parser.currentToken()});
        }
        XContentParser.Token token;
        List<KeyDescriptor.IndicesPrivileges> privileges = new ArrayList();

        while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {
            privileges.add(parseIndex(roleName, parser));
        }
        return ((IndicesPrivileges[]) privileges.toArray(new IndicesPrivileges[privileges.size()]));

    }

    private static IndicesPrivileges parseIndex(String roleName, XContentParser parser) throws Exception {
        XContentParser.Token token = parser.currentToken();
        if (token != XContentParser.Token.START_OBJECT) {
            throw new ElasticsearchParseException("failed to parse indices privileges for role [{}]. expected field [{}] value to be an array of objects, but found an array element of type [{}]", new Object[]{roleName, parser.currentName(), token});
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
        String currentFieldName = null;
        String[] names = null;
        String method = null;
        String[] privileges = null;
        String[] fields = null;
        while (true) {
            if ((token = parser.nextToken()) == XContentParser.Token.END_OBJECT) {
                break;
            }

            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            }

            //解析indices
            if (ParseFieldMatcher.STRICT.match(currentFieldName, Fields.NAMES)) {
                if (token == XContentParser.Token.VALUE_STRING) {
                    names = new String[]{parser.text()};
                }
                if (token == XContentParser.Token.START_ARRAY) {
                    names = XContentUtils.readStringArray(parser, false);
                    if (names.length == 0) {
                        throw new ElasticsearchParseException("failed to parse indices privileges for key [{}]. [{}] cannot be an empty array", new Object[]{roleName, currentFieldName});
                    }
                }

                // TODO: 2016/11/11 非法字段jiaoyan
//                throw new ElasticsearchParseException("failed to parse indices privileges for key [{}]. expected field [{}] value to be a string or an array of strings, but found [{}] instead", new Object[]{roleName, currentFieldName, token});
            }

            //解析methods
            if (ParseFieldMatcher.STRICT.match(currentFieldName, Fields.METHODS)) {
                if (token == XContentParser.Token.START_OBJECT) {
                    XContentBuilder builder = JsonXContent.contentBuilder();
                    XContentHelper.copyCurrentStructure(builder.generator(), parser);
                    method = builder.string();
                }
                method = parser.textOrNull();
            }

            if (ParseFieldMatcher.STRICT.match(currentFieldName, Fields.PRIVILEGES)) {
                privileges = XContentUtils.readStringArray(parser, false);
            }
            if (!(ParseFieldMatcher.STRICT.match(currentFieldName, Fields.FIELDS))) break;
            fields = XContentUtils.readStringArray(parser, true);
        }
        // TODO: 2016/11/11 字段校验
//        throw new ElasticsearchParseException("failed to parse indices privileges for role [{}]. unexpected field [{}]", new Object[]{roleName, currentFieldName});

        if (names == null) {
            throw new ElasticsearchParseException("failed to parse indices privileges for role [{}]. missing required [{}] field", new Object[]{roleName, Fields.NAMES.getPreferredName()});
        }

        if (privileges == null) {
            throw new ElasticsearchParseException("failed to parse indices privileges for role [{}]. missing required [{}] field", new Object[]{roleName, Fields.PRIVILEGES.getPreferredName()});
        }

        return IndicesPrivileges.builder().indices(names).privileges(privileges).fields(fields).methods(method).build();
    }

    public static class IndicesPrivileges implements ToXContent, Streamable {
        private static final IndicesPrivileges[] NONE = new IndicesPrivileges[0];
        private String[] indices;
        private String[] privileges;
        private String[] fields;
        private BytesReference methods;

        public static Builder builder() {
            return new Builder();
        }

        public static IndicesPrivileges[] getNONE() {
            return NONE;
        }

        public String[] getIndices() {
            return indices;
        }

        public void setIndices(String[] indices) {
            this.indices = indices;
        }

        public String[] getPrivileges() {
            return privileges;
        }

        public void setPrivileges(String[] privileges) {
            this.privileges = privileges;
        }

        public String[] getFields() {
            return fields;
        }

        public void setFields(String[] fields) {
            this.fields = fields;
        }

        public BytesReference getMethods() {
            return methods;
        }

        public void setMethods(BytesReference methods) {
            this.methods = methods;
        }

        public static IndicesPrivileges createFrom(StreamInput in) throws IOException {
            IndicesPrivileges ip = new IndicesPrivileges();
            ip.readFrom(in);
            return ip;
        }

        @Override
        public void readFrom(StreamInput streamInput) throws IOException {

        }

        @Override
        public void writeTo(StreamOutput streamOutput) throws IOException {

        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            return null;
        }

        public static class Builder {
            private KeyDescriptor.IndicesPrivileges indicesPrivileges = new KeyDescriptor.IndicesPrivileges();

            public Builder indices(String[] indices) {
                indicesPrivileges.setIndices(indices);
                return this;
            }

            public Builder privileges(String[] privileges) {
                indicesPrivileges.setPrivileges(privileges);
                return this;
            }

            public Builder fields(@Nullable String[] fields) {
                indicesPrivileges.setFields(fields);
                return this;
            }

            public Builder methods(@Nullable String methods) {
                return methods(new BytesArray(methods));
            }

            public Builder methods(@Nullable BytesReference query) {
                indicesPrivileges.setMethods(query);
                return this;
            }

            public KeyDescriptor.IndicesPrivileges build() {
                if ((this.indicesPrivileges.indices == null) || (this.indicesPrivileges.indices.length == 0)) {
                    throw new IllegalArgumentException("indices privileges must refer to at least one index keyname or index keyname pattern");
                }
                if ((this.indicesPrivileges.privileges == null) || (this.indicesPrivileges.privileges.length == 0)) {
                    throw new IllegalArgumentException("indices privileges must define at least one privilege");
                }
                return this.indicesPrivileges;
            }
        }
    }

    public static abstract interface Fields {
        public static final ParseField CLUSTER = new ParseField("cluster", new String[0]);
        public static final ParseField INDICES = new ParseField("indices", new String[0]);
        public static final ParseField RUN_AS = new ParseField("run_as", new String[0]);
        public static final ParseField NAMES = new ParseField("names", new String[0]);
        public static final ParseField METHODS = new ParseField("methods", new String[0]);
        public static final ParseField PRIVILEGES = new ParseField("privileges", new String[0]);
        public static final ParseField FIELDS = new ParseField("fields", new String[0]);
    }
}
