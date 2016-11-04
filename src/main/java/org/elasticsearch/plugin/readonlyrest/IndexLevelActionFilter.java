package org.elasticsearch.plugin.readonlyrest;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Singleton;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.plugin.readonlyrest.acl.ACL;
import org.elasticsearch.plugin.readonlyrest.acl.RequestContext;
import org.elasticsearch.plugin.readonlyrest.acl.blocks.Block;
import org.elasticsearch.plugin.readonlyrest.acl.blocks.BlockExitResult;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;

import java.io.IOException;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * Created by sscarduzio on 19/12/2015.
 */
@Singleton
public class IndexLevelActionFilter extends ActionFilter.Simple {
  private IndicesService indicesService;
  private ACL acl;

  private ConfigurationHelper conf;

  @Inject
  public IndexLevelActionFilter(Settings settings, ACL acl, ConfigurationHelper conf, IndicesService indicesService) {
    super(settings);
    this.conf = conf;
    this.indicesService = indicesService;

    logger.info("Readonly REST plugin was loaded...");

    if (!conf.enabled) {
      logger.info("Readonly REST plugin is disabled!");
      return;
    }

    logger.info("Readonly REST plugin is enabled. Yay, ponies!");
    this.acl = acl;
  }

  @Override
  public int order() {
    return 0;
  }

  @Override
  public boolean apply(String action, ActionRequest actionRequest, final ActionListener listener) {

    // Skip if disabled
    if (!conf.enabled) {
      logger.info("Readonly Rest plugin is installed, but not enabled");
      return true;
    }

    RestRequest req = actionRequest.getFromContext("request");
    RestChannel channel = actionRequest.getFromContext("channel");

    boolean reqNull = req == null;
    boolean chanNull = channel == null;

    // This was not a REST message
    if (reqNull && chanNull) {
      return true;
    }

    // Bailing out in case of catastrophical misconfiguration that would lead to insecurity
    if (reqNull != chanNull) {
      if (chanNull)
        throw new SecurityPermissionException("Problems analyzing the channel object. Have you checked the security permissions?", null);
      if (reqNull)
        throw new SecurityPermissionException("Problems analyzing the request object. Have you checked the security permissions?", null);
    }

    RequestContext rc = new RequestContext(channel, req, action, actionRequest, indicesService);
    BlockExitResult exitResult = acl.check(rc);

    // The request is allowed to go through
    if (exitResult.isMatch() && exitResult.getBlock().getPolicy() == Block.Policy.ALLOW) {
      return true;
    }

    // Barring
    logger.info("forbidden request: " + rc + " Reason: " + exitResult.getBlock() + " (" + exitResult.getBlock() + ")");
    String reason = conf.forbiddenResponse;

    BytesRestResponse resp;

    try {
      XContentBuilder reasonJson = jsonBuilder()
              .startObject()
              .field("error_message", reason)
              .endObject();
      if (acl.isBasicAuthConfigured()) {
        resp = new BytesRestResponse(RestStatus.UNAUTHORIZED, reasonJson);
        logger.debug("Sending login prompt header...");
        resp.addHeader("WWW-Authenticate", "Basic");
      } else {
        resp = new BytesRestResponse(RestStatus.FORBIDDEN, reasonJson);
      }
      channel.sendResponse(resp);
    } catch (IOException e) {
      logger.error("Construct forbidden reason failed", e);
    }
    return false;
  }

  @Override
  public boolean apply(String s, ActionResponse actionResponse, ActionListener actionListener) {
    return true;
  }
}
