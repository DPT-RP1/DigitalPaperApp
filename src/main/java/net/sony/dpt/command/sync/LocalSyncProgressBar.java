package net.sony.dpt.command.sync;

import net.sony.util.ProgressBar;

/**
 * Basically unusable since modal management is subpar
 */
public class LocalSyncProgressBar implements ProgressBar {

    private final ProgressStyle style;
    private int percentDone;
    private String currentTask;
    private String dialogText;
    private int totalSizeMB;
    private int doneSizeMB;

    public LocalSyncProgressBar(final ProgressStyle style) {
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
        currentTask = "Synchronization complete !";
        repaint();
    }

    @Override
    public void repaint() {
        dialogText = style.generateSequence(percentDone, 24) + " " + percentDone + "% (" + doneSizeMB + " / " + totalSizeMB + "MB) ";
        dialogText += currentTask;

        paint();
    }

    private void paint() {
        System.out.print(dialogText + "\r");
    }
}
