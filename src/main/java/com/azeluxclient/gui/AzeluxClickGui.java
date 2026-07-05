package com.azeluxclient.gui;

import com.azeluxclient.module.Module;
import com.azeluxclient.module.ModuleManager;
import com.azeluxclient.setting.BooleanSetting;
import com.azeluxclient.setting.Setting;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public class AzeluxClickGui extends Screen {

    // ── Textures (from Azelux Client.mcpack) ──────────────────────────────────
    private static final Identifier T_BG          = id("mod_menu/background");
    private static final Identifier T_SIDEBAR      = id("mod_menu/side_bar");
    private static final Identifier T_CARD         = id("mod_menu/mod_background");
    private static final Identifier T_OPT          = id("mod_menu/mod_settings");
    private static final Identifier T_OPT_HOV      = id("mod_menu/mod_settings_hover");
    private static final Identifier T_ENABLED       = id("mod_menu/enabled_mod");
    private static final Identifier T_ENABLED_HOV   = id("mod_menu/enabled_mod_hover");
    private static final Identifier T_DISABLED      = id("mod_menu/disabled_mod");
    private static final Identifier T_DISABLED_HOV  = id("mod_menu/disabled_mod_hover");
    private static final Identifier T_LOGO          = id("logo");
    private static final Identifier T_SLIDER        = id("slider_knob");

    private static Identifier id(String name) {
        return Identifier.of("azeluxclient", "textures/gui/" + name + ".png");
    }

    private static Identifier iconId(String name) {
        return Identifier.of("azeluxclient", "textures/gui/icons/" + name + ".png");
    }

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_GRAY    = 0xFF888888;
    private static final int C_RED     = 0xFFCC1A3F;
    private static final int C_RED_NEW = 0xFFFF2255;

    // ── Tabs ──────────────────────────────────────────────────────────────────
    private static final String[] TABS = {"MODS", "SETTINGS", "EMOTES"};
    private int activeTab = 0;

    // ── Layout (set in init) ──────────────────────────────────────────────────
    private int panX, panY, panW, panH;
    private int sideW, navH;
    private int cardW, cardH;
    private static final int COLS = 3;
    private static final int PAD  = 10;

    // ── State ─────────────────────────────────────────────────────────────────
    private int     scrollY = 0;
    private Module  optModule   = null; // module whose OPTIONS was clicked
    private SliderSetting dragging    = null;
    private int     dragTrackX = 0, dragTrackW = 0;

    public AzeluxClickGui() {
        super(Text.literal("Azelux Client"));
    }

    @Override
    protected void init() {
        panW  = (int)(width  * 0.84f);
        panH  = (int)(height * 0.82f);
        panX  = (width  - panW) / 2;
        panY  = (height - panH) / 2;
        navH  = 36;
        sideW = (int)(panW * 0.198f); // ~217/1072 ratio from source assets

        int mainW = panW - sideW;
        cardW = (mainW - PAD * (COLS + 1)) / COLS;
        cardH = (int)(cardW * (229.0 / 235.0));

        scrollY = 0;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  RENDER
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x99000000);
        tex(ctx, T_BG,      panX, panY, panW, panH, 1072, 658);
        renderNav(ctx, mx, my);
        tex(ctx, T_SIDEBAR, panX, panY + navH, sideW, panH - navH, 217, 610);
        renderSidebarContent(ctx);
        if (optModule != null) {
            renderSettingsView(ctx, mx, my);
        } else {
            renderGrid(ctx, mx, my);
        }
        super.render(ctx, mx, my, delta);
    }

    // ── Top nav bar ───────────────────────────────────────────────────────────
    private void renderNav(DrawContext ctx, int mx, int my) {
        int logoSz = navH - 8;
        int lx = panX + 10, ly = panY + (navH - logoSz) / 2;
        tex(ctx, T_LOGO, lx, ly, logoSz, logoSz, 281, 282);
        ctx.drawText(textRenderer, "AZELUX CLIENT", lx + logoSz + 6, panY + navH / 2 - 4, C_WHITE, false);

        int tabTotalW = 0;
        for (String t : TABS) tabTotalW += textRenderer.getWidth(t) + 28;
        int tabX = panX + (panW - tabTotalW) / 2;
        for (int i = 0; i < TABS.length; i++) {
            int tw  = textRenderer.getWidth(TABS[i]);
            int btnW = tw + 28;
            int tx  = tabX;
            boolean sel = (activeTab == i);
            boolean hov = mx >= tx && mx < tx + btnW && my >= panY && my < panY + navH;
            ctx.drawText(textRenderer, TABS[i], tx + 14, panY + navH / 2 - 4,
                    sel ? C_WHITE : (hov ? 0xFFCCCCCC : C_GRAY), false);
            if (sel) ctx.fill(tx + 6, panY + navH - 2, tx + btnW - 6, panY + navH, C_RED);
            tabX += btnW;
        }
        ctx.fill(panX, panY + navH, panX + panW, panY + navH + 1, 0x55FFFFFF);
    }

    // ── Sidebar content ───────────────────────────────────────────────────────
    private void renderSidebarContent(DrawContext ctx) {
        int sx = panX + 10, sy = panY + navH + 14;
        ctx.fill(panX + 6, sy, panX + sideW - 6, sy + 22, 0x33FFFFFF);
        ctx.drawText(textRenderer, "Default",  sx + 4, sy + 7, C_WHITE, false);
        ctx.drawText(textRenderer, "\u270E", panX + sideW - 20, sy + 7, C_GRAY, false);
    }

    // ── Module grid ───────────────────────────────────────────────────────────
    private void renderGrid(DrawContext ctx, int mx, int my) {
        if (activeTab != 0) {
            ctx.drawText(textRenderer, "Coming soon",
                    panX + sideW + 20, panY + navH + 20, C_GRAY, false);
            return;
        }
        List<Module> mods = ModuleManager.getModules();
        int gridX = panX + sideW;
        int gridY = panY + navH + 1;
        int gridH = panH - navH - 1;
        ctx.enableScissor(gridX, gridY, gridX + panW - sideW, gridY + gridH);

        for (int i = 0; i < mods.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int cx  = gridX + PAD + col * (cardW + PAD);
            int cy  = gridY + PAD + row * (cardH + PAD) - scrollY;
            if (cy + cardH < gridY || cy > gridY + gridH) continue;
            renderCard(ctx, mods.get(i), cx, cy, mx, my);
        }
        ctx.disableScissor();
    }

    private void renderCard(DrawContext ctx, Module mod, int cx, int cy, int mx, int my) {
        // Card background (235x229)
        tex(ctx, T_CARD, cx, cy, cardW, cardH, 235, 229);

        // Icon
        Identifier icon = resolveIcon(mod.getName());
        int iconSz = Math.min(36, cardW / 3);
        int iconX  = cx + (cardW - iconSz) / 2;
        int iconY  = cy + (int)(cardH * 0.14f);
        if (icon != null) tex(ctx, icon, iconX, iconY, iconSz, iconSz, 52, 52);

        // Name
        String name = mod.getName();
        int nameW = textRenderer.getWidth(name);
        ctx.drawText(textRenderer, name,
                cx + (cardW - nameW) / 2,
                cy + (int)(cardH * 0.50f) - 4,
                C_WHITE, false);

        // Button dimensions (230x39 options, 230x42 toggle)
        int btnW  = cardW - 10;
        int optH  = Math.max(12, (int)(cardH * 0.17f));
        int togH  = Math.max(12, (int)(cardH * 0.18f));
        int optX  = cx + 5;
        int optY  = cy + cardH - togH - optH - 8;
        int togY  = optY + optH + 3;

        boolean optHov = mx >= optX && mx < optX + btnW && my >= optY && my < optY + optH;
        boolean togHov = mx >= optX && mx < optX + btnW && my >= togY && my < togY + togH;
        boolean en = mod.isEnabled();

        // OPTIONS button
        tex(ctx, optHov ? T_OPT_HOV : T_OPT, optX, optY, btnW, optH, 230, 39);
        drawCentered(ctx, "OPTIONS", optX, optY, btnW, optH, C_WHITE);

        // ENABLED/DISABLED button
        tex(ctx, en ? (togHov ? T_ENABLED_HOV : T_ENABLED)
                    : (togHov ? T_DISABLED_HOV : T_DISABLED),
                optX, togY, btnW, togH, 230, 42);
        drawCentered(ctx, en ? "ENABLED" : "DISABLED", optX, togY, btnW, togH, C_WHITE);
    }

    // ── Settings view (when OPTIONS clicked) ─────────────────────────────────
    private void renderSettingsView(DrawContext ctx, int mx, int my) {
        int gx = panX + sideW + PAD;
        int gy = panY + navH + PAD;

        // Header
        boolean backHov = mx >= gx && mx < gx + 70 && my >= gy && my < gy + 18;
        ctx.fill(gx, gy, gx + 70, gy + 18, backHov ? 0xFF333355 : 0xFF222244);
        ctx.drawText(textRenderer, "\u2190 Back", gx + 6, gy + 5, C_WHITE, false);
        ctx.drawText(textRenderer, optModule.getName() + " Settings",
                gx + 80, gy + 5, C_WHITE, false);

        int sy = gy + 30;
        int settW = panW - sideW - PAD * 2;

        for (Setting<?> s : optModule.getSettings()) {
            if (s instanceof BooleanSetting bs) {
                ctx.drawText(textRenderer, bs.getName(), gx + 4, sy + 3, C_WHITE, false);
                int swX = gx + settW - 55;
                tex(ctx, bs.getValue() ? T_ENABLED : T_DISABLED, swX, sy - 2, 52, 14, 230, 42);
                sy += 24;
            } else if (s instanceof SliderSetting ss) {
                ctx.drawText(textRenderer,
                        ss.getName() + ": " + String.format("%.1f", ss.getValue()),
                        gx + 4, sy + 3, C_WHITE, false);
                sy += 14;
                int slW = settW - 20;
                float t = (float)((ss.getValue() - ss.getMin()) / (ss.getMax() - ss.getMin()));
                ctx.fill(gx + 4, sy, gx + 4 + slW, sy + 4, 0xFF333355);
                ctx.fill(gx + 4, sy, gx + 4 + (int)(t * slW), sy + 4, C_RED);
                int kx = gx + 4 + (int)(t * slW) - 5;
                tex(ctx, T_SLIDER, kx, sy - 4, 10, 12, 10, 12);
                sy += 24;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  INPUT
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        int mx = (int)click.x(), my = (int)click.y();

        // Tab clicks
        int tabTotalW = 0;
        for (String t : TABS) tabTotalW += textRenderer.getWidth(t) + 28;
        int tabX = panX + (panW - tabTotalW) / 2;
        for (int i = 0; i < TABS.length; i++) {
            int btnW = textRenderer.getWidth(TABS[i]) + 28;
            if (mx >= tabX && mx < tabX + btnW && my >= panY && my < panY + navH) {
                activeTab = i;
                optModule = null;
                return true;
            }
            tabX += btnW;
        }

        // Settings view — back button
        if (optModule != null) {
            int gx = panX + sideW + PAD, gy = panY + navH + PAD;
            if (mx >= gx && mx < gx + 70 && my >= gy && my < gy + 18) {
                optModule = null;
                return true;
            }
            // Slider / boolean clicks inside settings view
            int sy = gy + 30, settW = panW - sideW - PAD * 2;
            for (Setting<?> s : optModule.getSettings()) {
                if (s instanceof BooleanSetting bs) {
                    int swX = gx + settW - 55;
                    if (mx >= swX && mx < swX + 52 && my >= sy - 2 && my < sy + 12) {
                        bs.toggle();
                        return true;
                    }
                    sy += 24;
                } else if (s instanceof SliderSetting ss) {
                    sy += 14;
                    int slW = settW - 20;
                    if (mx >= gx + 4 && mx < gx + 4 + slW && my >= sy - 4 && my < sy + 8) {
                        dragging    = ss;
                        dragTrackX  = gx + 4;
                        dragTrackW  = slW;
                        double t = Math.max(0, Math.min(1, (double)(mx - dragTrackX) / dragTrackW));
                        ss.setValue(ss.getMin() + t * (ss.getMax() - ss.getMin()));
                        return true;
                    }
                    sy += 24;
                }
            }
            return true;
        }

        // Grid clicks (OPTIONS or ENABLED/DISABLED buttons)
        if (activeTab == 0) {
            List<Module> mods = ModuleManager.getModules();
            int gridX = panX + sideW;
            int gridY = panY + navH + 1;
            for (int i = 0; i < mods.size(); i++) {
                Module mod = mods.get(i);
                int col = i % COLS, row = i / COLS;
                int cx  = gridX + PAD + col * (cardW + PAD);
                int cy  = gridY + PAD + row * (cardH + PAD) - scrollY;
                int btnW = cardW - 10;
                int optH = Math.max(12, (int)(cardH * 0.17f));
                int togH = Math.max(12, (int)(cardH * 0.18f));
                int optX = cx + 5;
                int optY = cy + cardH - togH - optH - 8;
                int togY = optY + optH + 3;

                if (mx >= optX && mx < optX + btnW && my >= optY && my < optY + optH) {
                    optModule = mod;
                    return true;
                }
                if (mx >= optX && mx < optX + btnW && my >= togY && my < togY + togH) {
                    mod.toggle();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double dX, double dY) {
        if (dragging != null) {
            double t = Math.max(0, Math.min(1, (click.x() - dragTrackX) / dragTrackW));
            dragging.setValue(dragging.getMin() + t * (dragging.getMax() - dragging.getMin()));
            return true;
        }
        return super.mouseDragged(click, dX, dY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        dragging = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        scrollY = Math.max(0, scrollY - (int)(vy * 14));
        return true;
    }

    @Override public boolean shouldPause()      { return false; }
    @Override public boolean shouldCloseOnEsc() { return true;  }

    // ══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════════
    private void tex(DrawContext ctx, Identifier id, int x, int y, int w, int h, int srcW, int srcH) {
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0.0F, 0.0F, w, h, srcW, srcH);
    }

    private void drawCentered(DrawContext ctx, String text, int bx, int by, int bw, int bh, int color) {
        int tw = textRenderer.getWidth(text);
        ctx.drawText(textRenderer, text, bx + (bw - tw) / 2, by + (bh - 7) / 2, color, false);
    }

    private Identifier resolveIcon(String modName) {
        String key = switch (modName.toLowerCase().replace(" ", "_")) {
            case "armor_status", "armor_hud", "armorstatus", "armorhud" -> "armor_hud";
            case "direction_hud", "directionhud"                        -> "direction_hud";
            case "glint_colorizer", "glintcolorizer"                    -> "glint_colorizer";
            case "fullbright"                                            -> "fullbright";
            case "coordinates"                                           -> "coordinates";
            case "hitbox"                                                -> "hitbox";
            case "chat"                                                  -> "chat";
            case "f8_menu", "f8menu", "f8_screen"                       -> "f8_screen";
            case "server_address", "serveraddress"                      -> "server_address";
            case "shiny_pots", "shinypots"                              -> "shiny_pots";
            case "potion_counter", "potioncounter"                      -> "potion_counter";
            case "mods_list", "modslist"                                -> "mods_list";
            case "custom_crosshair", "customcrosshair"                  -> "custom_crosshair";
            case "hit_color", "hitcolor"                                -> "hit_color";
            case "tab_list", "tablist"                                   -> "tab_list";
            case "inventory_hud", "inventoryhud"                        -> "inventory_hud";
            case "durability_viewer", "durabilityviewer"                -> "durability_viewer";
            case "moving_status", "movingstatus"                        -> "moving_status";
            case "block_outline", "blockoutline"                        -> "block_outline";
            case "chunkmap", "chunk_map"                                -> "chunkmap";
            case "chunk_borders", "chunkborders"                        -> "chunk_borders";
            case "offhand_hud", "offhandhud"                            -> "offhand_hud";
            case "totem_counter", "totemcounter"                        -> "totem_counter";
            case "left_hand", "lefthand"                                -> "left_hand";
            case "framex"                                               -> "framex";
            case "custom_scoreboard", "customscoreboard"                -> "custom_scoreboard";
            default                                                      -> null;
        };
        return key == null ? null : iconId(key + ".png");
    }
}
