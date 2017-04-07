package com.atex.plugins.video.server.provider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.atex.plugins.video.VideoContentDataBean;
import com.atex.plugins.video.VideoPolicy;
import com.atex.plugins.video.VideoStreamNotFoundException;
import com.atex.plugins.video.VideoStreamProvider;
import com.google.common.base.Strings;
import com.polopoly.cm.client.CMException;

/**
 * An implementation of {@link VideoStreamProvider} for content stored in the video policy.
 *
 * @author mnova
 */
public class VideoPolicyStreamProvider implements VideoStreamProvider {

    @Override
    public InputStream fetch(final VideoPolicy videoPolicy) throws CMException, IOException {

        final VideoContentDataBean data = videoPolicy.getContentData();

        if (Strings.isNullOrEmpty(data.getVideoPath())) {
            throw new VideoStreamNotFoundException();
        }

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        videoPolicy.exportFile(data.getVideoPath(), os);

        return new ByteArrayInputStream(os.toByteArray());
    }

    @Override
    public String getSourceFilename(final VideoPolicy videoPolicy) throws CMException {
        final VideoContentDataBean data = videoPolicy.getContentData();
        return data.getVideoPath();
    }

}
