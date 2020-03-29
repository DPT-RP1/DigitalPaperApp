package net.sony.dpt.ui.gui.whiteboard;

import net.sony.dpt.command.device.TakeScreenshotCommand;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static java.awt.Image.SCALE_DEFAULT;

/**
 * VERY crude whiteboard attempt
 * TODO:
 * - rotate to landscape
 * - find maybe a better way to stream -> How does the official do ?
 */
public class Whiteboard {

    private TakeScreenshotCommand takeScreenshotCommand;
    private JFrame frame;

    public Whiteboard(TakeScreenshotCommand takeScreenshotCommand) throws IOException, InterruptedException {
        this.takeScreenshotCommand = takeScreenshotCommand;
        frame = new JFrame("Whiteboard");
        frame.setSize(1650 / 2, 2200 / 2);
        redraw();
        frame.setVisible(true);

        new Thread(() -> {
            try {
                while (true) {
                    redraw();
                    Thread.sleep(10000);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void redraw() throws IOException, InterruptedException {
        BufferedImage img = ImageIO.read(takeScreenshotCommand.takeScreenshot());
        Image currentScaledImage = img.getScaledInstance(1650 / 2, 2200 / 2, SCALE_DEFAULT);
        ImageIcon icon = new ImageIcon(currentScaledImage);
        JLabel label = new JLabel(icon);

        JPanel panel = new JPanel();
        panel.add(label);

        EventQueue.invokeLater(() -> {
            frame.setContentPane(panel);
            frame.revalidate();
            frame.repaint();
        });
    }

}
