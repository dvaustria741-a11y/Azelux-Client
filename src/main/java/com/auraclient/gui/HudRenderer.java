package com.auraclient.gui;

import com.auraclient.AuraClient;
import com.auraclient.module.Module;
import com.auraclient.module.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;
import java.util.stream.Collectors;

public class HudRenderer {
    private static final int ACCENT  = 0xFFA78BFA;
    private static final int WHITE   = 0xFFE2E8F0;
    private static final int SHADOW  = 0x88000000;

    public static void register() {
        HudRenderCallback.EVENT.register(HudRenderer::onHud);
    }

    private static void onHud(DrawContext ctx, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        var tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();

        // watermark
        String wm = "✦ " + AuraClient.NAME + " v" + AuraClient.VERSION;
        ctx.fill(2, 2, 6 + tr.getWidth(wm), 13, SHADOW);
        ctx.drawText(tr, wm, 4, 4, ACCENT, false);

        // coords
        String xyz = String.format("XYZ  %.1f  /  %.1f  /  %.1f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ());
        ctx.drawText(tr, xyz, 4, 16, WHITE, false);

        // active module list (top-right)
        List<Module> active = ModuleManager.getModules().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());

        int y = 4;
        for (Module mod : active) {
            int tw = tr.getWidth(mod.getName());
            int x  = sw - tw - 4;
            ctx.fill(x - 2, y - 1, sw - 2, y + 9, SHADOW);
            ctx.drawText(tr, mod.getName(), x, y, ACCENT, false);
            y += 12;
        }
    }
}
