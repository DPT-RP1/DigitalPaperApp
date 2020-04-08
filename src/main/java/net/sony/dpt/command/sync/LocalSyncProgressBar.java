package net.sony.dpt.command.sync;

import net.sony.util.ProgressBar;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Basically unusable since modal management is subpar
 */
public class LocalSyncProgressBar implements ProgressBar {

    private final PrintStream printStream;
    private final Map<String, Integer> remainingPerGroup;
    private final ProgressStyle style;
    private int percentDone;
    private String currentTask;
    private String dialogText;
    private int totalSizeMB;
    private int doneSizeMB;

    public LocalSyncProgressBar(final PrintStream printStream, final ProgressStyle style) {
        this.printStream = printStream;
        remainingPerGroup = new HashMap<>();
        percentDone = 0;
        this.style = style;
    }

    @Override
    public void progressed(int unit, int total) {
        if (total == 0) percentDone = 100;
        else percentDone = (int) (((double) unit / (double) total) * 100);
    }

    @Override
    public void progressedSize(int unit, int total) {
        this.totalSizeMB = total;
        this.doneSizeMB = unit;
    }

    @Override
    public void remaining(String group, int remaining) {
    }

    @Override
    public void current(String current) {
        this.currentTask = current;
    }

    @Override
    public void start() {
        dialogText = "Synchronization preparing...";
    }

    @Override
    public void stop() {
        currentTask = "\nSynchronization complete !";
        repaint();
    }

    @Override
    public void repaint() {
        dialogText = style.generateSequence(percentDone, 24) + " " + percentDone + "% (" + doneSizeMB + " / " + totalSizeMB + "MB) ";
        dialogText += currentTask + "   ";

        paint();
    }

    private void paint() {
        printStream.print(dialogText + "\r\033[2K");
    }
}
