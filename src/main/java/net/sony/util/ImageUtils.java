package net.sony.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.sony.util.AsyncUtils.waitForFinished;

public class ImageUtils {

    public static BufferedImage rotate(BufferedImage image, double angle, GraphicsConfiguration gc) {
        angle = Math.toRadians(angle);
        double sin = Math.abs(Math.sin(angle)), cos = Math.abs(Math.cos(angle));
        int width = image.getWidth();
        int height = image.getHeight();
        int rotatedWidth = (int) Math.floor(width * cos + height * sin);
        int rotatedHeight = (int) Math.floor(height * cos + width * sin);

        BufferedImage result = gc.createCompatibleImage(rotatedWidth, rotatedHeight, Transparency.TRANSLUCENT);
        Graphics2D g = result.createGraphics();
        g.translate((rotatedWidth - width) / 2, (rotatedHeight - height) / 2);

        g.rotate(angle, (double) width / 2, (double) height / 2);
        g.drawRenderedImage(image, null);
        g.dispose();
        return result;
    }

    public static byte[] resize(byte[] original, String requiredFormat, int requiredWidth, int requiredHeight) throws IOException, InterruptedException {
        BufferedImage inputImage = ImageIO.read(new ByteArrayInputStream(original));

        // creates output image
        BufferedImage outputImage = new BufferedImage(requiredWidth, requiredHeight, inputImage.getType());

        // scales the input image to the output image
        Graphics2D g2d = outputImage.createGraphics();
        AtomicBoolean imageLoaded = new AtomicBoolean(false);
        g2d.drawImage(inputImage, 0, 0, requiredWidth, requiredHeight, (img, infoflags, x, y, width, height) -> {
            if ((infoflags & ImageObserver.ALLBITS) != 0) {

                imageLoaded.set(true);
                return false;
            }
            return true;
        });

        waitForFinished(imageLoaded, 30);
        g2d.dispose();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        // writes to output file
        ImageIO.write(outputImage, requiredFormat, outputStream);
        return outputStream.toByteArray();
    }

}
