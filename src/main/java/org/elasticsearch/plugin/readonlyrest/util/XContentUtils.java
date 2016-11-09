package org.elasticsearch.plugin.readonlyrest.util;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class XContentUtils {
    public static void verifyObject(XContentParser parser)
            throws IOException, ElasticsearchParseException {
        if (parser.currentToken() == XContentParser.Token.START_OBJECT) {
            return;
        }
        XContentParser.Token token = parser.nextToken();
        if (token != XContentParser.Token.START_OBJECT)
            throw new ElasticsearchParseException("expected an object, but found token [{}]", new Object[]{parser.currentToken()});
    }

    public static String[] readStringArray(XContentParser parser, boolean allowNull)
            throws IOException {
        XContentParser.Token token;
        if (parser.currentToken() == XContentParser.Token.VALUE_NULL) {
            if (allowNull) {
                return null;
            }
            throw new ElasticsearchParseException("could not parse [{}] field. expected a string array but found null value instead", new Object[]{parser.currentName()});
        }

        if (parser.currentToken() != XContentParser.Token.START_ARRAY) {
            throw new ElasticsearchParseException("could not parse [{}] field. expected a string array but found [{}] value instead", new Object[]{parser.currentName(), parser.currentToken()});
        }

        List list = new ArrayList();
        while (true) {
            if ((token = parser.nextToken()) == XContentParser.Token.END_ARRAY) {
                return ((String[]) list.toArray(new String[list.size()]));
            }
            if (token != XContentParser.Token.VALUE_STRING) break;
            list.add(parser.text());
        }
        throw new ElasticsearchParseException("could not parse [{}] field. expected a string array but one of the value in the array is [{}]", new Object[]{parser.currentName(), token});

    }
}
