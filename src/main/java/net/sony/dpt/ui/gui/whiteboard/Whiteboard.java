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

import static java.awt.Image.SCALE_DEFAULT;

/**
 * Whiteboard attempt using the new fast screenshot API
 */
public class Whiteboard {

    private static final int DEVICE_WIDTH = 1650;
    private static final int DEVICE_HEIGHT = 2200;
    private final TakeScreenshotCommand takeScreenshotCommand;
    private final JFrame frame;
    private final JLabel label;

    public Whiteboard(TakeScreenshotCommand takeScreenshotCommand) throws IOException, InterruptedException {
        this.takeScreenshotCommand = takeScreenshotCommand;
        frame = new JFrame("Whiteboard");

        // We invert width and height and divide by 2 for rotation + scaling of the screenshots
        frame.setSize(DEVICE_HEIGHT / 2, DEVICE_WIDTH / 2);
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
            } catch (IOException | InterruptedException ignored) {
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

    }

    public void redraw() throws IOException, InterruptedException {
        BufferedImage img = ImageIO.read(takeScreenshotCommand.fastScreenshot());
        BufferedImage rotated = ImageUtils.rotate(
                img,
                90,
                frame.getGraphicsConfiguration());

        ImageIcon icon = new ImageIcon(rotated.getScaledInstance(rotated.getWidth() / 2, rotated.getHeight() / 2, SCALE_DEFAULT));

        // Callback to the UI thread
        EventQueue.invokeLater(() -> {
            label.setIcon(icon);
            frame.revalidate();
            frame.repaint();
        });
    }

}
