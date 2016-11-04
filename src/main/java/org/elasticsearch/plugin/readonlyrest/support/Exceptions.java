package org.elasticsearch.plugin.readonlyrest.support;

import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.rest.RestStatus;


public class Exceptions {
    public static ElasticsearchSecurityException authenticationError(String msg, Throwable cause, Object[] args) {
        ElasticsearchSecurityException e = new ElasticsearchSecurityException(msg, RestStatus.UNAUTHORIZED, cause, args);
        e.addHeader("WWW-Authenticate", new String[]{"Basic realm=\"shield\" charset=\"UTF-8\""});
        return e;
    }

    public static ElasticsearchSecurityException authenticationError(String msg, Object[] args) {
        ElasticsearchSecurityException e = new ElasticsearchSecurityException(msg, RestStatus.UNAUTHORIZED, args);
        e.addHeader("WWW-Authenticate", new String[]{"Basic realm=\"shield\" charset=\"UTF-8\""});
        return e;
    }

    public static ElasticsearchSecurityException authorizationError(String msg, Object[] args) {
        return new ElasticsearchSecurityException(msg, RestStatus.FORBIDDEN, args);
    }
}