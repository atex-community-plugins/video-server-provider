package com.atex.plugins.video.server.provider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.core.MediaType;

import com.atex.plugins.social.manager.SocialManager;
import com.atex.plugins.social.manager.SocialManagerProvider;
import com.atex.plugins.video.VideoContentDataBean;
import com.atex.plugins.video.VideoEncoderProvider;
import com.atex.plugins.video.VideoPolicy;
import com.google.common.base.Strings;
import com.polopoly.application.Application;
import com.polopoly.cm.ContentId;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.client.CmClient;
import com.polopoly.cm.client.CmClientBase;
import com.polopoly.cm.policy.Policy;
import com.polopoly.cm.policy.PolicyCMServer;
import com.polopoly.siteengine.structure.ParentPathResolver;
import com.polopoly.siteengine.structure.Site;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.multipart.FormDataMultiPart;

/**
 * Our own video server provider.
 *
 * @author mnova
 */
public class VideoServerProvider implements VideoEncoderProvider {

    private Application application = null;

    @Override
    public VideoEncoderProvider setApplication(final Application application) {
        this.application = application;
        return this;
    }

    @Override
    public void process(final VideoPolicy videoPolicy) throws CMException, IOException {

        final VideoContentDataBean data = videoPolicy.getContentData();

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        videoPolicy.exportFile(data.getVideoPath(), os);

        String videoUUID = videoPolicy.getVideoUUID();
        if (videoUUID == null) {
            videoUUID = UUID.randomUUID().toString();
        }

        final InputStream stream = new ByteArrayInputStream(os.toByteArray());
        final FormDataMultiPart part = new FormDataMultiPart()
                .field("file", stream, MediaType.APPLICATION_OCTET_STREAM_TYPE)
                .field("contentId", videoPolicy.getContentId().getContentId().getContentIdString())
                .field("videoUUID", videoUUID)
                .field("videoName", videoPolicy.getName())
                .field("siteCode", getSiteCode(videoPolicy))
                .field("webhook", getUpdateVideoUrl(videoPolicy.getContentId()));

        WebResource resource = Client.create().resource(getVideoUploadUrl());
        resource.type(MediaType.MULTIPART_FORM_DATA_TYPE).post(part);
    }

    private String getSiteCode(final VideoPolicy videoPolicy) throws CMException {
        final PolicyCMServer cmServer = videoPolicy.getCMServer();
        final List<ContentId> parents = new ParentPathResolver().getParentPathAsList(videoPolicy, cmServer);
        for (int idx = parents.size() - 1; idx >= 0; idx--) {
            final Policy policy = cmServer.getPolicy(parents.get(idx));
            if (policy instanceof Site) {
                return policy.getContent().getExternalId().getExternalId();
            }
        }
        return null;
    }

    private String getUpdateVideoUrl(final ContentId contentId) {
        final SocialManager socialManager = getSocialManager();
        String integrationServerUrl = socialManager.getIntegrationServerURL();
        if (!Strings.isNullOrEmpty(integrationServerUrl)) {
            if (!integrationServerUrl.endsWith("/")) {
                integrationServerUrl += "/";
            }
            integrationServerUrl += "videomanager/" + contentId.getContentId().getContentIdString();
        }
        return integrationServerUrl;
    }

    private SocialManager getSocialManager() {
        try {
            return new SocialManagerProvider(getApplication()).getSocialManager();
        } catch (CMException e) {
            throw new RuntimeException(e);
        }
    }

    private Application getApplication() {
        return application;
    }

    private <T> T getApplicationComponent(final String name) {
        final Application application = getApplication();
        return (T) application.getApplicationComponent(name);
    }

    private String getVideoUploadUrl() {
        try {
            return ConfigurationPolicy.getConfiguration(getCMServer()).getVideoUploadUrl();
        } catch (CMException e) {
            throw new RuntimeException(e);
        }
    }

    public PolicyCMServer getCMServer() {
        final CmClient cmClient = getApplicationComponent(CmClientBase.DEFAULT_COMPOUND_NAME);
        return cmClient.getPolicyCMServer();
    }

}
