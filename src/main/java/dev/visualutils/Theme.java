package dev.visualutils;

/** Kolory motywu (akcent menu i HUD) oraz male funkcje pomocnicze do kolorow. */
public final class Theme {

    /** Ostatni motyw (indeks 6) to teczowy - kolor plynnie zmienia sie w czasie. */
    public static final int RAINBOW = 6;

    public static final int[] ACCENTS = {
            0xFF8B6CFF, // fiolet
            0xFF3DDC97, // zielony
            0xFF4DA3FF, // niebieski
            0xFFFF6B6B, // czerwony
            0xFFFFB84D, // pomaranczowy
            0xFFFF6BD6, // rozowy
            0xFFFFFFFF  // teczowy (kolor liczony na biezaco)
    };

    private Theme() {}

    public static int accent() {
        return swatch(Math.floorMod(Config.getTheme(), ACCENTS.length));
    }

    /** Kolor probki motywu o danym numerze (dla teczowego - aktualny kolor). */
    public static int swatch(int index) {
        return index == RAINBOW ? rainbow() : ACCENTS[index];
    }

    private static int rainbow() {
        float hue = (System.currentTimeMillis() % 6000L) / 6000f;
        return hsv(hue, 0.65f, 1f);
    }

    private static int hsv(float h, float s, float v) {
        float h6 = h * 6f;
        int i = (int) Math.floor(h6) % 6;
        float f = h6 - (float) Math.floor(h6);
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        float r;
        float g;
        float b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | ((int) (r * 255f) << 16) | ((int) (g * 255f) << 8) | (int) (b * 255f);
    }

    /** Ten sam kolor, ale z innym przezroczystoscia (alpha 0-255). */
    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /** Plynne przejscie miedzy dwoma kolorami ARGB. */
    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) (((a >>> 24) & 0xFF) + ((((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t));
        int ar = (int) (((a >>> 16) & 0xFF) + ((((b >>> 16) & 0xFF) - ((a >>> 16) & 0xFF)) * t));
        int ag = (int) (((a >>> 8) & 0xFF) + ((((b >>> 8) & 0xFF) - ((a >>> 8) & 0xFF)) * t));
        int ab = (int) ((a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * t));
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }
}
