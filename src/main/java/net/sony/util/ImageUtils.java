package net.sony.util;

import java.awt.*;
import java.awt.image.BufferedImage;

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

}
