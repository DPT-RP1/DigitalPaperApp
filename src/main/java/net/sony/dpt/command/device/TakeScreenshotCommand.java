package net.sony.dpt.command.device;

import net.sony.dpt.DigitalPaperEndpoint;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class TakeScreenshotCommand {

    private final DigitalPaperEndpoint digitalPaperEndpoint;

    public TakeScreenshotCommand(DigitalPaperEndpoint digitalPaperEndpoint) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
    }

    public InputStream takeScreenshot() throws IOException, InterruptedException {
        InputStream screenshot = digitalPaperEndpoint.takeScreenshot();

        try (ByteArrayOutputStream memoryCopy = new ByteArrayOutputStream()) {
            IOUtils.copy(screenshot, memoryCopy);
            screenshot.close();
            return new ByteArrayInputStream(memoryCopy.toByteArray());
        }
    }

    /**
     * Takes a jpeg, with not screenshot animation. Used by the official app to do their
     * capture whiteboard
     *
     * @return A JPEG stream, directly from the server, without memory copy.
     */
    public InputStream fastScreenshot() throws IOException, InterruptedException {
        return digitalPaperEndpoint.takeFastScreenshot();
    }

}
