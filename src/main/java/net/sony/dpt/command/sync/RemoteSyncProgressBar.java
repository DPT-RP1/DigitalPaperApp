package net.sony.dpt.command.sync;

import net.sony.dpt.DigitalPaperEndpoint;
import net.sony.util.ProgressBar;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Basically unusable since modal management is subpar - the DPT can track and "reuse" a modal,
 * but needs to make it reappear non stop.
 */
public class RemoteSyncProgressBar implements ProgressBar {

    // The DPT cannot handle enough refreshing too often, repaints should staggered
    private static final int REFRESH_DELAY_MS = 10000;
    private final DigitalPaperEndpoint digitalPaperEndpoint;
    private final Map<String, Integer> remainingPerGroup;
    private final UUID dialogUUID;
    private final ProgressStyle style;
    private int percentDone;
    private String currentTask;
    private String dialogText;
    private boolean animate;
    private long lastRepaintMs = 0;

    public RemoteSyncProgressBar(final DigitalPaperEndpoint digitalPaperEndpoint, final ProgressStyle style) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
        remainingPerGroup = new HashMap<>();
        percentDone = 0;
        dialogUUID = UUID.randomUUID();
        dialogText = "Synchronization will start soon";
        animate = false;
        this.style = style;
    }

    @Override
    public void progressed(int unit, int total) {
        if (total == 0) percentDone = 100;
        else percentDone = (int) (((double) unit / (double) total) * 100);
    }

    @Override
    public void progressedSize(int unit, int total) {

    }

    @Override
    public void remaining(String group, int remaining) {
        remainingPerGroup.put(group, remaining);
    }

    @Override
    public void current(String current) {
        this.currentTask = current;
    }

    @Override
    public void start() {
        dialogText = "Synchronization preparing...";
        animate = false;
        repaint();
    }

    @Override
    public void stop() {
        currentTask = "Synchronization complete !";
        lastRepaintMs = 0;
        animate = false;
        repaint();
    }

    @Override
    public void repaint() {
        dialogText = style.generateSequence(percentDone, 24) + " " + percentDone + "%\n";
        StringBuilder remaining = new StringBuilder();
        for (String group : remainingPerGroup.keySet()) {
             remaining.append(group).append(": ").append(remainingPerGroup.get(group)).append(" remaining\n");
        }
        dialogText += remaining;
        if (currentTask != null && !currentTask.isEmpty()) {
            dialogText += currentTask;
        }
        if (lastRepaintMs == 0 || lastRepaintMs + REFRESH_DELAY_MS <= new Date().getTime()) {
            paint();
            lastRepaintMs = new Date().getTime();
        }
    }

    private void paint() {
        try {
            digitalPaperEndpoint.showDialog(dialogUUID.toString(), "Synchronization in progress", dialogText, "Hide", animate);
        } catch (Exception ignored) {
        }
    }
}
