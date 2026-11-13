package dev.visualutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Lista graczy w poblizu (liczona co tick) oraz alert, gdy ktos nowy sie zblizy. */
public final class Nearby {

    public record Entry(String name, double dist) {}

    private static List<Entry> entries = List.of();
    private static Set<UUID> previous = new HashSet<>();
    private static boolean initialised = false;

    private Nearby() {}

    public static List<Entry> entries() {
        return entries;
    }

    public static void tick(Minecraft mc) {
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null || !Config.isOn(Module.NEARBY)) {
            entries = List.of();
            previous = new HashSet<>();
            initialised = false;
            return;
        }

        double range = Config.settingInt(Setting.NEARBY_RANGE);
        List<Entry> list = new ArrayList<>();
        Set<UUID> current = new HashSet<>();
        String alert = null;

        for (AbstractClientPlayer other : mc.level.players()) {
            if (other == me || !other.isAlive()) continue;
            double dist = other.distanceTo(me);
            if (dist > range) continue;

            list.add(new Entry(other.getName().getString(), dist));
            current.add(other.getUUID());
            if (initialised && !previous.contains(other.getUUID())) {
                alert = "⚠ " + other.getName().getString() + " w zasięgu (" + (int) dist + "m)";
            }
        }

        list.sort(Comparator.comparingDouble(Entry::dist));
        entries = list;
        previous = current;
        initialised = true;

        if (alert != null) {
            me.displayClientMessage(Component.literal(alert), true);
        }
    }
}
