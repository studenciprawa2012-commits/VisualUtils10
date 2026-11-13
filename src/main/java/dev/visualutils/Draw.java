package dev.visualutils;

import net.minecraft.client.gui.GuiGraphics;

/** Male narzedzia do rysowania (zaokraglone prostokaty). */
public final class Draw {

    private Draw() {}

    /** Prostokat z zaokraglonymi rogami o promieniu r. */
    public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        // srodek
        g.fill(x, y + r, x + w, y + h - r, color);
        // gora i dol z "scietymi" rogami
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt((double) r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    public static boolean inside(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }
}
