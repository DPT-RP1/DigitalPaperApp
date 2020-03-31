package net.sony.dpt.command.sync;

import net.sony.dpt.DigitalPaperEndpoint;
import net.sony.util.ProgressBar;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RemoteSyncProgressBar implements ProgressBar {

    private final DigitalPaperEndpoint digitalPaperEndpoint;
    private final Map<String, Integer> remainingPerGroup;
    private final UUID dialogUUID;
    private int percentDone;
    private String currentTask;

    private String dialogText;

    public RemoteSyncProgressBar(DigitalPaperEndpoint digitalPaperEndpoint) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
        remainingPerGroup = new HashMap<>();
        percentDone = 0;
        dialogUUID = UUID.randomUUID();
        dialogText = "Synchronization will start soon";
    }

    @Override
    public void progressed(int unit, int total) {
        if (total == 0) percentDone = 100;
        else percentDone = unit / total * 100;
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
        repaint();
    }

    @Override
    public void stop() {
        // TODO: auto hide the dialog
    }

    @Override
    public void repaint() {
        dialogText = "Synchronizing your Digital Paper... " + percentDone + "%\n";
        for (String group : remainingPerGroup.keySet()) {
            dialogText += group + ": " + remainingPerGroup.get(group) + "\n";
        }
        if (currentTask != null && !currentTask.isEmpty()) {
            dialogText += currentTask;
        }
        paint();
    }

    private void paint() {
        try {
            digitalPaperEndpoint.showDialog(dialogUUID.toString(), "Synchronization in progress", dialogText, "Hide", true);
        } catch (Exception ignored) {
        }
    }
}
