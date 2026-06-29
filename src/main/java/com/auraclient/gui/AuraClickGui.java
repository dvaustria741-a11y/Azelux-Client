package com.auraclient.gui;

import com.auraclient.module.Module;
import com.auraclient.module.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

public class AuraClickGui extends Screen {

    // Aura colour palette
    private static final int BG          = 0xEE0D0D1F;
    private static final int HEADER      = 0xFF080815;
    private static final int CARD        = 0xFF13132A;
    private static final int BORDER_ON   = 0xFFA78BFA;
    private static final int BORDER_OFF  = 0xFF2D2D50;
    private static final int TAB_ACTIVE  = 0xFF6D28D9;
    private static final int TAB_IDLE    = 0xFF1A1A35;
    private static final int ACCENT      = 0xFFA78BFA;
    private static final int TXT_PRI     = 0xFFE2E8F0;
    private static final int TXT_SEC     = 0xFF94A3B8;
    private static final int STRIP_ON    = 0xFF7C3AED;

    private static final int WIN_W   = 430;
    private static final int WIN_H   = 290;
    private static final int HEAD_H  = 32;
    private static final int TAB_H   = 26;
    private static final int CARD_W  = 122;
    private static final int CARD_H  = 40;
    private static final int PAD     = 8;
    private static final int COLS    = 3;

    private Module.Category tab = Module.Category.COMBAT;
    private int wx, wy;

    public AuraClickGui() { super(Text.literal("AuraClient")); }

    @Override
    protected void init() {
        wx = (width  - WIN_W) / 2;
        wy = (height - WIN_H) / 2;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // ── dim backdrop ──────────────────────────────────────────────────────
        ctx.fillGradient(0, 0, width, height, 0x99000000, 0xBB000000);

        // ── window ────────────────────────────────────────────────────────────
        ctx.fill(wx, wy, wx + WIN_W, wy + WIN_H, BG);
        // top accent line
        ctx.fill(wx, wy, wx + WIN_W, wy + 2, ACCENT);

        // ── header ────────────────────────────────────────────────────────────
        ctx.fill(wx, wy + 2, wx + WIN_W, wy + HEAD_H, HEADER);
        ctx.drawText(textRenderer, "✦ AuraClient", wx + 10, wy + 10, ACCENT, false);
        ctx.drawText(textRenderer, "v1.0.0  |  Right-Shift to close", wx + 130, wy + 10, TXT_SEC, false);

        // close btn
        boolean hc = mx >= wx + WIN_W - 24 && mx <= wx + WIN_W - 8
                  && my >= wy + 8           && my <= wy + 24;
        ctx.fill(wx + WIN_W - 25, wy + 7, wx + WIN_W - 7, wy + 25,
                 hc ? 0xFFBB2222 : 0xFF3A1010);
        ctx.drawText(textRenderer, "✕", wx + WIN_W - 20, wy + 10, 0xFFFFFFFF, false);

        // ── category tabs ─────────────────────────────────────────────────────
        Module.Category[] cats = Module.Category.values();
        int tw = WIN_W / cats.length;
        int ty = wy + HEAD_H;
        for (int i = 0; i < cats.length; i++) {
            Module.Category c = cats[i];
            int tx = wx + i * tw;
            boolean active = c == tab;
            ctx.fill(tx, ty, tx + tw, ty + TAB_H, active ? TAB_ACTIVE : TAB_IDLE);
            // bottom underline for active tab
            if (active) ctx.fill(tx, ty + TAB_H - 2, tx + tw, ty + TAB_H, ACCENT);
            int lx = tx + (tw - textRenderer.getWidth(c.display)) / 2;
            ctx.drawText(textRenderer, c.display, lx, ty + 7, active ? TXT_PRI : TXT_SEC, false);
        }

        // ── module cards ──────────────────────────────────────────────────────
        List<Module> mods = ModuleManager.getByCategory(tab);
        int sx = wx + PAD;
        int sy = wy + HEAD_H + TAB_H + PAD;

        for (int i = 0; i < mods.size(); i++) {
            Module mod = mods.get(i);
            int col = i % COLS;
            int row = i / COLS;
            int cx  = sx + col * (CARD_W + PAD);
            int cy  = sy + row * (CARD_H + PAD);

            // card bg
            ctx.fill(cx, cy, cx + CARD_W, cy + CARD_H, CARD);

            // border
            int borderCol = mod.isEnabled() ? BORDER_ON : BORDER_OFF;
            ctx.fill(cx,              cy,              cx + CARD_W, cy + 1,      borderCol);
            ctx.fill(cx,              cy + CARD_H - 1, cx + CARD_W, cy + CARD_H, borderCol);
            ctx.fill(cx,              cy,              cx + 1,      cy + CARD_H, borderCol);
            ctx.fill(cx + CARD_W - 1, cy,              cx + CARD_W, cy + CARD_H, borderCol);

            // left colour strip when on
            if (mod.isEnabled()) ctx.fill(cx, cy, cx + 3, cy + CARD_H, STRIP_ON);

            // text
            ctx.drawText(textRenderer, mod.getName(), cx + 8, cy + 8, TXT_PRI, false);
            ctx.drawText(textRenderer,
                    mod.isEnabled() ? "● ON" : "○ OFF",
                    cx + 8, cy + 22,
                    mod.isEnabled() ? ACCENT : TXT_SEC, false);

            // hover highlight
            boolean hover = mx >= cx && mx <= cx + CARD_W && my >= cy && my <= cy + CARD_H;
            if (hover) ctx.fill(cx, cy, cx + CARD_W, cy + CARD_H, 0x22FFFFFF);
        }

        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        if (click.button() != 0) return super.mouseClicked(click, doubled);

        // close button
        if (mx >= wx + WIN_W - 25 && mx <= wx + WIN_W - 7
         && my >= wy + 7          && my <= wy + 25) {
            close(); return true;
        }

        // tabs
        Module.Category[] cats = Module.Category.values();
        int tw = WIN_W / cats.length;
        int ty = wy + HEAD_H;
        for (int i = 0; i < cats.length; i++) {
            int tx = wx + i * tw;
            if (mx >= tx && mx <= tx + tw && my >= ty && my <= ty + TAB_H) {
                tab = cats[i]; return true;
            }
        }

        // cards
        List<Module> mods = ModuleManager.getByCategory(tab);
        int sx = wx + PAD;
        int sy = wy + HEAD_H + TAB_H + PAD;
        for (int i = 0; i < mods.size(); i++) {
            int cx = sx + (i % COLS) * (CARD_W + PAD);
            int cy = sy + (i / COLS) * (CARD_H + PAD);
            if (mx >= cx && mx <= cx + CARD_W && my >= cy && my <= cy + CARD_H) {
                mods.get(i).toggle(); return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override public boolean shouldPause()       { return false; }
    @Override public boolean shouldCloseOnEsc()  { return true;  }
}
