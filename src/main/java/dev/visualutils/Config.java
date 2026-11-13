package dev.visualutils;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Properties;

/** Zapamietuje wlaczone moduly, ustawienia i motyw (plik config/visualutils.properties). */
public final class Config {

    private static final EnumMap<Module, Boolean> STATE = new EnumMap<>(Module.class);
    private static final EnumMap<Setting, Integer> SETTINGS = new EnumMap<>(Setting.class);
    private static int theme = 0;

    private Config() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("visualutils.properties");
    }

    public static void load() {
        Properties props = new Properties();
        Path f = file();
        if (Files.exists(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                props.load(in);
            } catch (IOException ignored) {
                // brak pliku albo blad odczytu - zostaja ustawienia domyslne (wszystko wylaczone)
            }
        }
        for (Module m : Module.values()) {
            STATE.put(m, Boolean.parseBoolean(props.getProperty(m.name(), "false")));
        }
        for (Setting s : Setting.values()) {
            int value = s.defaultIndex();
            try {
                value = Integer.parseInt(props.getProperty("S_" + s.name(), Integer.toString(value)).trim());
            } catch (NumberFormatException ignored) {
                // zostaje domyslna wartosc
            }
            if (value < 0 || value >= s.count()) value = s.defaultIndex();
            SETTINGS.put(s, value);
        }
        try {
            theme = Integer.parseInt(props.getProperty("THEME", "0").trim());
        } catch (NumberFormatException e) {
            theme = 0;
        }
    }

    public static void save() {
        Properties props = new Properties();
        for (Module m : Module.values()) {
            props.setProperty(m.name(), Boolean.toString(isOn(m)));
        }
        for (Setting s : Setting.values()) {
            props.setProperty("S_" + s.name(), Integer.toString(setting(s)));
        }
        props.setProperty("THEME", Integer.toString(theme));
        try (OutputStream out = Files.newOutputStream(file())) {
            props.store(out, "NullClient");
        } catch (IOException ignored) {
            // nie udalo sie zapisac - ustawienia po prostu nie zostana zapamietane
        }
    }

    public static boolean isOn(Module m) {
        return STATE.getOrDefault(m, false);
    }

    public static boolean toggle(Module m) {
        boolean value = !isOn(m);
        STATE.put(m, value);
        return value;
    }

    /** Numer wybranej wartosci ustawienia (0, 1, 2...). */
    public static int setting(Setting s) {
        return SETTINGS.getOrDefault(s, s.defaultIndex());
    }

    /** Przelacza ustawienie na nastepna wartosc. */
    public static void cycle(Setting s) {
        SETTINGS.put(s, (setting(s) + 1) % s.count());
    }

    /** Przelacza ustawienie na poprzednia wartosc (prawy klik w menu). */
    public static void cyclePrev(Setting s) {
        SETTINGS.put(s, Math.floorMod(setting(s) - 1, s.count()));
    }

    /** Przywraca domyslne wartosci wszystkich ustawien (moduly zostaja bez zmian). */
    public static void resetSettings() {
        for (Setting s : Setting.values()) {
            SETTINGS.put(s, s.defaultIndex());
        }
    }

    public static String settingText(Setting s) {
        return s.valueAt(setting(s));
    }

    /** Dla ustawien liczbowych (zasiegi, FOV): wartosc jako liczba. */
    public static int settingInt(Setting s) {
        try {
            return Integer.parseInt(settingText(s));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int getTheme() {
        return theme;
    }

    public static void setTheme(int value) {
        theme = value;
    }
}
