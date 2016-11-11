package org.elasticsearch.plugin.readonlyrest.builder;

import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.plugin.readonlyrest.builder.action.PutUserAction;
import org.elasticsearch.plugin.readonlyrest.builder.request.PutUserRequest;
import org.elasticsearch.plugin.readonlyrest.builder.response.PutUserResponse;
import org.elasticsearch.plugin.readonlyrest.user.GateUser;
import org.elasticsearch.plugin.readonlyrest.util.XContentUtils;
import org.elasticsearch.rest.RestRequest;

import java.io.IOException;
import java.util.Arrays;

public class PutUserRequestBuilder extends ActionRequestBuilder<PutUserRequest, PutUserResponse, PutUserRequestBuilder> {


    public PutUserRequestBuilder(ElasticsearchClient gateClient) {

        this(gateClient, PutUserAction.INSTANCE);
    }

    public PutUserRequestBuilder(ElasticsearchClient client, PutUserAction putUserAction) {
        super(client, putUserAction, new PutUserRequest());
    }

    public PutUserRequestBuilder source(RestRequest request) throws IOException {
        //username 是作为参数传进来的
        String username = request.param("username");
        username(username);

        BytesReference source = request.content();
        XContentParser parser = XContentHelper.createParser(source);


        XContentParser.Token token;
        XContentUtils.verifyObject(parser);

        Throwable localThrowable2 = null;

        String currentFieldName = null;
        char[] passwordChars;

        //下列参数是写在json串中的，需要解析
        try {
            while (true) {
                if ((token = parser.nextToken()) == XContentParser.Token.END_OBJECT) {
                    break;
                }
                if (token == XContentParser.Token.FIELD_NAME) {
                    currentFieldName = parser.currentName();
                }

                //解析roles，并赋值给request
                if (ParseFieldMatcher.STRICT.match(currentFieldName, GateUser.Fields.ROLES)) {
                    if (token != XContentParser.Token.FIELD_NAME) {
                        if (token == XContentParser.Token.VALUE_STRING) {
                            roles(Strings.commaDelimitedListToStringArray(parser.text()));
                        }
                        if (token == XContentParser.Token.START_ARRAY){
                            roles(XContentUtils.readStringArray(parser, false));
                        }
                    }
                }

                //解析密码password，校验后赋值给request
                if (ParseFieldMatcher.STRICT.match(currentFieldName, GateUser.Fields.PASSWORD)) {
                    if (token == XContentParser.Token.VALUE_STRING) {
                        passwordChars = parser.text().toCharArray();
                        password(passwordChars);
                        Arrays.fill(passwordChars, '\0');
                    }
//                    throw new ElasticsearchParseException("expected field [{}] to be of type string, but found [{}] instead", new Object[]{currentFieldName, token});
                }

            }
        } catch (Throwable localThrowable1) {
        } finally {
            if (parser != null) {
                if (localThrowable2 != null) {
                    try {
                        parser.close();
                    } catch (Throwable x2) {
                        localThrowable2.addSuppressed(x2);
                    }
                } else {
                    parser.close();
                }
            }
        }
        return this;
    }

    public PutUserRequestBuilder roles(String[] roles) {
        ((PutUserRequest) this.request).setRoles(roles);
        return this;
    }

    public PutUserRequestBuilder passwordHash(char[] passwordHash) {
        ((PutUserRequest) this.request).setPassword(passwordHash);
        return this;
    }

    public PutUserRequestBuilder password(@Nullable char[] password) {
        if (password != null) {
            if (password.length < 6) {
                throw new IllegalArgumentException("passwords must be at least [6] characters long");
            }
            // TODO: 2016/11/7 复杂的校验机制
//            Validation.Error error = Validation.ESUsers.validatePassword(password);
//            if (error != null) {
//                ValidationException validationException = new ValidationException();
//                validationException.addValidationError(error.toString());
//                throw validationException;
//            }
            // TODO: 2016/11/7 将密码做hash有什么用？
//            ((PutUserRequest) this.request).setPassword(this.hasher.hash(new SecuredString(password)));
            ((PutUserRequest) this.request).setPassword(password);
        } else {
            ((PutUserRequest) this.request).setPassword(null);
        }
        return this;
    }

    private void username(String username) {
        ((PutUserRequest) this.request).setUsername(username);
    }

    public void refresh(boolean refresh) {

    }
}
