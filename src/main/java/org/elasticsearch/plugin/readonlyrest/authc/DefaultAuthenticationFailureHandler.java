package org.elasticsearch.plugin.readonlyrest.authc;


import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.plugin.readonlyrest.support.Exceptions;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.transport.TransportMessage;


public class DefaultAuthenticationFailureHandler {
    public ElasticsearchSecurityException unsuccessfulAuthentication(RestRequest request) {
        return Exceptions.authenticationError("unable to authenticate user [{}] for REST request [{}]", new Object[]{"", request.uri()});
    }

    public ElasticsearchSecurityException unsuccessfulAuthentication(TransportMessage message, String action) {
        return Exceptions.authenticationError("unable to authenticate user [{}] for action [{}]", new Object[]{"", action});
    }

    public ElasticsearchSecurityException exceptionProcessingRequest(RestRequest request, Exception e) {
        if (e instanceof ElasticsearchSecurityException) {
            assert ((((ElasticsearchSecurityException) e).status() == RestStatus.UNAUTHORIZED));
            assert ((((ElasticsearchSecurityException) e).getHeader("WWW-Authenticate").size() == 1));
            return ((ElasticsearchSecurityException) e);
        }
        return Exceptions.authenticationError("error attempting to authenticate request", e, new Object[0]);
    }

    public ElasticsearchSecurityException exceptionProcessingRequest(TransportMessage message, Exception e) {
        if (e instanceof ElasticsearchSecurityException) {
            assert ((((ElasticsearchSecurityException) e).status() == RestStatus.UNAUTHORIZED));
            assert ((((ElasticsearchSecurityException) e).getHeader("WWW-Authenticate").size() == 1));
            return ((ElasticsearchSecurityException) e);
        }
        return Exceptions.authenticationError("error attempting to authenticate request", e, new Object[0]);
    }

    public ElasticsearchSecurityException missingToken(RestRequest request) {
        return Exceptions.authenticationError("missing authentication token for REST request [{}]", new Object[]{request.uri()});
    }

    public ElasticsearchSecurityException missingToken(TransportMessage message, String action) {
        return Exceptions.authenticationError("missing authentication token for action [{}]", new Object[]{action});
    }

    public ElasticsearchSecurityException authenticationRequired(String action) {
        return Exceptions.authenticationError("action [{}] requires authentication", new Object[]{action});
    }
}