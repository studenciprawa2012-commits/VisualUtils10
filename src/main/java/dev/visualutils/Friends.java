package dev.visualutils;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Lista znajomych (zapisana w config/visualutils_friends.txt). Znajomi swieca sie na zielono. */
public final class Friends {

    public static final int COLOR = 0xFF3DFF6E;

    private static final Set<String> NAMES = new TreeSet<>();

    private Friends() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("visualutils_friends.txt");
    }

    public static void load() {
        NAMES.clear();
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String n = line.trim().toLowerCase(Locale.ROOT);
                if (!n.isEmpty()) NAMES.add(n);
            }
        } catch (IOException ignored) {
            // brak listy to nie blad - zaczynamy od pustej
        }
    }

    private static void save() {
        try {
            Files.write(file(), new ArrayList<>(NAMES), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // zapis sie nie udal - lista zostanie w pamieci do konca sesji
        }
    }

    public static boolean isFriend(Entity e) {
        return Config.isOn(Module.FRIENDS) && e instanceof Player p
                && NAMES.contains(p.getName().getString().toLowerCase(Locale.ROOT));
    }

    public static List<String> names() {
        return new ArrayList<>(NAMES);
    }

    /** Dodaje albo usuwa gracza, na ktorego patrzysz (klawisz akcji). */
    public static void toggleLooked(Minecraft mc) {
        if (mc.player == null) return;
        Entity e = mc.crosshairPickEntity;
        if (!(e instanceof Player p)) {
            mc.player.displayClientMessage(Component.literal("Friend: najedź celownikiem na gracza"), true);
            return;
        }
        String name = p.getName().getString();
        String key = name.toLowerCase(Locale.ROOT);
        if (NAMES.remove(key)) {
            mc.player.displayClientMessage(Component.literal("Friend: usunięto " + name), true);
        } else {
            NAMES.add(key);
            mc.player.displayClientMessage(Component.literal("Friend: dodano " + name + " (świeci na zielono)"), true);
        }
        save();
    }
}
