package dev.visualutils;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Rysuje tracery, ramki ESP, nametagi, waypointy, radar, kompas oraz panele HUD.
 * Pozycje w swiecie sa przeliczane na ekran, wiec nie potrzeba dodatkowych mixinow.
 */
public final class Overlay {

    private static final int COLOR_PLAYER = 0xFFFF5555;
    private static final int COLOR_MONSTER = 0xFFFFAA00;
    private static final int COLOR_MOB = 0xFF55FF55;
    private static final int COLOR_ITEM = 0xFF55FFFF;
    private static final int LABEL_COLOR = 0xFF9A9AAA;

    private static final double ITEM_DISTANCE = 48.0;
    private static final int MAX_TARGETS = 60;
    private static final int GLOW_LIMIT = 6;       // tyle najblizszych tracerow ma dodatkowa poswiate
    private static final String[] CARDINALS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static int hudWidth = 0;

    private Overlay() {}

    /** Dane kamery gracza potrzebne do rzutowania punktow 3D na ekran. */
    private record Cam(Vec3 eye, Vec3 fwd, Vec3 right, Vec3 up, double scale, double cx, double cy) {}

    /** Cel do narysowania. kind: 0 gracz, 1 potwor, 2 inny mob, 3 przedmiot. */
    private record Target(Entity e, double dist, int kind) {}

    /** Kawalek tekstu w jednym kolorze (linia panelu sklada sie z kilku takich kawalkow). */
    private record Seg(String text, int color) {}

    /** Tlo paneli HUD zalezne od ustawienia "Tlo paneli HUD". */
    private static int panelBg() {
        return switch (Config.setting(Setting.PANEL_OPACITY)) {
            case 0 -> 0x60101018;
            case 2 -> 0xD0101018;
            default -> 0x90101018;
        };
    }

    public static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) return;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        drawHud(g, mc, player, width, height);

        boolean worldModules = Config.isOn(Module.TRACERS) || Config.isOn(Module.ESP)
                || Config.isOn(Module.NAMETAGS) || Config.isOn(Module.ITEMESP)
                || Config.isOn(Module.WAYPOINTS);
        // rzutowanie dziala poprawnie tylko w widoku z pierwszej osoby
        if (!worldModules || !mc.options.getCameraType().isFirstPerson()) return;

        float pt = deltaTracker.getGameTimeDeltaPartialTick(false);

        double yaw = Math.toRadians(player.getViewYRot(pt));
        double pitch = Math.toRadians(player.getViewXRot(pt));
        Vec3 fwd = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        Vec3 up = right.cross(fwd);

        double fov = mc.options.fov().get();
        double scale = (height / 2.0) / Math.tan(Math.toRadians(fov) / 2.0);
        Cam cam = new Cam(player.getEyePosition(pt), fwd, right, up, scale, width / 2.0, height / 2.0);

        double maxDistance = Config.settingInt(Setting.RANGE);
        boolean mobs = Config.isOn(Module.MOBS);
        boolean items = Config.isOn(Module.ITEMESP);

        List<Target> targets = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == player || !e.isAlive()) continue;
            double dist = e.distanceTo(player);
            if (dist > maxDistance) continue;

            int kind;
            if (e instanceof Player) {
                kind = 0;
            } else if (mobs && e instanceof Mob) {
                kind = e instanceof Monster ? 1 : 2;
            } else if (items && e instanceof ItemEntity) {
                if (dist > ITEM_DISTANCE) continue;
                kind = 3;
            } else {
                continue;
            }
            targets.add(new Target(e, dist, kind));
        }

        // najblizsze cele pierwsze - to one dostaja nametagi i tracery
        targets.sort(Comparator.comparingDouble(Target::dist));

        List<int[]> placedTags = new ArrayList<>();
        int[] tagCount = {0};
        int limit = Math.min(targets.size(), MAX_TARGETS);
        for (int i = 0; i < limit; i++) {
            drawTarget(g, mc, cam, targets.get(i), i, pt, placedTags, tagCount, width, height);
        }

        if (Config.isOn(Module.WAYPOINTS)) {
            drawWaypoints(g, mc, cam, width, height);
        }
    }

    // ---------- HUD ----------

    private static void drawHud(GuiGraphics g, Minecraft mc, LocalPlayer player, int width, int height) {
        int accent = Theme.accent();
        int y = 4;
        hudWidth = width;

        if (Config.isOn(Module.LOWHP)) {
            drawLowHp(g, player, width, height);
        }
        if (Config.isOn(Module.COMPASS)) {
            drawCompass(g, mc, player, width, accent);
        }

        if (Config.isOn(Module.INFO)) {
            BlockPos pos = player.blockPosition();
            int blockLight = mc.level.getBrightness(LightLayer.BLOCK, pos);
            int skyLight = mc.level.getBrightness(LightLayer.SKY, pos);
            List<String> lines = new ArrayList<>();
            lines.add(String.format(Locale.ROOT, "XYZ  %.1f / %.1f / %.1f",
                    player.getX(), player.getY(), player.getZ()));
            lines.add("Kierunek  " + directionName(player.getDirection().getSerializedName()));
            lines.add(String.format(Locale.ROOT, "Prędkość  %.1f bl/s", Stats.speed()));
            lines.add("Światło  blok " + blockLight + " | niebo " + skyLight);
            y += drawBox(g, mc, 4, y, lines, accent) + 4;
        }

        List<String> stats = new ArrayList<>();
        if (Config.isOn(Module.STATS)) {
            stats.add("FPS  " + mc.getFps());
            stats.add("Ping  " + pingText(mc, player));
        }
        if (Config.isOn(Module.CPS)) {
            stats.add("CPS  " + Stats.leftCps() + " | " + Stats.rightCps());
        }
        if (Config.isOn(Module.CLOCK)) {
            stats.add("Czas  " + LocalTime.now().format(CLOCK_FORMAT));
        }
        if (!stats.isEmpty()) {
            y += drawBox(g, mc, 4, y, stats, accent) + 4;
        }

        if (Config.isOn(Module.NEARBY) && !Nearby.entries().isEmpty()) {
            List<Nearby.Entry> entries = Nearby.entries();
            List<String> lines = new ArrayList<>();
            lines.add("Gracze  " + entries.size());
            for (int i = 0; i < Math.min(8, entries.size()); i++) {
                Nearby.Entry en = entries.get(i);
                lines.add(en.name() + "  " + (int) en.dist() + "m");
            }
            y += drawBox(g, mc, 4, y, lines, accent) + 4;
        }

        if (Config.isOn(Module.HITINFO) && HitInfo.active()) {
            List<String> hits = new ArrayList<>();
            hits.add("Walka");
            hits.add("Oberwane  " + HitInfo.taken());
            hits.add("Machnięcia  " + HitInfo.swings());
            y += drawBox(g, mc, 4, y, hits, accent) + 4;
        }

        if (Config.isOn(Module.ARMORWARN)) {
            int worst = 100;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack piece = player.getItemBySlot(slot);
                if (!piece.isEmpty() && piece.isDamageableItem() && piece.getMaxDamage() > 0) {
                    int left = 100 - piece.getDamageValue() * 100 / piece.getMaxDamage();
                    worst = Math.min(worst, left);
                }
            }
            if (worst <= 20) {
                String warn = "⚠ Pancerz " + worst + "%";
                int ww = mc.font.width(warn) + 14;
                int wx = width / 2 - ww / 2;
                Draw.roundRect(g, wx, height / 2 + 24, ww, 16, 4, 0xB0B01818);
                g.drawCenteredString(mc.font, warn, width / 2, height / 2 + 28, 0xFFFFFFFF);
            }
        }

        if (Config.isOn(Module.ITEMCOUNT)) {
            drawItemCount(g, mc, player, 4, height - 86 - 78, accent);
        }
        if (Config.isOn(Module.KEYSTROKES)) {
            drawKeystrokes(g, mc, 6, height - 86, accent);
        }
        if (Config.isOn(Module.ARMOR)) {
            drawArmor(g, mc, player, width, height, accent);
        }
        if (Config.isOn(Module.POTIONS)) {
            drawPotions(g, mc, player, width, accent);
        }
        if (Config.isOn(Module.RADAR)) {
            drawRadar(g, mc, player, width, height, accent);
        }
        if (Config.isOn(Module.TARGETHUD)) {
            drawTargetHud(g, mc, width, height, accent);
        }
    }

    /** Pasek kierunkow u gory ekranu: N, NE, E... i znacznik w srodku. */
    private static void drawCompass(GuiGraphics g, Minecraft mc, LocalPlayer player, int width, int accent) {
        // yaw 0 = poludnie, kompasowo 0 stopni = polnoc
        double bearing = (((player.getYRot() % 360.0) + 360.0) + 180.0) % 360.0;
        int w = 180;
        int h = 16;
        int x = width / 2 - w / 2;
        int y = 4;
        double pxPerDeg = 1.5;
        int halfDeg = (int) (w / 2.0 / pxPerDeg);

        Draw.roundRect(g, x, y, w, h, 4, panelBg());

        int start = (int) Math.floor((bearing - halfDeg) / 15.0) * 15;
        for (int a = start; a <= bearing + halfDeg; a += 15) {
            int sx = (int) Math.round(width / 2.0 + (a - bearing) * pxPerDeg);
            if (sx < x + 6 || sx > x + w - 6) continue;
            int norm = ((a % 360) + 360) % 360;
            if (norm % 45 == 0) {
                String label = CARDINALS[norm / 45];
                int lw = mc.font.width(label);
                int color = norm % 90 == 0 ? 0xFFFFFFFF : 0xFFB0B0C0;
                g.drawString(mc.font, label, sx - lw / 2, y + 4, color, false);
            } else {
                g.fill(sx, y + 6, sx + 1, y + 10, 0x70FFFFFF);
            }
        }
        // znacznik kierunku patrzenia
        g.fill(width / 2, y, width / 2 + 1, y + 3, accent);
        g.fill(width / 2, y + h - 3, width / 2 + 1, y + h, accent);

        String deg = (int) bearing + "°";
        g.drawString(mc.font, deg, width / 2 - mc.font.width(deg) / 2, y + h + 2, LABEL_COLOR, true);
    }

    /** Pulsujaca czerwona poswiata na brzegach ekranu, gdy zostaly 3 serduszka lub mniej. */
    private static void drawLowHp(GuiGraphics g, LocalPlayer player, int width, int height) {
        float hp = player.getHealth();
        if (hp <= 0f || hp > 6f) return;
        double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 220.0);
        int base = (int) (50 + 70 * pulse);
        int bands = 12;
        int band = 3;
        for (int i = 0; i < bands; i++) {
            int a = base * (bands - i) / bands;
            int color = (a << 24) | 0x00FF2020;
            int o = i * band;
            g.fill(0, o, width, o + band, color);
            g.fill(0, height - o - band, width, height - o, color);
            g.fill(o, 0, o + band, height, color);
            g.fill(width - o - band, 0, width - o, height, color);
        }
    }

    /** Radar: widok z gory, "do przodu" jest zawsze w gore. */
    private static void drawRadar(GuiGraphics g, Minecraft mc, LocalPlayer player,
                                  int width, int height, int accent) {
        int size = Config.settingInt(Setting.RADAR_SIZE);
        int x = width - size - 4;
        int y = Math.min(height - size - 4, height / 2 + 50);
        int cx = x + size / 2;
        int cy = y + size / 2;
        double range = Config.settingInt(Setting.RADAR_RANGE);

        Draw.roundRect(g, x, y, size, size, 5, panelBg());
        g.fill(x + 4, cy, x + size - 4, cy + 1, 0x30FFFFFF);
        g.fill(cx, y + 4, cx + 1, y + size - 4, 0x30FFFFFF);
        g.fill(x + 6, y, x + size - 6, y + 1, accent);

        double yaw = Math.toRadians(player.getYRot());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double half = size / 2.0 - 5.0;
        double pxPerBlock = half / range;
        boolean mobs = Config.isOn(Module.MOBS);

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == player || !e.isAlive()) continue;
            boolean isPlayer = e instanceof Player;
            if (!isPlayer && !(mobs && e instanceof Mob)) continue;

            double dx = e.getX() - player.getX();
            double dz = e.getZ() - player.getZ();
            double fwdDist = dx * fx + dz * fz;
            double rightDist = dx * rx + dz * rz;
            double len = Math.hypot(fwdDist, rightDist);
            if (len > range) continue;

            int color = isPlayer ? (Friends.isFriend(e) ? Friends.COLOR : COLOR_PLAYER) : (e instanceof Monster ? COLOR_MONSTER : COLOR_MOB);
            int sx = (int) Math.round(cx + rightDist * pxPerBlock);
            int sy = (int) Math.round(cy - fwdDist * pxPerBlock);
            Draw.roundRect(g, sx - 2, sy - 2, 5, 5, 2, color);
        }
        // Ty: srodek radaru
        Draw.roundRect(g, cx - 2, cy - 2, 5, 5, 2, 0xFFFFFFFF);
    }

    /** Panel pod celownikiem z informacja o istocie, na ktora patrzysz. */
    private static void drawTargetHud(GuiGraphics g, Minecraft mc, int width, int height, int accent) {
        Entity e = mc.crosshairPickEntity;
        if (!(e instanceof LivingEntity living) || !living.isAlive() || mc.player == null) return;

        float max = Math.max(1f, living.getMaxHealth());
        float ratio = Math.max(0f, Math.min(1f, living.getHealth() / max));
        int hpColor = Theme.lerp(0xFFFF5555, 0xFF55FF55, ratio);

        List<List<Seg>> lines = new ArrayList<>();
        List<Seg> first = new ArrayList<>();
        first.add(new Seg(living.getName().getString(), 0xFFFFFFFF));
        first.add(new Seg("  ♥" + (int) Math.ceil(living.getHealth()) + "/" + (int) Math.ceil(max), hpColor));
        first.add(new Seg("  " + (int) living.distanceTo(mc.player) + "m", LABEL_COLOR));
        lines.add(first);
        if (living instanceof Player) {
            lines.add(setLine(living));
        }

        int top = height / 2 + 20;
        int panelH = drawSegPanel(g, mc, width / 2, top, lines, accent);

        // pasek zdrowia pod panelem
        int barW = 90;
        int bx = width / 2 - barW / 2;
        int by = top + panelH + 2;
        Draw.roundRect(g, bx, by, barW, 4, 2, 0xA0000000);
        Draw.roundRect(g, bx, by, Math.max(2, Math.round(barW * ratio)), 4, 2, hpColor);
    }

    /** Male ikony z licznikami: totemy, zlote jablka, perly i butelki XP w ekwipunku. */
    private static void drawItemCount(GuiGraphics g, Minecraft mc, LocalPlayer player, int x, int y, int accent) {
        int totems = 0;
        int apples = 0;
        int pearls = 0;
        int xp = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            int c = s.getCount();
            if (s.is(Items.TOTEM_OF_UNDYING)) {
                totems += c;
            } else if (s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                apples += c;
            } else if (s.is(Items.ENDER_PEARL)) {
                pearls += c;
            } else if (s.is(Items.EXPERIENCE_BOTTLE)) {
                xp += c;
            }
        }

        int rowH = 18;
        int boxW = 48;
        int boxH = 4 * rowH + 4;
        Draw.roundRect(g, x, y, boxW, boxH, 4, panelBg());
        g.fill(x, y + 3, x + 2, y + boxH - 3, accent);

        drawCountRow(g, mc, x, y + 2, new ItemStack(Items.TOTEM_OF_UNDYING), totems, true);
        drawCountRow(g, mc, x, y + 2 + rowH, new ItemStack(Items.GOLDEN_APPLE), apples, false);
        drawCountRow(g, mc, x, y + 2 + 2 * rowH, new ItemStack(Items.ENDER_PEARL), pearls, false);
        drawCountRow(g, mc, x, y + 2 + 3 * rowH, new ItemStack(Items.EXPERIENCE_BOTTLE), xp, false);
    }

    private static void drawCountRow(GuiGraphics g, Minecraft mc, int x, int y, ItemStack icon,
                                     int count, boolean warnWhenZero) {
        g.renderItem(icon, x + 4, y + 1);
        int color = count > 0 ? 0xFFFFFFFF : (warnWhenZero ? 0xFFFF5555 : 0xFF6E6E7E);
        g.drawString(mc.font, Integer.toString(count), x + 24, y + 5, color, false);
    }

    private static String directionName(String serialized) {
        return switch (serialized) {
            case "north" -> "Północ (-Z)";
            case "south" -> "Południe (+Z)";
            case "east" -> "Wschód (+X)";
            case "west" -> "Zachód (-X)";
            default -> serialized;
        };
    }

    private static String pingText(Minecraft mc, LocalPlayer player) {
        ClientPacketListener conn = mc.getConnection();
        if (conn == null) return "-";
        PlayerInfo info = conn.getPlayerInfo(player.getUUID());
        if (info == null) return "-";
        return info.getLatency() + " ms";
    }

    /** Panel z liniami "nazwa  wartosc" (nazwa szara, wartosc biala). Zwraca wysokosc panelu. */
    private static int drawBox(GuiGraphics g, Minecraft mc, int x, int y, List<String> lines, int accent) {
        int lineH = 11;
        int w = 0;
        for (String line : lines) {
            int idx = line.indexOf("  ");
            String label = idx >= 0 ? line.substring(0, idx) : line;
            String value = idx >= 0 ? line.substring(idx + 2) : "";
            w = Math.max(w, mc.font.width(label) + 6 + mc.font.width(value));
        }
        int boxW = w + 16;
        int boxH = lines.size() * lineH + 6;
        if (x == 4 && Config.setting(Setting.HUD_SIDE) == 1) {
            x = hudWidth - boxW - 4;
        }
        Draw.roundRect(g, x, y, boxW, boxH, 4, panelBg());
        g.fill(x, y + 3, x + 2, y + boxH - 3, accent);

        int ty = y + 4;
        for (String line : lines) {
            int idx = line.indexOf("  ");
            String label = idx >= 0 ? line.substring(0, idx) : line;
            String value = idx >= 0 ? line.substring(idx + 2) : "";
            g.drawString(mc.font, label, x + 8, ty, LABEL_COLOR, false);
            g.drawString(mc.font, value, x + 8 + mc.font.width(label) + 6, ty, 0xFFFFFFFF, false);
            ty += lineH;
        }
        return boxH;
    }

    private static void drawKeystrokes(GuiGraphics g, Minecraft mc, int x, int y, int accent) {
        int s = 20;
        int gap = 2;
        drawKey(g, mc, x + s + gap, y, s, s, "W", mc.options.keyUp.isDown(), accent);
        int row2 = y + s + gap;
        drawKey(g, mc, x, row2, s, s, "A", mc.options.keyLeft.isDown(), accent);
        drawKey(g, mc, x + s + gap, row2, s, s, "S", mc.options.keyDown.isDown(), accent);
        drawKey(g, mc, x + 2 * (s + gap), row2, s, s, "D", mc.options.keyRight.isDown(), accent);
        int row3 = row2 + s + gap;
        int total = 3 * s + 2 * gap;
        drawKey(g, mc, x, row3, total, 12, "SPACJA", mc.options.keyJump.isDown(), accent);
        int row4 = row3 + 12 + gap;
        int half = (total - gap) / 2;
        drawKey(g, mc, x, row4, half, 16, "LPM", mc.options.keyAttack.isDown(), accent);
        drawKey(g, mc, x + half + gap, row4, half, 16, "PPM", mc.options.keyUse.isDown(), accent);
    }

    private static void drawKey(GuiGraphics g, Minecraft mc, int x, int y, int w, int h,
                                String label, boolean pressed, int accent) {
        Draw.roundRect(g, x, y, w, h, 3, pressed ? Theme.withAlpha(accent, 0xE0) : panelBg());
        g.drawCenteredString(mc.font, label, x + w / 2, y + (h - 8) / 2,
                pressed ? 0xFFFFFFFF : 0xFFC4C4D0);
    }

    private static void drawArmor(GuiGraphics g, Minecraft mc, LocalPlayer player,
                                  int width, int height, int accent) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        List<ItemStack> stacks = new ArrayList<>();
        for (EquipmentSlot slot : slots) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        if (stacks.isEmpty()) return;

        int rowH = 20;
        int boxW = 56;
        int boxH = stacks.size() * rowH + 4;
        int bx = width - boxW - 4;
        int by = height / 2 - 46;
        Draw.roundRect(g, bx, by, boxW, boxH, 4, panelBg());
        g.fill(bx + boxW - 2, by + 3, bx + boxW, by + boxH - 3, accent);

        int y = by + 2;
        for (ItemStack stack : stacks) {
            g.renderItem(stack, bx + boxW - 24, y + 1);
            g.renderItemDecorations(mc.font, stack, bx + boxW - 24, y + 1);
            if (stack.isDamageableItem()) {
                int max = stack.getMaxDamage();
                int left = max - stack.getDamageValue();
                float ratio = max > 0 ? (float) left / max : 1f;
                int color = Theme.lerp(0xFFFF5555, 0xFF55FF55, ratio);
                String text = Integer.toString(left);
                g.drawString(mc.font, text, bx + boxW - 28 - mc.font.width(text), y + 6, color, false);
            }
            y += rowH;
        }
    }

    private static void drawPotions(GuiGraphics g, Minecraft mc, LocalPlayer player, int width, int accent) {
        int y = 4;
        for (MobEffectInstance eff : player.getActiveEffects()) {
            String name = eff.getEffect().value().getDisplayName().getString();
            String level = eff.getAmplifier() > 0 ? " " + (eff.getAmplifier() + 1) : "";
            String time;
            if (eff.isInfiniteDuration()) {
                time = "∞";
            } else {
                int secs = eff.getDuration() / 20;
                time = String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
            }
            String left = name + level;
            int w = mc.font.width(left) + 8 + mc.font.width(time) + 14;
            int x = width - w - 4;
            Draw.roundRect(g, x, y, w, 14, 4, panelBg());
            g.fill(x + w - 2, y + 3, x + w, y + 11, accent);
            g.drawString(mc.font, left, x + 6, y + 3, 0xFFFFFFFF, false);
            g.drawString(mc.font, time, x + 6 + mc.font.width(left) + 8, y + 3, LABEL_COLOR, false);
            y += 16;
        }
    }

    // ---------- set pancerza (materialy, Protection, Unbreaking) ----------

    private static String materialName(String path) {
        if (path.startsWith("netherite")) return "Netherite";
        if (path.startsWith("diamond")) return "Diament";
        if (path.startsWith("iron")) return "Żelazo";
        if (path.startsWith("golden")) return "Złoto";
        if (path.startsWith("chainmail")) return "Kolczuga";
        if (path.startsWith("copper")) return "Miedź";
        if (path.startsWith("leather")) return "Skóra";
        if (path.startsWith("turtle")) return "Żółw";
        return "Inny";
    }

    private static int materialColor(String name) {
        return switch (name) {
            case "Netherite" -> 0xFFB48CFF;
            case "Diament" -> 0xFF55FFFF;
            case "Żelazo" -> 0xFFE0E0E0;
            case "Złoto" -> 0xFFFFD84D;
            case "Kolczuga" -> 0xFFA0A8B8;
            case "Miedź" -> 0xFFE8955A;
            case "Skóra" -> 0xFFC8895A;
            case "Żółw" -> 0xFF55DD77;
            default -> 0xFFCCCCCC;
        };
    }

    private static int levelColor(int level, int good) {
        if (level >= good) return 0xFF55FF55;
        if (level >= good - 1 && level > 0) return 0xFFFFDD55;
        return 0xFFFF6666;
    }

    private static String range(int min, int max) {
        return min == max ? Integer.toString(min) : min + "-" + max;
    }

    /** Linia "Netherite P4 U3" opisujaca pancerz celu (poziomy to najnizsze/najwyzsze na czesciach). */
    private static List<Seg> setLine(LivingEntity living) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        int pieces = 0;
        String material = null;
        int pMin = Integer.MAX_VALUE, pMax = 0;
        int uMin = Integer.MAX_VALUE, uMax = 0;

        for (EquipmentSlot slot : slots) {
            ItemStack stack = living.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            pieces++;

            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (material == null || slot == EquipmentSlot.CHEST) {
                material = materialName(path);
            }

            int protection = 0;
            int unbreaking = 0;
            ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            for (var entry : enchants.entrySet()) {
                if (entry.getKey().is(Enchantments.PROTECTION)) {
                    protection = entry.getIntValue();
                } else if (entry.getKey().is(Enchantments.UNBREAKING)) {
                    unbreaking = entry.getIntValue();
                }
            }
            pMin = Math.min(pMin, protection);
            pMax = Math.max(pMax, protection);
            uMin = Math.min(uMin, unbreaking);
            uMax = Math.max(uMax, unbreaking);
        }

        List<Seg> line = new ArrayList<>();
        if (pieces == 0) {
            line.add(new Seg("Brak pancerza", 0xFF9A9AAA));
            return line;
        }
        line.add(new Seg(material, materialColor(material)));
        line.add(new Seg("  P" + range(pMin, pMax), levelColor(pMin, 4)));
        line.add(new Seg("  U" + range(uMin, uMax), levelColor(uMin, 3)));
        if (pieces < 4) {
            line.add(new Seg("  " + pieces + "/4", 0xFF9A9AAA));
        }
        return line;
    }

    private static int lineWidth(Minecraft mc, List<Seg> line) {
        int w = 0;
        for (Seg s : line) w += mc.font.width(s.text());
        return w;
    }

    /** Panel z wysrodkowanymi, wielokolorowymi liniami. Zwraca jego wysokosc. */
    private static int drawSegPanel(GuiGraphics g, Minecraft mc, int centerX, int top,
                                    List<List<Seg>> lines, int underline) {
        int lineH = 10;
        int textW = 0;
        for (List<Seg> line : lines) textW = Math.max(textW, lineWidth(mc, line));
        int rectW = textW + 8;
        int rectH = lines.size() * lineH + 3;
        int rx = centerX - rectW / 2;

        Draw.roundRect(g, rx, top, rectW, rectH, 3, 0xC0101018);
        g.fill(rx + 2, top + rectH - 1, rx + rectW - 2, top + rectH, Theme.withAlpha(underline, 0xD0));
        int ty = top + 2;
        for (List<Seg> line : lines) {
            int cursor = rx + rectW / 2 - lineWidth(mc, line) / 2;
            for (Seg s : line) {
                g.drawString(mc.font, s.text(), cursor, ty, s.color(), false);
                cursor += mc.font.width(s.text());
            }
            ty += lineH;
        }
        return rectH;
    }

    // ---------- waypointy ----------

    private static void drawWaypoints(GuiGraphics g, Minecraft mc, Cam cam, int width, int height) {
        String dim = Waypoints.dimOf(mc);
        int accent = Theme.accent();
        for (Waypoints.Waypoint wp : Waypoints.all()) {
            if (!wp.dim().equals(dim)) continue;
            double[] s = project(cam, new Vec3(wp.x(), wp.y() + 1.0, wp.z()));
            if (s[2] <= 0.05) continue; // za plecami - nie pokazujemy

            double dx = wp.x() - cam.eye().x;
            double dy = wp.y() - cam.eye().y;
            double dz = wp.z() - cam.eye().z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            // gdy znacznik jest poza ekranem, przyklejamy go do krawedzi
            int sx = (int) clamp(s[0], 14, width - 14);
            int sy = (int) clamp(s[1], 26, height - 14);
            boolean death = Waypoints.DEATH.equals(wp.name());
            int color = death ? 0xFFFF5555 : accent;

            // romb
            for (int i = -3; i <= 3; i++) {
                int half = 3 - Math.abs(i);
                g.fill(sx - half, sy + i, sx + half + 1, sy + i + 1, color);
            }

            List<List<Seg>> lines = new ArrayList<>();
            List<Seg> line = new ArrayList<>();
            line.add(new Seg(wp.name(), 0xFFFFFFFF));
            line.add(new Seg("  " + (int) dist + "m", 0xFF9A9AAA));
            lines.add(line);
            drawSegPanel(g, mc, sx, sy - 18, lines, color);
        }
    }

    // ---------- swiat (tracery, ESP, nametagi) ----------

    private static void drawTarget(GuiGraphics g, Minecraft mc, Cam cam, Target t, int index, float pt,
                                   List<int[]> placedTags, int[] tagCount, int width, int height) {
        Entity e = t.e();
        double dist = t.dist();
        int kind = t.kind();
        int color = switch (kind) {
            case 0 -> Friends.isFriend(e) ? Friends.COLOR : COLOR_PLAYER;
            case 1 -> COLOR_MONSTER;
            case 2 -> COLOR_MOB;
            default -> COLOR_ITEM;
        };
        int accent = Theme.accent();

        Vec3 pos = e.getPosition(pt);
        double halfW = e.getBbWidth() / 2.0;
        double h = e.getBbHeight();

        // ramka: rzutujemy 8 rogow bryly kolizji
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean allFront = true;
        for (int i = 0; i < 8; i++) {
            double px = pos.x + ((i & 1) == 0 ? -halfW : halfW);
            double py = pos.y + ((i & 2) == 0 ? 0.0 : h);
            double pz = pos.z + ((i & 4) == 0 ? -halfW : halfW);
            double[] s = project(cam, new Vec3(px, py, pz));
            if (s[2] <= 0.05) {
                allFront = false;
                break;
            }
            minX = Math.min(minX, s[0]);
            maxX = Math.max(maxX, s[0]);
            minY = Math.min(minY, s[1]);
            maxY = Math.max(maxY, s[1]);
        }
        if (allFront && (maxX - minX > width * 4 || maxY - minY > height * 4)) {
            allFront = false;
        }
        if (allFront) {
            // male cele (np. przedmioty, odlegle moby) dostaja minimalny rozmiar ramki
            if (maxX - minX < 8) {
                double mid = (minX + maxX) / 2.0;
                minX = mid - 4;
                maxX = mid + 4;
            }
            if (maxY - minY < 8) {
                double mid = (minY + maxY) / 2.0;
                minY = mid - 4;
                maxY = mid + 4;
            }
        }

        double[] center = project(cam, pos.add(0.0, h / 2.0, 0.0));
        boolean centerFront = center[2] > 0.05;

        // ----- ESP: rogi albo pelna ramka, wypelnienie i pasek HP (zalezne od ustawien) -----
        boolean boxOn = kind == 3 ? Config.isOn(Module.ITEMESP) : Config.isOn(Module.ESP);
        if (boxOn && allFront) {
            int x0 = (int) minX;
            int y0 = (int) minY;
            int x1 = (int) maxX;
            int y1 = (int) maxY;
            int fill = Config.setting(Setting.ESP_FILL);
            if (fill > 0) {
                g.fill(x0, y0, x1, y1, Theme.withAlpha(color, fill == 1 ? 0x18 : 0x40));
            }
            if (Config.setting(Setting.ESP_STYLE) == 1) {
                drawFrame(g, x0, y0, x1, y1, color);
            } else {
                drawCorners(g, x0, y0, x1, y1, color);
            }
            if (kind != 3 && Config.setting(Setting.HP_BAR) == 0
                    && e instanceof LivingEntity living && living.getMaxHealth() > 0f) {
                float ratio = Math.max(0f, Math.min(1f, living.getHealth() / living.getMaxHealth()));
                int barH = y1 - y0;
                int filled = Math.round(barH * ratio);
                g.fill(x0 - 4, y0, x0 - 2, y1, 0xA0000000);
                g.fill(x0 - 4, y1 - filled, x0 - 2, y1, Theme.lerp(0xFFFF4444, 0xFF55FF55, ratio));
            }
        }

        // ----- tracery: gradient koloru + poswiata, ograniczone do najblizszych -----
        if (kind != 3 && Config.isOn(Module.TRACERS) && index < Config.settingInt(Setting.TRACER_COUNT)) {
            double tx;
            double ty;
            if (centerFront) {
                tx = clamp(center[0], 0, width);
                ty = clamp(center[1], 0, height);
            } else {
                // cel jest za plecami - linia wskazuje kierunek do krawedzi ekranu
                double dx = center[3];
                double dy = -center[4];
                double len = Math.hypot(dx, dy);
                if (len < 1e-4) {
                    dx = 0.0;
                    dy = 1.0;
                    len = 1.0;
                }
                dx /= len;
                dy /= len;
                double kx = dx != 0.0 ? (width / 2.0) / Math.abs(dx) : Double.MAX_VALUE;
                double ky = dy != 0.0 ? (height / 2.0) / Math.abs(dy) : Double.MAX_VALUE;
                double k = Math.min(kx, ky);
                tx = width / 2.0 + dx * k;
                ty = height / 2.0 + dy * k;
            }

            double startY = Config.setting(Setting.TRACER_FROM) == 1 ? height / 2.0 + 6 : height - 1;
            int endColor = Config.setting(Setting.TRACER_COLOR) == 1 ? accent : color;
            // im dalej cel, tym delikatniejsza linia
            int endAlpha = (int) clamp(255 - dist * 1.6, 100, 255);
            int size = Config.setting(Setting.TRACER_WIDTH) == 1 ? 2 : 1;
            if (index >= 10) size += 1;
            drawTracer(g, width / 2.0, startY, tx, ty, accent, endColor, endAlpha, size, index < GLOW_LIMIT);

            // znacznik na koncu linii: ciemny obrys + kolorowa kropka z jasnym srodkiem
            int ex = (int) tx;
            int ey = (int) ty;
            Draw.roundRect(g, ex - 3, ey - 3, 7, 7, 3, 0xB0000000);
            Draw.roundRect(g, ex - 2, ey - 2, 5, 5, 2, Theme.withAlpha(endColor, endAlpha));
            g.fill(ex, ey, ex + 1, ey + 1, 0xFFFFFFFF);
        }

        // ----- nametagi: kompaktowe, tylko najblizsze cele, bez nakladania -----
        boolean wantTag;
        if (kind == 3) {
            wantTag = Config.isOn(Module.ITEMESP) && dist <= 16.0;
        } else {
            wantTag = Config.isOn(Module.NAMETAGS) && tagCount[0] < Config.settingInt(Setting.TAG_COUNT)
                    && dist <= (kind == 0 ? 80.0 : 40.0);
        }
        if (!wantTag) return;

        double tagX;
        int bottom;
        if (allFront) {
            tagX = (minX + maxX) / 2.0;
            bottom = (int) minY - 3;
        } else if (centerFront) {
            tagX = center[0];
            bottom = (int) center[1] - 6;
        } else {
            return;
        }

        // tresc: linia 1 = nick (+ HP, dystans), linia 2 = set pancerza (tylko gracze)
        List<List<Seg>> lines = new ArrayList<>();
        List<Seg> first = new ArrayList<>();
        if (kind == 3 && e instanceof ItemEntity item) {
            ItemStack stack = item.getItem();
            String text = stack.getHoverName().getString() + (stack.getCount() > 1 ? " x" + stack.getCount() : "");
            first.add(new Seg(text, 0xFFFFFFFF));
        } else {
            first.add(new Seg(e.getName().getString(), 0xFFFFFFFF));
            if (dist <= 30.0) {
                if (e instanceof LivingEntity living) {
                    float ratio = living.getMaxHealth() > 0f
                            ? Math.max(0f, Math.min(1f, living.getHealth() / living.getMaxHealth())) : 1f;
                    first.add(new Seg("  ♥" + (int) Math.ceil(living.getHealth()),
                            Theme.lerp(0xFFFF5555, 0xFF55FF55, ratio)));
                }
                first.add(new Seg("  " + (int) dist + "m", 0xFF9A9AAA));
            }
        }
        lines.add(first);
        LivingEntity iconTarget = null;
        int iconLine = -1;
        if (kind == 0 && Config.isOn(Module.SETINFO) && dist <= 40.0 && e instanceof LivingEntity living) {
            lines.add(setLine(living));
            int details = Config.setting(Setting.TAG_DETAILS);
            if (details <= 1) {
                // dwie "puste" linie robia miejsce na rzad ikon (5 x 18 px), ktory rysujemy po panelu
                iconTarget = living;
                iconLine = lines.size();
                List<Seg> blank = new ArrayList<>();
                blank.add(new Seg(" ".repeat(24), 0x00FFFFFF));
                lines.add(blank);
                lines.add(new ArrayList<>());
            }
            if (details == 0) {
                List<Seg> armorEnch = enchSegs("Zbroja: ", ARMOR_ENCH,
                        living.getItemBySlot(EquipmentSlot.HEAD), living.getItemBySlot(EquipmentSlot.CHEST),
                        living.getItemBySlot(EquipmentSlot.LEGS), living.getItemBySlot(EquipmentSlot.FEET));
                if (armorEnch != null) lines.add(armorEnch);
                List<Seg> handEnch = enchSegs("W ręku: ", HAND_ENCH, living.getItemBySlot(EquipmentSlot.MAINHAND));
                if (handEnch != null) lines.add(handEnch);
            }
        }

        int lineH = 10;
        int textW = 0;
        for (List<Seg> line : lines) textW = Math.max(textW, lineWidth(mc, line));
        int rectW = textW + 8;
        int rectH = lines.size() * lineH + 3;
        int rx = (int) tagX - rectW / 2;
        int ry = Math.max(0, bottom - rectH);
        int[] rect = {rx, ry, rectW, rectH};
        if (overlaps(placedTags, rect)) return;
        placedTags.add(rect);
        if (kind != 3) tagCount[0]++;

        drawSegPanel(g, mc, rx + rectW / 2, ry, lines, color);

        if (iconTarget != null) {
            EquipmentSlot[] order = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                    EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
            int ix = rx + rectW / 2 - (order.length * 18 - 2) / 2;
            int iy = ry + 2 + iconLine * lineH + 2;
            for (int i = 0; i < order.length; i++) {
                ItemStack stack = iconTarget.getItemBySlot(order[i]);
                int sx = ix + i * 18;
                Draw.roundRect(g, sx - 1, iy - 1, 18, 18, 3, 0x30FFFFFF);
                if (!stack.isEmpty()) {
                    g.renderItem(stack, sx, iy);
                    g.renderItemDecorations(mc.font, stack, sx, iy);
                }
            }
        }
    }

    private record Ench(ResourceKey<Enchantment> key, String name) {}

    private static final Ench[] ARMOR_ENCH = {
            new Ench(Enchantments.BLAST_PROTECTION, "Wyb"),
            new Ench(Enchantments.PROJECTILE_PROTECTION, "Poc"),
            new Ench(Enchantments.FIRE_PROTECTION, "Og"),
            new Ench(Enchantments.FEATHER_FALLING, "Spad"),
            new Ench(Enchantments.THORNS, "Kolce"),
            new Ench(Enchantments.MENDING, "Napr"),
    };

    private static final Ench[] HAND_ENCH = {
            new Ench(Enchantments.SHARPNESS, "Ostr"),
            new Ench(Enchantments.FIRE_ASPECT, "Ogień"),
            new Ench(Enchantments.KNOCKBACK, "Odrzut"),
            new Ench(Enchantments.LOOTING, "Łupy"),
            new Ench(Enchantments.SWEEPING_EDGE, "Zamach"),
            new Ench(Enchantments.POWER, "Moc"),
            new Ench(Enchantments.UNBREAKING, "U"),
            new Ench(Enchantments.MENDING, "Napr"),
    };

    /** Linia z enchantami z tabeli (najwyzszy poziom na podanych przedmiotach) albo null, gdy nie ma zadnych. */
    private static List<Seg> enchSegs(String label, Ench[] table, ItemStack... stacks) {
        int[] best = new int[table.length];
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            for (var entry : enchants.entrySet()) {
                for (int i = 0; i < table.length; i++) {
                    if (entry.getKey().is(table[i].key())) {
                        best[i] = Math.max(best[i], entry.getIntValue());
                    }
                }
            }
        }
        List<Seg> out = new ArrayList<>();
        out.add(new Seg(label, LABEL_COLOR));
        boolean any = false;
        for (int i = 0; i < table.length; i++) {
            if (best[i] > 0) {
                out.add(new Seg((any ? " " : "") + table[i].name() + best[i], 0xFFD7A8FF));
                any = true;
            }
        }
        return any ? out : null;
    }

    // ---------- pomocnicze ----------

    private static boolean overlaps(List<int[]> placed, int[] r) {
        for (int[] p : placed) {
            if (r[0] < p[0] + p[2] && r[0] + r[2] > p[0] && r[1] < p[1] + p[3] && r[1] + r[3] > p[1]) {
                return true;
            }
        }
        return false;
    }

    /** Zwraca {ekranX, ekranY, glebokosc, prawo, gora}. Glebokosc <= 0 oznacza punkt za plecami. */
    private static double[] project(Cam c, Vec3 p) {
        Vec3 d = p.subtract(c.eye());
        double depth = d.dot(c.fwd());
        double x = d.dot(c.right());
        double y = d.dot(c.up());
        double sx = c.cx();
        double sy = c.cy();
        if (depth > 0.05) {
            sx = c.cx() + x / depth * c.scale();
            sy = c.cy() - y / depth * c.scale();
        }
        return new double[]{sx, sy, depth, x, y};
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Ramka rysowana tylko w rogach (wyglada lzej niz pelny prostokat). */
    private static void drawCorners(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int len = Math.max(3, Math.min((x1 - x0), (y1 - y0)) / 4);
        g.fill(x0, y0, x0 + len, y0 + 1, color);
        g.fill(x1 - len, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x0 + len, y1, color);
        g.fill(x1 - len, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y0 + len, color);
        g.fill(x0, y1 - len, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y0 + len, color);
        g.fill(x1 - 1, y1 - len, x1, y1, color);
    }

    /** Pelna ramka dookola celu. */
    private static void drawFrame(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    /**
     * Tracer: linia z malych kwadratow. Kolor plynnie przechodzi od startu do celu,
     * a przezroczystosc rosnie w strone celu. Opcjonalnie z miekka poswiata dookola.
     */
    private static void drawTracer(GuiGraphics g, double x0, double y0, double x1, double y1,
                                   int colorStart, int colorEnd, int alphaEnd, int size, boolean glow) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        int steps = (int) Math.min(3000.0, Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / size));
        if (steps <= 0) return;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) (x0 + dx * t);
            int y = (int) (y0 + dy * t);
            int col = Theme.lerp(colorStart, colorEnd, (float) t);
            int a = (int) (alphaEnd * (0.15 + 0.85 * t));
            if (glow) {
                g.fill(x - 1, y - 1, x + size + 1, y + size + 1, Theme.withAlpha(col, a / 4));
            }
            g.fill(x, y, x + size, y + size, Theme.withAlpha(col, a));
        }
    }
}
