package dev.visualutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Waypointy (znaczniki na ekranie). Trzymane w pamieci do zamkniecia gry. */
public final class Waypoints {

    public static final String DEATH = "Śmierć";
    private static final int MAX = 20;

    public record Waypoint(String name, double x, double y, double z, String dim) {}

    private static final List<Waypoint> LIST = new ArrayList<>();
    private static int counter = 1;

    private Waypoints() {}

    public static List<Waypoint> all() {
        return LIST;
    }

    /** Nazwa wymiaru jako tekst - waypoint pokazujemy tylko w tym wymiarze, w ktorym go ustawiono. */
    public static String dimOf(Minecraft mc) {
        return mc.level == null ? "" : mc.level.dimension().toString();
    }

    public static void addHere(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        // limit: usuwamy najstarszy zwykly waypoint
        while (LIST.size() >= MAX) {
            boolean removed = false;
            for (int i = 0; i < LIST.size(); i++) {
                if (!DEATH.equals(LIST.get(i).name())) {
                    LIST.remove(i);
                    removed = true;
                    break;
                }
            }
            if (!removed) break;
        }
        String name = "WP " + counter++;
        LIST.add(new Waypoint(name, p.getX(), p.getY(), p.getZ(), dimOf(mc)));
        p.displayClientMessage(Component.literal("Dodano waypoint: " + name), true);
    }

    /** Zapisuje miejsce smierci (jest tylko jedno - nowe zastepuje stare). */
    public static void setDeath(Minecraft mc, double x, double y, double z) {
        LIST.removeIf(w -> DEATH.equals(w.name()));
        LIST.add(new Waypoint(DEATH, x, y, z, dimOf(mc)));
    }

    /** Usuwa najblizszy waypoint w tym wymiarze. */
    public static void removeNearest(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        String dim = dimOf(mc);
        Waypoint best = null;
        double bestDist = Double.MAX_VALUE;
        for (Waypoint w : LIST) {
            if (!w.dim().equals(dim)) continue;
            double dx = w.x() - p.getX();
            double dy = w.y() - p.getY();
            double dz = w.z() - p.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = w;
            }
        }
        if (best != null) {
            LIST.remove(best);
            p.displayClientMessage(Component.literal("Usunięto waypoint: " + best.name()), true);
        } else {
            p.displayClientMessage(Component.literal("Brak waypointów w tym wymiarze"), true);
        }
    }
}
