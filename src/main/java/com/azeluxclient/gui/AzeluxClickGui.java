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

    // ── Textures ──────────────────────────────────────────────────────────────
    private static final Identifier T_CARD        = tex("mod_menu/mod_background");
    private static final Identifier T_OPT         = tex("mod_menu/mod_settings");
    private static final Identifier T_OPT_HOV     = tex("mod_menu/mod_settings_hover");
    private static final Identifier T_ENABLED      = tex("mod_menu/enabled_mod");
    private static final Identifier T_ENABLED_HOV  = tex("mod_menu/enabled_mod_hover");
    private static final Identifier T_DISABLED     = tex("mod_menu/disabled_mod");
    private static final Identifier T_DISABLED_HOV = tex("mod_menu/disabled_mod_hover");
    private static final Identifier T_LOGO         = tex("logo");
    private static final Identifier T_SLIDER       = tex("slider_knob");

    private static Identifier tex(String name) {
        return Identifier.of("azeluxclient", "textures/gui/" + name + ".png");
    }
    private static Identifier iconTex(String key) {
        return Identifier.of("azeluxclient", "textures/gui/icons/" + key + ".png");
    }

    // ── Colors (more transparent than before) ─────────────────────────────────
    private static final int C_WHITE     = 0xFFFFFFFF;
    private static final int C_GRAY      = 0xFFAAAAAA;
    private static final int C_DIM       = 0xFF777777;
    private static final int C_RED       = 0xFFCC1A3F;
    private static final int C_PANEL     = 0x880A0A18; // 53% opaque — was 0xCC
    private static final int C_SIDEBAR   = 0x33000000;
    private static final int C_NAV       = 0x660D0D1E; // 40% opaque — was 0xBB
    private static final int C_CARD_TINT = 0x22FFFFFF;
    private static final int C_ACCENT    = 0xFFC084FC; // lavender accent

    // ── Tabs ──────────────────────────────────────────────────────────────────
    private static final String[] TABS = { "MODS", "SETTINGS", "EMOTES" };
    private int activeTab = 0;

    // ── Layout ────────────────────────────────────────────────────────────────
    private int panX, panY, panW, panH;
    private int sideW, navH;
    private int cardW, cardH;
    private static final int COLS = 3;
    private static final int PAD  = 8;
    private static final int R    = 6; // corner radius

    // ── State ─────────────────────────────────────────────────────────────────
    private int          scrollY    = 0;
    private Module       optModule  = null;
    private SliderSetting dragging   = null;
    private int          dragTrackX = 0, dragTrackW = 0;
    private double       lastDragY  = 0;
    private boolean      dragScroll = false;

    public AzeluxClickGui() {
        super(Text.literal("Azelux Client"));
    }

    @Override
    protected void init() {
        panW  = (int)(width  * 0.86f);
        panH  = (int)(height * 0.83f);
        panX  = (width  - panW) / 2;
        panY  = (height - panH) / 2;
        navH  = 32;
        sideW = (int)(panW * 0.195f);
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
        // Dim the game world behind the panel (lighter than before)
        ctx.fill(0, 0, width, height, 0x55000000);

        // Panel background — rounded corners
        fillRounded(ctx, panX, panY, panX + panW, panY + panH, C_PANEL, R);

        // Nav bar — round only top corners
        fillRoundedTop(ctx, panX, panY, panX + panW, panY + navH, C_NAV, R);

        // Sidebar tint (rectangular, safely inside panel)
        ctx.fill(panX + 1, panY + navH, panX + sideW, panY + panH - 1, C_SIDEBAR);

        // Separator lines (inset so they don't overdraw the rounded corners)
        ctx.fill(panX + R, panY + navH, panX + panW - R, panY + navH + 1, 0x44FFFFFF);
        ctx.fill(panX + sideW, panY + navH + 4, panX + sideW + 1, panY + panH - 4, 0x33FFFFFF);

        renderNav(ctx, mx, my);
        renderSidebar(ctx);
        if (optModule != null) renderSettings(ctx, mx, my);
        else renderGrid(ctx, mx, my);

        super.render(ctx, mx, my, delta);
    }

    // ── Nav ───────────────────────────────────────────────────────────────────
    private void renderNav(DrawContext ctx, int mx, int my) {
        int logoSz = navH - 6;
        int lx = panX + 10, ly = panY + (navH - logoSz) / 2;
        texScaled(ctx, T_LOGO, lx, ly, logoSz, logoSz, 281, 282);
        ctx.drawText(textRenderer, "AZELUX CLIENT",
                lx + logoSz + 6, panY + navH / 2 - 4, C_WHITE, false);

        int[] tabWidths = new int[TABS.length];
        int totalW = 0;
        for (int i = 0; i < TABS.length; i++) {
            tabWidths[i] = textRenderer.getWidth(TABS[i]) + 24;
            totalW += tabWidths[i];
        }
        int tabX = panX + (panW - totalW) / 2;
        for (int i = 0; i < TABS.length; i++) {
            int tw = tabWidths[i];
            boolean sel = (activeTab == i);
            boolean hov = mx >= tabX && mx < tabX + tw && my >= panY && my < panY + navH;
            ctx.drawText(textRenderer, TABS[i],
                    tabX + (tw - textRenderer.getWidth(TABS[i])) / 2,
                    panY + navH / 2 - 4,
                    sel ? C_WHITE : (hov ? 0xFFCCCCCC : C_DIM), false);
            if (sel) {
                ctx.fill(tabX + 4, panY + navH - 2, tabX + tw - 4, panY + navH, C_RED);
            }
            tabX += tw;
        }
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────
    private void renderSidebar(DrawContext ctx) {
        int sx = panX + 8, sy = panY + navH + 12;
        ctx.fill(sx, sy, panX + sideW - 8, sy + 22, 0x33FFFFFF);
        ctx.fill(sx, sy, panX + sideW - 8, sy + 1, 0x55FFFFFF);
        ctx.drawText(textRenderer, "Default", sx + 8, sy + 7, C_WHITE, false);
        ctx.drawText(textRenderer, "✎", panX + sideW - 20, sy + 7, C_GRAY, false);
    }

    // ── Module grid ───────────────────────────────────────────────────────────
    private void renderGrid(DrawContext ctx, int mx, int my) {
        if (activeTab == 1) { renderSettingsTab(ctx); return; }
        if (activeTab != 0) {
            ctx.drawText(textRenderer, "Coming soon",
                    panX + sideW + 20, panY + navH + 20, C_GRAY, false);
            return;
        }
        List<Module> mods = ModuleManager.getModules();
        int gx = panX + sideW + 1, gy = panY + navH + 1;
        int gw = panW - sideW - 1, gh = panH - navH - 1;
        ctx.enableScissor(gx, gy, gx + gw, gy + gh);
        for (int i = 0; i < mods.size(); i++) {
            int col = i % COLS, row = i / COLS;
            int cx  = gx + PAD + col * (cardW + PAD);
            int cy  = gy + PAD + row * (cardH + PAD) - scrollY;
            if (cy + cardH < gy || cy > gy + gh) continue;
            renderCard(ctx, mods.get(i), cx, cy, mx, my);
        }
        ctx.disableScissor();
    }

    private void renderCard(DrawContext ctx, Module mod, int cx, int cy, int mx, int my) {
        boolean hov = mx >= cx && mx < cx + cardW && my >= cy && my < cy + cardH;
        texScaled(ctx, T_CARD, cx, cy, cardW, cardH, 235, 229);
        if (hov) ctx.fill(cx, cy, cx + cardW, cy + cardH, C_CARD_TINT);

        Identifier icon = resolveIcon(mod.getName());
        int iconSz = (int)(cardW * 0.28f);
        int iconX  = cx + (cardW - iconSz) / 2;
        int iconY  = cy + (int)(cardH * 0.12f);
        if (icon != null) texScaled(ctx, icon, iconX, iconY, iconSz, iconSz, 52, 52);

        String name = mod.getName();
        ctx.drawText(textRenderer, name,
                cx + (cardW - textRenderer.getWidth(name)) / 2,
                cy + (int)(cardH * 0.52f) - 4, C_WHITE, false);

        int optH = Math.max(13, (int)(cardH * 0.165f));
        int togH = Math.max(13, (int)(cardH * 0.175f));
        int btnW = cardW - 10;
        int optX = cx + 5;
        int optY = cy + cardH - togH - optH - 6;
        int togY = optY + optH + 2;

        boolean optHov = mx >= optX && mx < optX + btnW && my >= optY && my < optY + optH;
        boolean togHov = mx >= optX && mx < optX + btnW && my >= togY && my < togY + togH;
        boolean en = mod.isEnabled();

        texScaled(ctx, optHov ? T_OPT_HOV : T_OPT, optX, optY, btnW, optH, 230, 39);
        drawCentered(ctx, "OPTIONS", optX, optY, btnW, optH, C_WHITE);

        texScaled(ctx,
                en ? (togHov ? T_ENABLED_HOV : T_ENABLED)
                   : (togHov ? T_DISABLED_HOV : T_DISABLED),
                optX, togY, btnW, togH, 230, 42);
        drawCentered(ctx, en ? "ENABLED" : "DISABLED", optX, togY, btnW, togH, C_WHITE);
    }

    // ── Settings tab (General settings) ───────────────────────────────────────
    private void renderSettingsTab(DrawContext ctx) {
        int gx = panX + sideW + PAD + 1, gy = panY + navH + PAD;
        ctx.drawText(textRenderer, "GENERAL", gx, gy, C_WHITE, false);
        ctx.fill(gx, gy + 12, gx + 200, gy + 13, 0x44FFFFFF);
        ctx.drawText(textRenderer, "Hide Azelux Loading Screen", gx, gy + 20, C_WHITE, false);
        ctx.drawText(textRenderer, "Show Nametag in Third Person", gx, gy + 36, C_WHITE, false);
        ctx.drawText(textRenderer, "Made by Azelux Team  <3", gx, gy + 60, C_GRAY, false);
    }

    // ── Module settings view ──────────────────────────────────────────────────
    private void renderSettings(DrawContext ctx, int mx, int my) {
        int gx    = panX + sideW + PAD + 1, gy = panY + navH + PAD;
        int settW = panW - sideW - PAD * 2 - 1;

        // Header
        boolean bHov = mx >= gx && mx < gx + 72 && my >= gy && my < gy + 18;
        ctx.fill(gx, gy, gx + 72, gy + 18, bHov ? 0xFF2A2A4A : 0xFF1A1A32);
        ctx.fill(gx, gy, gx + 72, gy + 1, 0x44FFFFFF);
        ctx.drawText(textRenderer, "←  Back", gx + 8, gy + 5, C_WHITE, false);

        // Module name + accent bar
        String title = optModule.getName().toUpperCase();
        ctx.drawText(textRenderer, title, gx + 82, gy + 5, C_WHITE, false);
        int tw = textRenderer.getWidth(title);
        ctx.fill(gx + 82, gy + 16, gx + 82 + tw, gy + 17, C_RED);

        // Description
        ctx.drawText(textRenderer, optModule.getDescription(), gx + 82, gy + 20, C_DIM, false);
        ctx.fill(gx, gy + 30, gx + settW, gy + 31, 0x33FFFFFF);

        int sy = gy + 40;
        for (Setting<?> s : optModule.getSettings()) {
            if (sy > panY + panH - 20) break;
            if (s instanceof BooleanSetting bs) {
                ctx.drawText(textRenderer, bs.getName(), gx + 4, sy + 3, C_WHITE, false);
                int swX = gx + settW - 56;
                // Toggle pill
                int togW = 52, togH = 14;
                ctx.fill(swX, sy - 2, swX + togW, sy + togH, bs.getValue() ? 0xFF2D6A4F : 0xFF4A1020);
                ctx.fill(swX, sy - 2, swX + togW, sy - 1, 0x44FFFFFF);
                drawCentered(ctx, bs.getValue() ? "ON" : "OFF",
                        swX, sy - 2, togW, togH + 2,
                        bs.getValue() ? 0xFF4ADE80 : 0xFFFF6B6B);
                sy += 26;
            } else if (s instanceof SliderSetting ss) {
                // Label + value
                String label = ss.getName() + "  " + String.format("%.1f", ss.getValue());
                ctx.drawText(textRenderer, label, gx + 4, sy + 3, C_WHITE, false);
                sy += 18;
                int slW = settW - 16;
                float t = (float)((ss.getValue() - ss.getMin()) / (ss.getMax() - ss.getMin()));
                // Track background
                ctx.fill(gx + 4, sy + 1, gx + 4 + slW, sy + 5, 0x55FFFFFF);
                // Fill
                ctx.fill(gx + 4, sy + 1, gx + 4 + (int)(t * slW), sy + 5, C_RED);
                // Knob
                texScaled(ctx, T_SLIDER, gx + 4 + (int)(t * slW) - 5, sy - 3, 10, 12, 10, 12);
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

        // Tabs
        int[] tabWidths = new int[TABS.length];
        int totalW = 0;
        for (int i = 0; i < TABS.length; i++) {
            tabWidths[i] = textRenderer.getWidth(TABS[i]) + 24;
            totalW += tabWidths[i];
        }
        int tabX = panX + (panW - totalW) / 2;
        for (int i = 0; i < TABS.length; i++) {
            if (mx >= tabX && mx < tabX + tabWidths[i] && my >= panY && my < panY + navH) {
                activeTab = i; optModule = null; return true;
            }
            tabX += tabWidths[i];
        }

        // Settings view
        if (optModule != null) {
            int gx = panX + sideW + PAD + 1, gy = panY + navH + PAD;
            int settW = panW - sideW - PAD * 2 - 1;
            if (mx >= gx && mx < gx + 72 && my >= gy && my < gy + 18) {
                optModule = null; return true;
            }
            int sy = gy + 40;
            for (Setting<?> s : optModule.getSettings()) {
                if (s instanceof BooleanSetting bs) {
                    int swX = gx + settW - 56;
                    if (mx >= swX && mx < swX + 52 && my >= sy - 2 && my < sy + 12) {
                        bs.toggle(); return true;
                    }
                    sy += 26;
                } else if (s instanceof SliderSetting ss) {
                    sy += 18;
                    int slW = settW - 16;
                    if (mx >= gx + 4 && mx < gx + 4 + slW && my >= sy - 4 && my < sy + 8) {
                        dragging = ss; dragTrackX = gx + 4; dragTrackW = slW;
                        double t2 = Math.max(0, Math.min(1, (double)(mx - dragTrackX) / dragTrackW));
                        ss.setValue(ss.getMin() + t2 * (ss.getMax() - ss.getMin()));
                        return true;
                    }
                    sy += 24;
                }
            }
            return true;
        }

        // Grid
        if (activeTab == 0) {
            List<Module> mods = ModuleManager.getModules();
            int gx = panX + sideW + 1, gy = panY + navH + 1;
            for (int i = 0; i < mods.size(); i++) {
                Module mod = mods.get(i);
                int col = i % COLS, row = i / COLS;
                int cx  = gx + PAD + col * (cardW + PAD);
                int cy  = gy + PAD + row * (cardH + PAD) - scrollY;
                int optH = Math.max(13, (int)(cardH * 0.165f));
                int togH = Math.max(13, (int)(cardH * 0.175f));
                int btnW = cardW - 10;
                int optX = cx + 5;
                int optY = cy + cardH - togH - optH - 6;
                int togY = optY + optH + 2;
                if (mx >= optX && mx < optX + btnW && my >= optY && my < optY + optH) {
                    optModule = mod; return true;
                }
                if (mx >= optX && mx < optX + btnW && my >= togY && my < togY + togH) {
                    mod.toggle(); return true;
                }
            }
            int gh = panH - navH - 1;
            if (mx >= gx && mx < gx + panW - sideW - 1 && my >= gy && my < gy + gh) {
                lastDragY = click.y(); dragScroll = true; return true;
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
        if (dragScroll) {
            scrollY = Math.max(0, scrollY - (int)(click.y() - lastDragY));
            lastDragY = click.y();
            return true;
        }
        return super.mouseDragged(click, dX, dY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        dragging = null; dragScroll = false;
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

    /** Fills a rectangle with rounded corners (radius r). */
    private void fillRounded(DrawContext ctx, int x1, int y1, int x2, int y2, int color, int r) {
        // Centre body
        ctx.fill(x1 + r, y1, x2 - r, y2, color);
        // Left/right strips (excluding corner areas)
        ctx.fill(x1, y1 + r, x1 + r, y2 - r, color);
        ctx.fill(x2 - r, y1 + r, x2, y2 - r, color);
        // Corner arcs (per-row)
        for (int i = 0; i < r; i++) {
            int arc = r - (int) Math.sqrt((double) r * r - (double)(r - i) * (r - i));
            ctx.fill(x1 + arc, y1 + i, x1 + r, y1 + i + 1, color); // TL
            ctx.fill(x2 - r, y1 + i, x2 - arc, y1 + i + 1, color); // TR
            ctx.fill(x1 + arc, y2 - i - 1, x1 + r, y2 - i, color); // BL
            ctx.fill(x2 - r, y2 - i - 1, x2 - arc, y2 - i, color); // BR
        }
    }

    /** Fills a rectangle rounded only at the TOP two corners. */
    private void fillRoundedTop(DrawContext ctx, int x1, int y1, int x2, int y2, int color, int r) {
        // Body below corners
        ctx.fill(x1, y1 + r, x2, y2, color);
        // Top centre strip
        ctx.fill(x1 + r, y1, x2 - r, y1 + r, color);
        // Top corner arcs
        for (int i = 0; i < r; i++) {
            int arc = r - (int) Math.sqrt((double) r * r - (double)(r - i) * (r - i));
            ctx.fill(x1 + arc, y1 + i, x1 + r, y1 + i + 1, color); // TL
            ctx.fill(x2 - r, y1 + i, x2 - arc, y1 + i + 1, color); // TR
        }
    }

    private void texScaled(DrawContext ctx, Identifier id,
                            int x, int y, int destW, int destH, int srcW, int srcH) {
        if (destW <= 0 || destH <= 0) return;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED,
                id, x, y, 0.0F, 0.0F, destW, destH, srcW, srcH, srcW, srcH);
    }

    private void drawCentered(DrawContext ctx, String text,
                               int bx, int by, int bw, int bh, int color) {
        ctx.drawText(textRenderer, text,
                bx + (bw - textRenderer.getWidth(text)) / 2,
                by + (bh - 7) / 2, color, false);
    }

    private Identifier resolveIcon(String modName) {
        String key = switch (modName.toLowerCase().replace(" ", "_")) {
            case "armor_status", "armor_hud", "armorstatus", "armorhud" -> "armor_hud";
            case "direction_hud",  "directionhud"                       -> "direction_hud";
            case "glint_colorizer", "glintcolorizer"                     -> "glint_colorizer";
            case "fullbright"                                             -> "fullbright";
            case "coordinates"                                            -> "coordinates";
            case "hitbox"                                                 -> "hitbox";
            case "chat"                                                   -> "chat";
            case "f8_menu", "f8menu", "f8_screen"                        -> "f8_screen";
            case "server_address", "serveraddress"                        -> "server_address";
            case "shiny_pots", "shinypots"                               -> "shiny_pots";
            case "potion_counter", "potioncounter"                        -> "potion_counter";
            case "mods_list", "modslist"                                 -> "mods_list";
            case "custom_crosshair", "customcrosshair"                    -> "custom_crosshair";
            case "hit_color", "hitcolor"                                 -> "hit_color";
            case "tab_list", "tablist"                                   -> "tab_list";
            case "inventory_hud", "inventoryhud"                         -> "inventory_hud";
            case "durability_viewer", "durabilityviewer"                  -> "durability_viewer";
            case "moving_status", "movingstatus"                         -> "moving_status";
            case "block_outline", "blockoutline"                         -> "block_outline";
            case "chunkmap", "chunk_map"                                 -> "chunkmap";
            case "chunk_borders", "chunkborders"                         -> "chunk_borders";
            case "offhand_hud", "offhandhud"                             -> "offhand_hud";
            case "totem_counter", "totemcounter"                         -> "totem_counter";
            case "left_hand", "lefthand"                                 -> "left_hand";
            case "framex"                                                 -> "framex";
            case "custom_scoreboard", "customscoreboard"                  -> "custom_scoreboard";
            case "killaura", "kill_aura"                                 -> "hit_color";
            case "aimassist", "aim_assist"                               -> "direction_hud";
            case "reach"                                                  -> "hitbox";
            case "speed", "sprint"                                       -> "moving_status";
            case "nofall", "no_fall"                                     -> "mods_list";
            case "esp"                                                    -> "block_outline";
            case "zoom"                                                   -> "custom_crosshair";
            default                                                       -> null;
        };
        return key == null ? null : iconTex(key);
    }
}
