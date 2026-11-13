package dev.visualutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/** Prosty licznik walki: ile razy oberwales i ile razy machnales w gracza. Zeruje sie po 10 s ciszy. */
public final class HitInfo {

    private static int taken = 0;
    private static int swings = 0;
    private static int idle = 0;
    private static boolean prevHurt = false;
    private static boolean prevAttack = false;

    private HitInfo() {}

    public static int taken() {
        return taken;
    }

    public static int swings() {
        return swings;
    }

    public static boolean active() {
        return taken > 0 || swings > 0;
    }

    public static void tick(Minecraft mc) {
        LocalPlayer me = mc.player;
        if (me == null || !Config.isOn(Module.HITINFO)) {
            taken = 0;
            swings = 0;
            idle = 0;
            prevHurt = false;
            prevAttack = false;
            return;
        }
        boolean hurt = me.hurtTime > 0;
        boolean attack = mc.screen == null && mc.options.keyAttack.isDown();
        boolean changed = false;
        if (hurt && !prevHurt) {
            taken++;
            changed = true;
        }
        if (attack && !prevAttack && mc.crosshairPickEntity instanceof Player) {
            swings++;
            changed = true;
        }
        prevHurt = hurt;
        prevAttack = attack;
        idle = changed ? 0 : idle + 1;
        if (idle > 200) {
            taken = 0;
            swings = 0;
            idle = 0;
        }
    }
}
