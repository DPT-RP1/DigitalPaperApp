package net.sony.dpt.ui.gui.whiteboard;

import net.sony.dpt.command.device.TakeScreenshotCommand;
import net.sony.util.ImageUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.awt.Image.SCALE_SMOOTH;

/**
 * Whiteboard attempt using the new fast screenshot API
 */
public class Whiteboard {

    private static final int DEVICE_WIDTH = 1650;
    private static final int DEVICE_HEIGHT = 2200;
    private final TakeScreenshotCommand takeScreenshotCommand;
    private final JFrame frame;
    private final JLabel label;
    private final Orientation orientation;
    private int rotationAngle = 0;
    private float scalingFactor;

    public Whiteboard(final TakeScreenshotCommand takeScreenshotCommand, Orientation orientation, float scalingFactor) throws IOException, InterruptedException {
        this.takeScreenshotCommand = takeScreenshotCommand;
        frame = new JFrame("Whiteboard");

        this.orientation = orientation;
        this.scalingFactor = scalingFactor;

        // We invert scale and rotate if needed
        int scaledWidth = (int) ((float) DEVICE_WIDTH * scalingFactor);
        int scaledHeight = (int) ((float) DEVICE_HEIGHT * scalingFactor);
        int width = scaledWidth;
        int height = scaledHeight;
        if (orientation == Orientation.LANDSCAPE) {
            width = scaledHeight;
            height = scaledWidth;
            rotationAngle = 90;
        }
        frame.setSize(width , height);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        label = new JLabel();

        JPanel panel = new JPanel();
        panel.add(label);
        frame.setContentPane(panel);

        redraw();
        frame.setVisible(true);

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            try {
                redraw();
            } catch (IOException ignored) {
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    public void redraw() throws IOException, InterruptedException {
        BufferedImage img = ImageIO.read(takeScreenshotCommand.fastScreenshot());

        if (orientation == Orientation.LANDSCAPE) {
            img = ImageUtils.rotate(
                    img,
                    rotationAngle,
                    frame.getGraphicsConfiguration());
        }

        ImageIcon icon = new ImageIcon(
                img.getScaledInstance(
                        (int) ((float) img.getWidth() * scalingFactor),
                        (int) ((float) img.getHeight() * scalingFactor),
                        SCALE_SMOOTH
                )
        );

        // Callback to the UI thread
        EventQueue.invokeLater(() -> {
            label.setIcon(icon);
            frame.revalidate();
            frame.repaint();
        });
    }

}
