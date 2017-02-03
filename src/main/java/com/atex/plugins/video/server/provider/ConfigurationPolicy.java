package com.atex.plugins.video.server.provider;

import com.atex.plugins.baseline.policy.BaselinePolicy;
import com.polopoly.cm.ExternalContentId;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.policy.PolicyCMServer;

/**
 * The configuration policy.
 *
 * @author mnova
 */
public class ConfigurationPolicy extends BaselinePolicy {

    public static final String EXTERNALID = "plugins.com.atex.plugins.video-server-provider.Config";

    private static final String VIDEOSERVER_BACKEND_URL = "videoServerBackendUrl";

    public String getVideoserverBackendUrl() {
        return getChildValue(VIDEOSERVER_BACKEND_URL, "");
    }

    public String getVideoUploadUrl() {
        return getVideoserverBackendUrl() + "/api/video/upload";
    }

    public static ConfigurationPolicy getConfiguration(final PolicyCMServer cmServer) throws CMException {
        final ExternalContentId contentId = new ExternalContentId(EXTERNALID);
        return (ConfigurationPolicy) cmServer.getPolicy(contentId);
    }

}
