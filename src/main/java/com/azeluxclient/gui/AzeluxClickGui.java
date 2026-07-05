package com.azeluxclient.gui;

import com.azeluxclient.module.Module;
import com.azeluxclient.module.ModuleManager;
import com.azeluxclient.setting.BooleanSetting;
import com.azeluxclient.setting.Setting;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.stream.Collectors;

public class AzeluxClickGui extends Screen {

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final int C_BG_BASE    = 0xFF0A0A1A;
    private static final int C_BG_MID     = 0xFF0F0F22;
    private static final int C_BG_PANEL   = 0xFF111128;
    private static final int C_BG_CARD    = 0xFF161636;
    private static final int C_BG_CARD_HV = 0xFF1C1C42;
    private static final int C_BG_CARD_SEL= 0xFF1E1E48;
    private static final int C_BG_SEL_NAV = 0xFF18183A;
    private static final int C_ACCENT     = 0xFFA78BFA;
    private static final int C_ACCENT_DIM = 0xFF7B5EA7;
    private static final int C_TOGGLE_ON  = 0xFF7C3AED;
    private static final int C_TOGGLE_OFF = 0xFF2D2D55;
    private static final int C_BORDER     = 0xFF1E1E3F;
    private static final int C_TXT_PRI    = 0xFFE2E8F0;
    private static final int C_TXT_SEC    = 0xFF94A3B8;
    private static final int C_TXT_DIM    = 0xFF4A5568;
    private static final int C_CHIP_BG    = 0xFF1C1C40;
    private static final int C_GREEN      = 0xFF4ADE80;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int SIDEBAR_W = 152;
    private static final int RIGHT_W   = 248;
    private static final int HEAD_H    = 58;
    private static final int TAB_H     = 42;
    private static final int SEARCH_H  = 36;
    private static final int FOOTER_H  = 32;
    private static final int CARD_H    = 68;
    private static final int CARD_GAP  = 6;
    private static final int NAV_H     = 34;

    // ── Nav ───────────────────────────────────────────────────────────────────
    private static final String[] NAV_LABELS = {
            "Modules", "Configs", "Click GUI", "Friends", "Macros", "HUD", "Accounts", "Settings"
    };
    private static final String[] NAV_ICONS = {
            "\u229E", "\u2263", "\u25A3", "\u265F", "\u2327", "\u229F", "\u2299", "\u2699"
    };
    private static final String[] TAB_ICONS = { "\u2316", "\u21C9", "\u25CE", "\u25A6", "\u2609" };

    // ── Textures (from Azelux mcpack) ─────────────────────────────────────────
    private static final Identifier TEX_LOGO     = Identifier.of("azeluxclient", "textures/gui/logo.png");
    private static final Identifier TEX_CARD_BG  = Identifier.of("azeluxclient", "textures/gui/card_bg.png");
    private static final Identifier TEX_SLIDER   = Identifier.of("azeluxclient", "textures/gui/slider_knob.png");

    // ── State ─────────────────────────────────────────────────────────────────
    private Module.Category activeTab   = Module.Category.COMBAT;
    private String          activeNav   = "Modules";
    private Module          selectedMod = null;
    private int             scrollY     = 0;
    private String          searchQuery = "";

    // Slider drag
    private SliderSetting draggingSlider = null;
    private int           sliderTrackX   = 0;
    private int           sliderTrackW   = 0;

    // Window position / size
    private int wx, wy, ww, wh;
    // Center content area
    private int cx, cw;
    // Right panel
    private int rx;

    public AzeluxClickGui() {
        super(Text.literal("Azelux Client"));
    }

    @Override
    protected void init() {
        ww = Math.min(width  - 30, 960);
        wh = Math.min(height - 30, 580);
        wx = (width  - ww) / 2;
        wy = (height - wh) / 2;
        cx = wx + SIDEBAR_W;
        cw = ww - SIDEBAR_W - RIGHT_W;
        rx = wx + ww - RIGHT_W;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  RENDER
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Dimmed world backdrop
        ctx.fillGradient(0, 0, width, height, 0xBB000015, 0xDD000022);

        // Window background
        ctx.fill(wx, wy, wx + ww, wy + wh, C_BG_BASE);

        renderSidebar(ctx, mx, my);
        renderCenter(ctx, mx, my);
        renderRightPanel(ctx, mx, my);
        renderTopHud(ctx);
        renderBottomBar(ctx);

        super.render(ctx, mx, my, delta);
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────
    private void renderSidebar(DrawContext ctx, int mx, int my) {
        ctx.fill(wx, wy, wx + SIDEBAR_W, wy + wh, C_BG_BASE);
        // right divider
        ctx.fill(wx + SIDEBAR_W - 1, wy, wx + SIDEBAR_W, wy + wh, C_BORDER);

        // Logo
        ctx.fill(wx, wy, wx + SIDEBAR_W - 1, wy + HEAD_H, 0xFF08081A);
        ctx.fill(wx, wy, wx + 3, wy + HEAD_H, C_ACCENT_DIM); // left accent bar
        ctx.drawTexture(net.minecraft.client.render.RenderPipelines.GUI_TEXTURED, TEX_LOGO, wx + 8, wy + 8, 0.0F, 0.0F, 36, 36, 512, 512);
        ctx.drawText(textRenderer, "AzeluxClient", wx + 50, wy + 12, C_ACCENT, false);
        ctx.drawText(textRenderer, "v1.0.0", wx + 52, wy + 24, C_TXT_DIM, false);
        ctx.fill(wx + 10, wy + HEAD_H - 1, wx + SIDEBAR_W - 10, wy + HEAD_H, C_BORDER);

        // Nav items
        int navY = wy + HEAD_H + 6;
        for (int i = 0; i < NAV_LABELS.length; i++) {
            String label = NAV_LABELS[i];
            String icon  = NAV_ICONS[i];
            boolean sel  = label.equals(activeNav);
            boolean hov  = mx >= wx && mx <= wx + SIDEBAR_W - 1 && my >= navY && my <= navY + NAV_H;

            if (sel) {
                ctx.fill(wx, navY, wx + SIDEBAR_W - 1, navY + NAV_H, C_BG_SEL_NAV);
                ctx.fill(wx, navY, wx + 3, navY + NAV_H, C_ACCENT_DIM);
            } else if (hov) {
                ctx.fill(wx, navY, wx + SIDEBAR_W - 1, navY + NAV_H, 0xFF101028);
            }

            ctx.drawText(textRenderer, icon,  wx + 14, navY + 11, sel ? C_ACCENT : C_TXT_DIM,  false);
            ctx.drawText(textRenderer, label, wx + 28, navY + 11, sel ? C_ACCENT : C_TXT_SEC, false);
            navY += NAV_H;
        }

        // User profile strip
        int py = wy + wh - 46;
        ctx.fill(wx, py, wx + SIDEBAR_W - 1, wy + wh, 0xFF070714);
        ctx.fill(wx, py, wx + SIDEBAR_W - 1, py + 1, C_BORDER);
        // avatar block
        ctx.fill(wx + 10, py + 8, wx + 30, py + 30, C_ACCENT_DIM);
        ctx.fill(wx + 13, py + 11, wx + 27, py + 20, 0xFFD4A0A0); // face
        ctx.drawText(textRenderer, "AzeluxUser", wx + 36, py + 8,  C_TXT_PRI, false);
        ctx.drawText(textRenderer, "Premium \u2605", wx + 36, py + 20, C_ACCENT,  false);
    }

    // ── Center ────────────────────────────────────────────────────────────────
    private void renderCenter(DrawContext ctx, int mx, int my) {
        ctx.fill(cx, wy, cx + cw, wy + wh, C_BG_MID);

        // ── Tabs ──────────────────────────────────────────────────────────────
        Module.Category[] cats = Module.Category.values();
        int tw = cw / cats.length;
        for (int i = 0; i < cats.length; i++) {
            Module.Category cat = cats[i];
            boolean active = (cat == activeTab);
            int tx = cx + i * tw;

            ctx.fill(tx, wy, tx + tw, wy + TAB_H, active ? 0xFF0D0D20 : C_BG_MID);

            String icon  = TAB_ICONS[i];
            String label = cat.display;
            int iw = textRenderer.getWidth(icon);
            int lw = textRenderer.getWidth(label);
            int tot = iw + 4 + lw;
            int startX = tx + (tw - tot) / 2;

            ctx.drawText(textRenderer, icon,  startX,          wy + 14, active ? C_ACCENT : C_TXT_DIM, false);
            ctx.drawText(textRenderer, label, startX + iw + 4, wy + 14, active ? C_TXT_PRI : C_TXT_SEC, false);

            if (active) {
                ctx.fill(tx + 8, wy + TAB_H - 2, tx + tw - 8, wy + TAB_H, C_ACCENT_DIM);
            }
        }
        ctx.fill(cx, wy + TAB_H, cx + cw, wy + TAB_H + 1, C_BORDER);

        // ── Module cards ──────────────────────────────────────────────────────
        List<Module> mods = ModuleManager.getByCategory(activeTab);
        if (!searchQuery.isEmpty()) {
            String q = searchQuery.toLowerCase();
            mods = mods.stream()
                    .filter(m -> m.getName().toLowerCase().contains(q))
                    .collect(Collectors.toList());
        }

        int COLS    = 2;
        int areaX   = cx + 8;
        int areaY   = wy + TAB_H + 8;
        int areaH   = wh - TAB_H - SEARCH_H - 8;
        int cardW   = (cw - 24) / COLS;

        for (int i = 0; i < mods.size(); i++) {
            Module mod  = mods.get(i);
            int col     = i % COLS;
            int row     = i / COLS;
            int cardX   = areaX + col * (cardW + CARD_GAP);
            int cardY   = areaY + row * (CARD_H + CARD_GAP) - scrollY;

            if (cardY + CARD_H < areaY || cardY > areaY + areaH) continue;

            boolean hov = mx >= cardX && mx <= cardX + cardW && my >= cardY && my <= cardY + CARD_H;
            boolean sel = (mod == selectedMod);

            int bg = sel ? C_BG_CARD_SEL : (hov ? C_BG_CARD_HV : C_BG_CARD);
            ctx.fill(cardX, cardY, cardX + cardW, cardY + CARD_H, bg);
            ctx.drawTexture(net.minecraft.client.render.RenderPipelines.GUI_TEXTURED, TEX_CARD_BG, cardX, cardY, 0.0F, 0.0F, cardW, CARD_H, cardW, CARD_H);

            // top border on selected
            if (sel) ctx.fill(cardX, cardY, cardX + cardW, cardY + 1, C_ACCENT_DIM);

            // left enable strip
            if (mod.isEnabled()) ctx.fill(cardX, cardY, cardX + 3, cardY + CARD_H, C_ACCENT_DIM);

            // icon box
            String icon = getModuleIcon(mod.getName());
            ctx.fill(cardX + 10, cardY + 12, cardX + 34, cardY + CARD_H - 12, 0xFF1A1A3A);
            ctx.drawText(textRenderer, icon, cardX + 14, cardY + 19, C_ACCENT, false);

            // text
            ctx.drawText(textRenderer, mod.getName(), cardX + 42, cardY + 13, C_TXT_PRI, false);
            String desc = mod.getDescription();
            if (desc.length() > 28) desc = desc.substring(0, 26) + "..";
            ctx.drawText(textRenderer, desc, cardX + 42, cardY + 26, C_TXT_SEC, false);

            // toggle switch
            int tgCX = cardX + cardW - 32;
            int tgCY = cardY + CARD_H / 2;
            drawToggle(ctx, tgCX, tgCY, mod.isEnabled());

            // arrow
            ctx.drawText(textRenderer, ">", cardX + cardW - 10, cardY + 26, C_TXT_DIM, false);
        }

        // ── Search bar ────────────────────────────────────────────────────────
        int sy = wy + wh - SEARCH_H;
        ctx.fill(cx, sy, cx + cw, wy + wh, 0xFF08081A);
        ctx.fill(cx, sy, cx + cw, sy + 1, C_BORDER);
        ctx.fill(cx + 8, sy + 7, cx + cw - 8, sy + SEARCH_H - 7, 0xFF14143A);
        String sText = searchQuery.isEmpty() ? "\u2315  Search modules..." : "\u2315  " + searchQuery + "|";
        ctx.drawText(textRenderer, sText, cx + 16, sy + 13, searchQuery.isEmpty() ? C_TXT_DIM : C_TXT_PRI, false);
    }

    // ── Right panel ───────────────────────────────────────────────────────────
    private void renderRightPanel(DrawContext ctx, int mx, int my) {
        ctx.fill(rx, wy, rx + RIGHT_W, wy + wh, C_BG_PANEL);
        ctx.fill(rx, wy, rx + 1, wy + wh, C_BORDER);

        // ── Module Info header ────────────────────────────────────────────────
        ctx.fill(rx, wy, rx + RIGHT_W, wy + HEAD_H - 12, 0xFF0D0D20);
        ctx.fill(rx, wy + HEAD_H - 13, rx + RIGHT_W, wy + HEAD_H - 12, C_BORDER);
        ctx.drawText(textRenderer, "Module Info", rx + 14, wy + 14, C_ACCENT, false);

        int settingBaseY;
        if (selectedMod != null) {
            // icon
            String mIcon = getModuleIcon(selectedMod.getName());
            ctx.fill(rx + 10, wy + HEAD_H - 8, rx + 32, wy + HEAD_H + 14, 0xFF1A1A3A);
            ctx.drawText(textRenderer, mIcon, rx + 14, wy + HEAD_H - 2, C_ACCENT, false);

            // name + toggle
            ctx.drawText(textRenderer, selectedMod.getName(), rx + 38, wy + HEAD_H - 8, C_TXT_PRI, false);
            drawToggle(ctx, rx + RIGHT_W - 20, wy + HEAD_H + 2, selectedMod.isEnabled());

            // wrapped description
            String[] words = selectedMod.getDescription().split(" ");
            StringBuilder line = new StringBuilder();
            int dY = wy + HEAD_H + 6;
            for (String w : words) {
                String test = line.isEmpty() ? w : line + " " + w;
                if (textRenderer.getWidth(test) > RIGHT_W - 55) {
                    ctx.drawText(textRenderer, line.toString(), rx + 38, dY, C_TXT_SEC, false);
                    dY += 10;
                    line = new StringBuilder(w);
                } else {
                    if (!line.isEmpty()) line.append(" ");
                    line.append(w);
                }
            }
            if (!line.isEmpty()) {
                ctx.drawText(textRenderer, line.toString(), rx + 38, dY, C_TXT_SEC, false);
            }

            // Settings
            settingBaseY = wy + HEAD_H + 34;
            ctx.fill(rx + 10, settingBaseY, rx + RIGHT_W - 10, settingBaseY + 1, C_BORDER);
            settingBaseY += 8;

            int bottomLimit = wy + wh - 140;
            int sY = settingBaseY;
            for (Setting<?> s : selectedMod.getSettings()) {
                if (sY > bottomLimit) break;

                ctx.drawText(textRenderer, s.getName(), rx + 14, sY, C_TXT_SEC, false);

                if (s instanceof SliderSetting slider) {
                    int sTrackX = rx + 14;
                    int sTrackW = RIGHT_W - 52;
                    drawSlider(ctx, sTrackX, sY + 14, sTrackW, slider.getValue(), slider.getMin(), slider.getMax());
                    String val = String.format("%.1f", slider.getValue());
                    ctx.drawText(textRenderer, val, rx + RIGHT_W - 32, sY, C_TXT_PRI, false);
                    sY += 32;
                } else if (s instanceof BooleanSetting bool) {
                    drawToggle(ctx, rx + RIGHT_W - 22, sY + 6, bool.getValue());
                    sY += 24;
                }
            }
        } else {
            ctx.drawText(textRenderer, "Select a module",    rx + 14, wy + HEAD_H + 4, C_TXT_DIM, false);
            ctx.drawText(textRenderer, "to view settings.",  rx + 14, wy + HEAD_H + 16, C_TXT_DIM, false);
        }

        // ── Active Modules ────────────────────────────────────────────────────
        int activeSection = wy + wh - 128;
        ctx.fill(rx + 10, activeSection, rx + RIGHT_W - 10, activeSection + 1, C_BORDER);
        activeSection += 8;
        ctx.drawText(textRenderer, "Active Modules", rx + 14, activeSection, C_ACCENT, false);
        activeSection += 14;

        List<Module> activeList = ModuleManager.getModules().stream()
                .filter(Module::isEnabled).collect(Collectors.toList());

        if (activeList.isEmpty()) {
            ctx.drawText(textRenderer, "None", rx + 14, activeSection, C_TXT_DIM, false);
        } else {
            int chipX = rx + 10;
            int chipY = activeSection;
            for (Module m : activeList) {
                int chipW = textRenderer.getWidth(m.getName()) + 14;
                if (chipX + chipW > rx + RIGHT_W - 10) {
                    chipX = rx + 10;
                    chipY += 17;
                }
                if (chipY > wy + wh - 20) break;
                ctx.fill(chipX, chipY, chipX + chipW, chipY + 13, C_CHIP_BG);
                ctx.fill(chipX, chipY, chipX + chipW, chipY + 1, C_ACCENT_DIM);
                ctx.drawText(textRenderer, m.getName(), chipX + 5, chipY + 3, C_ACCENT, false);
                chipX += chipW + 4;
            }
        }

        // ── Status ────────────────────────────────────────────────────────────
        int statusY = wy + wh - 22;
        ctx.fill(rx + 10, statusY, rx + RIGHT_W - 10, statusY + 1, C_BORDER);
        ctx.fill(rx + 14, statusY + 4, rx + 20, statusY + 10, C_GREEN);
        ctx.drawText(textRenderer, "All systems operational", rx + 24, statusY + 4, C_TXT_SEC, false);
    }

    // ── Top HUD ───────────────────────────────────────────────────────────────
    private void renderTopHud(DrawContext ctx) {
        if (client == null || client.player == null) return;
        double px  = client.player.getX();
        double py  = client.player.getY();
        double pz  = client.player.getZ();
        int    fps = client.getCurrentFps();

        // Coords pill
        String coords = String.format("X: %.1f  Y: %.1f  Z: %.1f", px, py, pz);
        int cpw = textRenderer.getWidth(coords) + 18;
        int chx = cx + cw - cpw - 8 - 52;
        int chy = wy - 22;
        if (chy < 2) chy = wy + TAB_H + 4; // fallback inside window

        ctx.fill(chx, chy, chx + cpw, chy + 16, 0xCC101020);
        ctx.drawText(textRenderer, coords, chx + 9, chy + 4, C_TXT_PRI, false);

        // FPS pill
        String fpsStr = fps + " FPS";
        int fpw = textRenderer.getWidth(fpsStr) + 14;
        int fhx = chx + cpw + 6;
        ctx.fill(fhx, chy, fhx + fpw, chy + 16, 0xCC5B21B6);
        ctx.drawText(textRenderer, fpsStr, fhx + 7, chy + 4, C_TXT_PRI, false);
    }

    // ── Bottom bar ────────────────────────────────────────────────────────────
    private void renderBottomBar(DrawContext ctx) {
        int by = wy + wh;
        if (by + FOOTER_H > height) return;
        ctx.fill(wx, by, wx + ww, by + FOOTER_H, 0xDD07071A);
        ctx.fill(wx, by, wx + ww, by + 1, C_BORDER);

        String[] bLabels = { "\u2302 Home", "\u25D0 Statistics", "\u2338 Binds", "\u2665 Theme" };
        int btw = 110;
        for (int i = 0; i < bLabels.length; i++) {
            int bx2 = wx + 10 + i * btw;
            boolean first = i == 0;
            ctx.drawText(textRenderer, bLabels[i], bx2, by + 10, first ? C_ACCENT : C_TXT_SEC, false);
            if (first) ctx.fill(bx2, by + FOOTER_H - 2, bx2 + btw - 10, by + FOOTER_H, C_ACCENT_DIM);
        }

        // Server + ping
        String server = (client != null && client.getCurrentServerEntry() != null)
                ? "Server: " + client.getCurrentServerEntry().address
                : "Server: Singleplayer";
        int sw = textRenderer.getWidth(server);
        ctx.drawText(textRenderer, server, wx + ww - sw - 60, by + 10, C_TXT_SEC, false);
        ctx.drawText(textRenderer, "\u2580\u2580\u2580 42ms", wx + ww - 50, by + 10, C_GREEN, false);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DRAW HELPERS
    // ══════════════════════════════════════════════════════════════════════════
    private void drawToggle(DrawContext ctx, int cx2, int cy2, boolean on) {
        int pw = 26, ph = 13;
        int px = cx2 - pw / 2, py = cy2 - ph / 2;
        ctx.fill(px, py, px + pw, py + ph, on ? C_TOGGLE_ON : C_TOGGLE_OFF);
        int circX = on ? px + pw - ph + 1 : px + 1;
        ctx.fill(circX, py + 1, circX + ph - 2, py + ph - 1, 0xFFFFFFFF);
    }

    private void drawSlider(DrawContext ctx, int x, int y, int w, double val, double min, double max) {
        float t = (float) ((val - min) / (max - min));
        int filled = (int) (t * w);
        ctx.fill(x, y, x + w, y + 4, 0xFF252550);
        ctx.fill(x, y, x + filled, y + 4, C_ACCENT_DIM);
        int kx = x + filled - 5;
        ctx.drawTexture(net.minecraft.client.render.RenderPipelines.GUI_TEXTURED, TEX_SLIDER, kx, y - 4, 0.0F, 0.0F, 10, 12, 10, 12);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  MODULE ICONS
    // ══════════════════════════════════════════════════════════════════════════
    private String getModuleIcon(String name) {
        return switch (name.toLowerCase()) {
            case "killaura"         -> "\u2316";
            case "velocity"         -> "\u21BA";
            case "reach"            -> "\u2694";
            case "speed"            -> "\u21C9";
            case "sprint"           -> "\u25B7";
            case "nofall"           -> "\u2193";
            case "esp"              -> "\u25CE";
            case "fullbright"       -> "\u2600";
            case "zoom"             -> "\u2299";
            case "antiafk"          -> "\u21BB";
            case "fastplace"        -> "\u229E";
            case "aimassist"        -> "\u2609";
            case "armorhud"         -> "\u26E8";
            case "directionhud"     -> "\u2316";
            case "durabilityviewer" -> "\u2692";
            case "totemcounter"     -> "\u2665";
            case "potioncounter"    -> "\u2697";
            case "serveraddress"    -> "\u2601";
            case "movingstatus"     -> "\u25BA";
            case "customcrosshair"  -> "\u271A";
            case "chunkborders"     -> "\u25A3";
            default                 -> "\u25C6";
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  INPUT
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        double mx = click.x(), my = click.y();

        // ── Sidebar nav ───────────────────────────────────────────────────────
        int navY = wy + HEAD_H + 6;
        for (String label : NAV_LABELS) {
            if (mx >= wx && mx <= wx + SIDEBAR_W - 1 && my >= navY && my <= navY + NAV_H) {
                activeNav = label;
                return true;
            }
            navY += NAV_H;
        }

        // ── Tabs ──────────────────────────────────────────────────────────────
        Module.Category[] cats = Module.Category.values();
        int tw = cw / cats.length;
        for (int i = 0; i < cats.length; i++) {
            int tx = cx + i * tw;
            if (mx >= tx && mx <= tx + tw && my >= wy && my <= wy + TAB_H) {
                activeTab = cats[i];
                scrollY   = 0;
                return true;
            }
        }

        // ── Module cards ──────────────────────────────────────────────────────
        List<Module> mods = ModuleManager.getByCategory(activeTab);
        int COLS  = 2;
        int areaX = cx + 8;
        int areaY = wy + TAB_H + 8;
        int cardW = (cw - 24) / COLS;
        for (int i = 0; i < mods.size(); i++) {
            Module mod  = mods.get(i);
            int cardX   = areaX + (i % COLS) * (cardW + CARD_GAP);
            int cardY   = areaY + (i / COLS) * (CARD_H + CARD_GAP) - scrollY;
            if (mx >= cardX && mx <= cardX + cardW && my >= cardY && my <= cardY + CARD_H) {
                selectedMod = mod;
                // click toggle switch zone
                int tgZoneX = cardX + cardW - 48;
                if (mx >= tgZoneX && mx <= tgZoneX + 36) {
                    mod.toggle();
                }
                return true;
            }
        }

        // ── Right panel: module toggle header ─────────────────────────────────
        if (selectedMod != null) {
            int tgX = rx + RIGHT_W - 34;
            if (mx >= tgX && mx <= tgX + 30 && my >= wy + HEAD_H - 4 && my <= wy + HEAD_H + 14) {
                selectedMod.toggle();
                return true;
            }

            // ── Sliders & booleans ────────────────────────────────────────────
            int sY2  = wy + HEAD_H + 44;
            for (Setting<?> s : selectedMod.getSettings()) {
                if (s instanceof SliderSetting slider) {
                    int sTrackX = rx + 14;
                    int sTrackW = RIGHT_W - 52;
                    if (mx >= sTrackX && mx <= sTrackX + sTrackW && my >= sY2 + 11 && my <= sY2 + 21) {
                        draggingSlider = slider;
                        sliderTrackX   = sTrackX;
                        sliderTrackW   = sTrackW;
                        double t = Math.max(0, Math.min(1, (mx - sTrackX) / sTrackW));
                        slider.setValue(slider.getMin() + t * (slider.getMax() - slider.getMin()));
                        return true;
                    }
                    sY2 += 32;
                } else if (s instanceof BooleanSetting bool) {
                    int tgX2 = rx + RIGHT_W - 35;
                    if (mx >= tgX2 && mx <= tgX2 + 30 && my >= sY2 && my <= sY2 + 14) {
                        bool.toggle();
                        return true;
                    }
                    sY2 += 24;
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double deltaX, double deltaY) {
        if (draggingSlider != null) {
            double mouseX = click.x();
            double t = Math.max(0, Math.min(1, (mouseX - sliderTrackX) / sliderTrackW));
            draggingSlider.setValue(draggingSlider.getMin() + t * (draggingSlider.getMax() - draggingSlider.getMin()));
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        draggingSlider = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollY = Math.max(0, scrollY - (int)(verticalAmount * 14));
        return true;
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        char chr = (char) input.codepoint();
        if (chr == '\b') {
            if (!searchQuery.isEmpty())
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
        } else if (chr >= 32 && chr < 127) {
            searchQuery += chr;
        }
        return true;
    }

    @Override public boolean shouldPause()      { return false; }
    @Override public boolean shouldCloseOnEsc() { return true;  }
}

