package dev.visualutils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Menu otwierane klawiszem . - zakladki, karty modulow, plynne przelaczniki,
 * zakladki z ustawieniami, wybor koloru motywu i przycisk "Wylacz wszystko".
 * Klikanie obslugujemy na podstawie pozycji myszy w render(), wiec nie zalezy
 * od zmian w obsludze zdarzen.
 */
public class MenuScreen extends Screen {

    private static final int PW = 380;
    private static final int HEAD = 36;
    private static final int TABS = 26;
    private static final int FOOT = 40;

    private Module.Category tab = Module.Category.SWIAT;
    private final EnumMap<Module, Float> anim = new EnumMap<>(Module.class);
    private boolean rightWasDown = true;
    private boolean rightClick = false;
    private boolean mouseWasDown = true; // zeby klik otwierajacy menu nie przelaczyl modulu
    private long lastNanos = System.nanoTime();

    public MenuScreen() {
        super(Component.literal("NullClient"));
        for (Module m : Module.values()) {
            anim.put(m, Config.isOn(m) ? 1f : 0f);
        }
    }

    private static List<Module> modulesOf(Module.Category c) {
        List<Module> list = new ArrayList<>();
        for (Module m : Module.values()) {
            if (m.category() == c) list.add(m);
        }
        return list;
    }

    private static List<Setting> settingsOf(Module.Category c) {
        List<Setting> list = new ArrayList<>();
        for (Setting s : Setting.values()) {
            if (s.tab() == c) list.add(s);
        }
        return list;
    }

    private static int rowsIn(Module.Category c) {
        return c.isSettings() ? settingsOf(c).size() : modulesOf(c).size();
    }

    private static int maxRows() {
        int max = 1;
        for (Module.Category c : Module.Category.values()) {
            max = Math.max(max, rowsIn(c));
        }
        return max;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;

        // stan lewego przycisku czytamy bezposrednio z GLFW (gra nie podaje go, gdy menu jest otwarte)
        boolean down = GLFW.glfwGetMouseButton(GLFW.glfwGetCurrentContext(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
                == GLFW.GLFW_PRESS;
        if (!down) {
            down = Minecraft.getInstance().mouseHandler.isLeftPressed();
        }
        boolean click = down && !mouseWasDown;
        mouseWasDown = down;
        boolean rdown = GLFW.glfwGetMouseButton(GLFW.glfwGetCurrentContext(), GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                == GLFW.GLFW_PRESS;
        rightClick = rdown && !rightWasDown;
        rightWasDown = rdown;

        int accent = Theme.accent();
        int rows = maxRows();
        int rowH = Math.max(22, Math.min(28, (this.height - 16 - HEAD - TABS - FOOT) / rows));
        int ph = HEAD + TABS + rows * rowH + FOOT;
        int px = (this.width - PW) / 2;
        int py = (this.height - ph) / 2;

        // tlo panelu z delikatna poswiata w kolorze motywu
        // miekki cien i poswiata w kolorze motywu
        Draw.roundRect(g, px - 3, py - 1, PW + 6, ph + 6, 9, 0x40000000);
        Draw.roundRect(g, px - 1, py - 1, PW + 2, ph + 2, 7, Theme.withAlpha(accent, 0x70));
        int bgAlpha = switch (Config.setting(Setting.MENU_OPACITY)) {
            case 1 -> 0xC8;
            case 2 -> 0x96;
            default -> 0xF2;
        };
        Draw.roundRect(g, px, py, PW, ph, 6, (bgAlpha << 24) | 0x121218);

        // gradient pod naglowkiem (poswiata motywu schodzi w dol)
        for (int i = 0; i < 22; i++) {
            int a = Math.max(0, 0x38 - i * 0x38 / 22);
            g.fill(px + 6, py + 1 + i, px + PW - 6, py + 2 + i, Theme.withAlpha(accent, a));
        }

        // naglowek: "Null" biale, "Client" w kolorze motywu
        Component namePart1 = Component.literal("Null").withStyle(ChatFormatting.BOLD);
        Component namePart2 = Component.literal("Client").withStyle(ChatFormatting.BOLD);
        g.drawString(this.font, namePart1, px + 14, py + 11, 0xFFFFFFFF, false);
        g.drawString(this.font, namePart2, px + 14 + this.font.width(namePart1), py + 11, accent, false);
        String menuHint = "Menu: [" + VisualUtilsClient.menuKeyName() + "]";
        g.drawString(this.font, menuHint, px + PW - 14 - this.font.width(menuHint), py + 11, 0xFF8A8A9A, false);
        int enabled = 0;
        for (Module m : Module.values()) {
            if (Config.isOn(m)) enabled++;
        }
        String countText = "Włączone: " + enabled + "/" + Module.values().length;
        g.drawCenteredString(this.font, countText, px + PW / 2 + 10, py + 11, Theme.withAlpha(accent, 0xFF));
        g.fill(px + 12, py + HEAD - 8, px + PW - 12, py + HEAD - 7, Theme.withAlpha(accent, 0x90));

        // zakladki
        Module.Category[] cats = Module.Category.values();
        int gap = 5;
        int tabW = (PW - 24 - gap * (cats.length - 1)) / cats.length;
        int tabY = py + HEAD;
        for (int i = 0; i < cats.length; i++) {
            int tx = px + 12 + i * (tabW + gap);
            boolean active = cats[i] == tab;
            boolean hover = Draw.inside(mouseX, mouseY, tx, tabY, tabW, 18);
            int bg = active ? accent : (hover ? 0x45FFFFFF : 0x28FFFFFF);
            Draw.roundRect(g, tx, tabY, tabW, 18, 4, bg);
            if (active) {
                g.fill(tx + 4, tabY + 17, tx + tabW - 4, tabY + 18, 0xFFFFFFFF);
            }
            String tabLabel = cats[i].label();
            if (!cats[i].isSettings()) {
                int on = 0;
                for (Module m : modulesOf(cats[i])) {
                    if (Config.isOn(m)) on++;
                }
                if (on > 0) tabLabel += " " + on;
            }
            g.drawCenteredString(this.font, tabLabel, tx + tabW / 2, tabY + 5,
                    active ? 0xFFFFFFFF : 0xFFC8C8D4);
            if (click && hover) {
                tab = cats[i];
            }
        }

        int ry = py + HEAD + TABS;
        if (tab.isSettings()) {
            drawSettings(g, mouseX, mouseY, click, px, ry, rowH, accent);
        } else {
            drawModules(g, mouseX, mouseY, click, dt, px, ry, rowH, accent);
        }

        // stopka: motyw, przycisk "Wylacz wszystko" i podpowiedz
        int fy = ry + rows * rowH + 8;
        g.drawString(this.font, "Motyw", px + 14, fy + 3, 0xFF8A8A9A, false);
        for (int i = 0; i < Theme.ACCENTS.length; i++) {
            int sx = px + 58 + i * 20;
            boolean selected = i == Math.floorMod(Config.getTheme(), Theme.ACCENTS.length);
            boolean hover = Draw.inside(mouseX, mouseY, sx - 2, fy - 2, 16, 16);
            if (selected) {
                Draw.roundRect(g, sx - 2, fy - 2, 16, 16, 5, 0xFFFFFFFF);
            } else if (hover) {
                Draw.roundRect(g, sx - 2, fy - 2, 16, 16, 5, 0x80FFFFFF);
            }
            Draw.roundRect(g, sx, fy, 12, 12, 4, Theme.swatch(i));
            if (click && hover) {
                Config.setTheme(i);
                Config.save();
            }
        }

        String offLabel = "Wyłącz wszystko";
        int offW = this.font.width(offLabel) + 14;
        int offX = px + PW - 12 - offW;
        int offY = fy - 2;
        boolean offHover = Draw.inside(mouseX, mouseY, offX, offY, offW, 16);
        Draw.roundRect(g, offX, offY, offW, 16, 4, offHover ? 0x80FF5555 : 0x45FF5555);
        g.drawCenteredString(this.font, offLabel, offX + offW / 2, offY + 4, 0xFFFFFFFF);
        if (click && offHover) {
            for (Module m : Module.values()) {
                if (Config.isOn(m)) Config.toggle(m);
            }
            Config.save();
        }

        String resetLabel = "Reset opcji";
        int resetW = this.font.width(resetLabel) + 14;
        int resetX = offX - 4 - resetW;
        boolean resetHover = Draw.inside(mouseX, mouseY, resetX, offY, resetW, 16);
        Draw.roundRect(g, resetX, offY, resetW, 16, 4, resetHover ? 0x80FFFFFF : 0x30FFFFFF);
        g.drawCenteredString(this.font, resetLabel, resetX + resetW / 2, offY + 4, 0xFFFFFFFF);
        if (click && resetHover) {
            Config.resetSettings();
            Config.save();
        }

        String hint = tab == Module.Category.PVP
                ? "Friend: celownik na gracza + [" + VisualUtilsClient.friendKeyName() + "]   Znajomych: " + Friends.names().size()
                : "Klawisze modułów: Opcje > Sterowanie > NullClient";
        g.drawString(this.font, hint, px + 14, fy + 20, 0xFF6E6E7E, false);
    }

    private void drawModules(GuiGraphics g, int mouseX, int mouseY, boolean click, float dt,
                             int px, int ry, int rowH, int accent) {
        List<Module> list = modulesOf(tab);
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            int y = ry + i * rowH;
            int rx = px + 8;
            int rw = PW - 16;
            int rh = rowH - 2;
            boolean hover = Draw.inside(mouseX, mouseY, rx, y, rw, rh);

            Draw.roundRect(g, rx, y, rw, rh, 4, hover ? 0x32FFFFFF : 0x16FFFFFF);

            boolean on = Config.isOn(m);
            float a = anim.get(m);
            a += ((on ? 1f : 0f) - a) * Math.min(1f, dt * 16f);
            anim.put(m, a);

            // pasek akcentu po lewej, gdy modul wlaczony
            if (on) {
                g.fill(rx, y + 3, rx + 2, y + rh - 3, accent);
            }

            int textY = y + (rh - 17) / 2;
            g.drawString(this.font, m.label(), rx + 10, textY, on ? 0xFFFFFFFF : 0xFFD0D0DC, false);
            g.drawString(this.font, m.description(), rx + 10, textY + 10, 0xFF808090, false);

            // przelacznik
            int swW = 30;
            int swH = 14;
            int swX = rx + rw - 10 - swW;
            int swY = y + (rh - swH) / 2;
            Draw.roundRect(g, swX, swY, swW, swH, 7, Theme.lerp(0xFF3A3A4A, accent, a));
            Draw.roundRect(g, swX + 2 + Math.round(a * 16f), swY + 2, 10, 10, 5, 0xFFFFFFFF);

            // przypisany klawisz
            String key = VisualUtilsClient.keyHint(m);
            if (key != null) {
                String label = "[" + key + "]";
                g.drawString(this.font, label, swX - 8 - this.font.width(label),
                        y + (rh - 8) / 2, Theme.withAlpha(accent, 0xFF), false);
            }

            if (click && hover) {
                Config.toggle(m);
                Config.save();
            }
        }
    }

    private void drawSettings(GuiGraphics g, int mouseX, int mouseY, boolean click,
                              int px, int ry, int rowH, int accent) {
        List<Setting> settings = settingsOf(tab);
        for (int i = 0; i < settings.size(); i++) {
            Setting s = settings.get(i);
            int y = ry + i * rowH;
            int rx = px + 8;
            int rw = PW - 16;
            int rh = rowH - 2;
            boolean hover = Draw.inside(mouseX, mouseY, rx, y, rw, rh);

            Draw.roundRect(g, rx, y, rw, rh, 4, hover ? 0x32FFFFFF : 0x16FFFFFF);
            g.fill(rx, y + 3, rx + 2, y + rh - 3, Theme.withAlpha(accent, 0xB0));

            int textY = y + (rh - 17) / 2;
            g.drawString(this.font, s.label(), rx + 10, textY, 0xFFFFFFFF, false);
            g.drawString(this.font, s.description() + "  (PPM = wstecz)", rx + 10, textY + 10, 0xFF808090, false);

            // pole z aktualna wartoscia - klikniecie przelacza na nastepna
            String value = Config.settingText(s);
            int pillW = this.font.width(value) + 18;
            int pillH = 14;
            int pillX = rx + rw - 10 - pillW;
            int pillY = y + (rh - pillH) / 2;
            boolean pillHover = Draw.inside(mouseX, mouseY, pillX, pillY, pillW, pillH);
            Draw.roundRect(g, pillX, pillY, pillW, pillH, 7,
                    Theme.withAlpha(accent, pillHover ? 0xA0 : 0x70));
            g.drawCenteredString(this.font, value, pillX + pillW / 2, pillY + 3, 0xFFFFFFFF);

            if (hover && (click || rightClick)) {
                if (rightClick) Config.cyclePrev(s);
                else Config.cycle(s);
                Config.save();
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Config.save();
        super.onClose();
    }
}
