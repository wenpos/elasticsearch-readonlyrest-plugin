package org.elasticsearch.plugin.readonlyrest.authc;

import com.google.common.base.Charsets;
import org.elasticsearch.common.Base64;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugin.readonlyrest.acl.blocks.rules.RuleNotConfiguredException;
import org.elasticsearch.rest.RestRequest;

import java.io.IOException;
import java.util.Map;

public class ReadOnlySettingParser {
    private final static ESLogger logger = Loggers.getLogger(ReadOnlySettingParser.class);
    private final static String USERS_PREFIX = "readonlyrest.users";
    private Map<String, Settings> userSettings;

    public ReadOnlySettingParser(Settings s) {
        this.userSettings = s.getGroups(USERS_PREFIX);
    }

    private String extractAuthFromHeader(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.trim().length() == 0 || !authorizationHeader.contains("Basic "))
            return null;
        String interestingPart = authorizationHeader.split("Basic")[1].trim();
        if (interestingPart.length() == 0) {
            return null;
        }
        return interestingPart;
    }

    private String parserAuthKey(String authKey) {
        if (authKey != null && authKey.trim().length() > 0) {
            return Base64.encodeBytes(authKey.getBytes(Charsets.UTF_8));
        } else {
            return "";
        }

    }

    public boolean checkUserAuth(RestRequest request) {
        String authHeader = extractAuthFromHeader(request.header("Authorization"));

        if (authHeader != null && logger.isDebugEnabled()) {
            try {
                logger.info("Login as: " + new String(Base64.decode(authHeader)).split(":")[0] + " request: " + request);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (authHeader == null) {
            return false;
        }

        String val = authHeader.trim();
        if (val.length() == 0) {
            return false;
        }

        for (Map.Entry<String, Settings> user : userSettings.entrySet()) {
            String auth_key = parserAuthKey(user.getValue().get("auth_key"));
            if (auth_key != null && !"".equals(auth_key)) {
                if (authHeader.equals(auth_key) == true) {
                    return true;
                }
            }
        }
        return false;
    }
}
