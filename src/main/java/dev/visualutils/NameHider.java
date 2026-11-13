package dev.visualutils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Decyduje, czy ukryc zwykly (vanillowy) nametag nad celem, bo Nametagi+ rysuja wlasny.
 * Dzieki temu nie ma dwoch nickow jeden nad drugim.
 */
public final class NameHider {

    private NameHider() {}

    public static boolean shouldHide(Entity e) {
        if (!Config.isOn(Module.NAMETAGS)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e == mc.player) return false;
        // nasze nametagi dzialaja tylko w widoku z pierwszej osoby
        if (!mc.options.getCameraType().isFirstPerson()) return false;

        double dist = e.distanceTo(mc.player);
        if (e instanceof Player) {
            return dist <= 80.0;
        }
        if (e instanceof Mob && Config.isOn(Module.MOBS)) {
            return dist <= 40.0;
        }
        return false;
    }
}
