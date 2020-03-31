package net.sony.util;

public interface ProgressBar {

    void progressed(int unit, int total);

    void remaining(String group, int remaining);

    void current(String current);

    void start();

    void stop();

    void repaint();
}
