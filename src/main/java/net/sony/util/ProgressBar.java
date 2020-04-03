package net.sony.util;

public interface ProgressBar {

    void progressedSize(int unit, int total);

    void progressed(int unit, int total);

    enum ProgressStyle {
        RAISING_FILLED("▁▂▃▄▅▆▇█"),
        RAISING_DOTS("⣀⣄⣤⣦⣶⣷⣿"),
        CAMEMBERT("○◔◐◕●"),
        SQUARES_1("□◱◧▣■"),
        SQUARES_2("□◱▨▩■"),
        SQUARES_3("□◱▥▦■"),
        RECTANGLES_1("░▒▓█"),
        RECTANGLES_2("░█"),
        BLACK_WHITE("⬜⬛"),
        LOZANGES_SMALL("▱▰"),
        SQUARES_SMALL("▭◼"),
        RECTANGLES_SMALL("▯▮"),
        CIRCLES_1("◯●"),
        CIRCLES_2("⚪⚫"),
        BLOCKS(" ▏▎▍▌▋▊▉█");

        private String sequence;

        ProgressStyle(String sequence) {
            this.sequence = sequence;
        }

        public String generateSequence(int progress, int stringLength) {
            String emptySymbol = sequence.substring(0, 1);
            String fullSymbol = sequence.substring(sequence.length() - 1, sequence.length());

            if (progress == 0) return emptySymbol.repeat(stringLength);
            if (progress == 100) return fullSymbol.repeat(stringLength);

            int lastIndex = (sequence.length() - 1);
            double currentProgress = (double) progress / 100.;

            double progressLength = currentProgress * stringLength;
            double full = Math.floor(progressLength);
            double rest = progressLength - full;
            double middle = Math.floor(rest * lastIndex);
            if (full == 0 && middle == 0) middle = 1;

            String middleChar = sequence.substring((int) middle, (int) middle + 1);
            if (full == stringLength) middleChar = "";
            return fullSymbol.repeat((int) full) + middleChar + emptySymbol.repeat(stringLength - (int) full - 1);
        }
    }

    void remaining(String group, int remaining);

    void current(String current);

    void start();

    void stop();

    void repaint();
}
