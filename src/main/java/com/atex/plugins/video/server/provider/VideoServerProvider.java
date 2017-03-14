package com.atex.plugins.video.server.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.ws.rs.core.MediaType;

import com.atex.plugins.social.manager.SocialManager;
import com.atex.plugins.social.manager.SocialManagerProvider;
import com.atex.plugins.video.ConfigurationPolicy;
import com.atex.plugins.video.VideoEncoderProvider;
import com.atex.plugins.video.VideoPolicy;
import com.atex.plugins.video.VideoStreamProvider;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
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

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * Our own video server provider.
 *
 * @author mnova
 */
public class VideoServerProvider implements VideoEncoderProvider {

    public static final long ONE_DAY = 1000l * 60 * 60 * 24;
    private Application application = null;

    @Override
    public VideoEncoderProvider setApplication(final Application application) {
        this.application = application;
        return this;
    }

    @Override
    public void process(final VideoPolicy videoPolicy, final VideoStreamProvider streamProvider) throws CMException, IOException {

        final ConfigurationProviderPolicy config = getConfig();

        final String videoUUID = Optional
                .ofNullable(videoPolicy.getVideoUUID())
                .orElse(UUID.randomUUID().toString());

        final String jwt = createJWT(videoPolicy.getContentId(), getVideoConfig().getJWTSecret());

        final String filename = streamProvider.getSourceFilename(videoPolicy);
        final InputStream stream = streamProvider.fetch(videoPolicy);
        final FormDataMultiPart part = new FormDataMultiPart()
                .field("file", stream, MediaType.APPLICATION_OCTET_STREAM_TYPE)
                .field("contentId", videoPolicy.getContentId().getContentId().getContentIdString())
                .field("videoUUID", videoUUID)
                .field("videoName", videoPolicy.getName())
                .field("fileName", filename)
                .field("siteCode", getSiteCode(videoPolicy))
                .field("webhook", getUpdateVideoUrl(videoPolicy.getContentId()))
                .field("jwt", jwt);

        WebResource resource = Client.create().resource(config.getVideoUploadUrl());
        resource.type(MediaType.MULTIPART_FORM_DATA_TYPE).post(part);
    }

    private String createJWT(final ContentId contentId, final String secret) throws CMException {
        try {
            final Date creation = new Date();
            final Date expirationTime = new Date(creation.getTime() + ONE_DAY);

            final Map<String, Object> claims = Maps.newHashMap();
            claims.put("provider", "video-server-provider");
            claims.put("contentId", contentId.getContentId().getContentIdString());

            return Jwts.builder()
                       .setSubject(contentId.getContentId().getContentIdString())
                       .setClaims(claims)
                       .setExpiration(expirationTime)
                       .setIssuedAt(creation)
                       .signWith(
                               SignatureAlgorithm.HS512,
                               secret.getBytes("UTF-8"))
                       .compact();
        } catch (UnsupportedEncodingException e) {
            throw new CMException(e);
        }
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

    private ConfigurationProviderPolicy getConfig() {
        try {
            return ConfigurationProviderPolicy.getConfiguration(getCMServer());
        } catch (CMException e) {
            throw new RuntimeException(e);
        }
    }

    private ConfigurationPolicy getVideoConfig() {
        try {
            return ConfigurationPolicy.getConfiguration(getCMServer());
        } catch (CMException e) {
            throw new RuntimeException(e);
        }
    }

    public PolicyCMServer getCMServer() {
        final CmClient cmClient = getApplicationComponent(CmClientBase.DEFAULT_COMPOUND_NAME);
        return cmClient.getPolicyCMServer();
    }

}
