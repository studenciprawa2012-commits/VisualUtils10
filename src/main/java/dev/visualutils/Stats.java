package dev.visualutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayDeque;

/** Liczniki liczone co tick: CPS (klikniecia na sekunde) i predkosc gracza. */
public final class Stats {

    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();
    private static boolean leftWas = false;
    private static boolean rightWas = false;

    private static double lastX = 0.0;
    private static double lastZ = 0.0;
    private static boolean hasLast = false;
    private static double speed = 0.0;

    private Stats() {}

    public static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();

        boolean left = mc.options.keyAttack.isDown();
        boolean right = mc.options.keyUse.isDown();
        if (left && !leftWas) LEFT.addLast(now);
        if (right && !rightWas) RIGHT.addLast(now);
        leftWas = left;
        rightWas = right;
        prune(LEFT, now);
        prune(RIGHT, now);

        LocalPlayer player = mc.player;
        if (player == null) {
            hasLast = false;
            speed = 0.0;
            return;
        }
        if (hasLast) {
            double dx = player.getX() - lastX;
            double dz = player.getZ() - lastZ;
            speed = Math.sqrt(dx * dx + dz * dz) * 20.0; // 20 tickow = 1 sekunda
        }
        lastX = player.getX();
        lastZ = player.getZ();
        hasLast = true;
    }

    private static void prune(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000L) {
            q.pollFirst();
        }
    }

    public static int leftCps() {
        return LEFT.size();
    }

    public static int rightCps() {
        return RIGHT.size();
    }

    /** Predkosc pozioma w blokach na sekunde. */
    public static double speed() {
        return speed;
    }
}
