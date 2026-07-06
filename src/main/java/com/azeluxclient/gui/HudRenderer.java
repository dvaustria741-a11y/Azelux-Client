package com.azeluxclient.gui;

import com.azeluxclient.AzeluxClient;
import com.azeluxclient.module.Module;
import com.azeluxclient.module.ModuleManager;
import com.azeluxclient.module.hud.*;
import com.azeluxclient.module.misc.ChunkBorders;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import java.util.List;
import java.util.stream.Collectors;

public class HudRenderer {
    private static final int COL_ACCENT = 0xFFA78BFA;
    private static final int COL_WHITE  = 0xFFE2E8F0;
    private static final int COL_DIM    = 0xFF94A3B8;
    private static final int COL_GREEN  = 0xFF4ADE80;
    private static final int COL_RED    = 0xFFFF6B6B;
    private static final int COL_YELLOW = 0xFFFFD93D;
    private static final int COL_SHADOW = 0x88000000;

    public static void register() {
        HudRenderCallback.EVENT.register(HudRenderer::onHud);
    }

    private static void onHud(DrawContext ctx, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        var tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        // Watermark
        String wm = "♦ " + AzeluxClient.NAME + " v" + AzeluxClient.VERSION;
        ctx.fill(2, 2, 6 + tr.getWidth(wm), 13, COL_SHADOW);
        ctx.drawText(tr, wm, 4, 4, COL_ACCENT, false);

        // XYZ
        String xyz = String.format("XYZ  %.1f / %.1f / %.1f",
            mc.player.getX(), mc.player.getY(), mc.player.getZ());
        ctx.drawText(tr, xyz, 4, 16, COL_DIM, false);

        // Active modules (top-right)
        List<Module> active = ModuleManager.getModules().stream()
            .filter(Module::isEnabled).collect(Collectors.toList());
        int modY = 4;
        for (Module mod : active) {
            int tw = tr.getWidth(mod.getName());
            int x  = sw - tw - 4;
            ctx.fill(x - 2, modY - 1, sw - 2, modY + 9, COL_SHADOW);
            ctx.drawText(tr, mod.getName(), x, modY, COL_ACCENT, false);
            modY += 12;
        }

        int leftY = 28;

        // ArmorHUD
        ArmorHUD armorHUD = getModule(ArmorHUD.class);
        if (armorHUD != null && armorHUD.isEnabled()) {
            leftY = renderArmorHUD(ctx, mc, armorHUD, leftY);
        }

        // DirectionHUD
        DirectionHUD dirHUD = getModule(DirectionHUD.class);
        if (dirHUD != null && dirHUD.isEnabled()) {
            String txt = "⌖ " + dirHUD.getFacingLabel(mc) + " - " + dirHUD.getFacingFull(mc);
            ctx.fill(2, leftY, 6 + tr.getWidth(txt), leftY + 12, COL_SHADOW);
            ctx.drawText(tr, txt, 4, leftY + 2, COL_WHITE, false);
            leftY += 14;
        }

        // MovingStatus
        MovingStatus moveStatus = getModule(MovingStatus.class);
        if (moveStatus != null && moveStatus.isEnabled()) {
            String status = moveStatus.getStatus(mc);
            int col = switch (status) {
                case "Sprinting" -> COL_GREEN;
                case "Sneaking"  -> COL_YELLOW;
                case "Jumping"   -> COL_ACCENT;
                default          -> COL_DIM;
            };
            String txt = "► " + status;
            ctx.fill(2, leftY, 6 + tr.getWidth(txt), leftY + 12, COL_SHADOW);
            ctx.drawText(tr, txt, 4, leftY + 2, col, false);
            leftY += 14;
        }

        // Totem + Potion
        int counterX = 4;
        int counterY = sh - 40;
        TotemCounter totemCounter = getModule(TotemCounter.class);
        if (totemCounter != null && totemCounter.isEnabled()) {
            int n = totemCounter.countTotems(mc);
            String txt = "♥ " + n + (n == 1 ? " Totem" : " Totems");
            ctx.fill(counterX - 2, counterY - 2, counterX + tr.getWidth(txt) + 4, counterY + 10, COL_SHADOW);
            ctx.drawText(tr, txt, counterX, counterY, n > 0 ? COL_GREEN : COL_RED, false);
            counterX += tr.getWidth(txt) + 12;
        }
        PotionCounter potCounter = getModule(PotionCounter.class);
        if (potCounter != null && potCounter.isEnabled()) {
            int n = potCounter.countPotions(mc);
            String txt = "⚗ " + n + " Pots";
            int col = n >= 4 ? COL_GREEN : n >= 1 ? COL_YELLOW : COL_RED;
            ctx.fill(counterX - 2, counterY - 2, counterX + tr.getWidth(txt) + 4, counterY + 10, COL_SHADOW);
            ctx.drawText(tr, txt, counterX, counterY, col, false);
        }

        // DurabilityViewer
        DurabilityViewer durView = getModule(DurabilityViewer.class);
        if (durView != null && durView.isEnabled() && !mc.player.getMainHandStack().isEmpty()) {
            ItemStack held = mc.player.getMainHandStack();
            if (held.isDamageable()) {
                int max = held.getMaxDamage(), cur = max - held.getDamage();
                int pct = (int)(100f * cur / max);
                int col = pct > 60 ? COL_GREEN : pct > 25 ? COL_YELLOW : COL_RED;
                String txt = "⚒ " + cur + "/" + max + " (" + pct + "%)";
                int x = sw / 2 + 24, y = sh / 2 + 10;
                ctx.fill(x - 2, y - 2, x + tr.getWidth(txt) + 4, y + 10, COL_SHADOW);
                ctx.drawText(tr, txt, x, y, col, false);
            }
        }

        // ServerAddress
        ServerAddressHUD srvHUD = getModule(ServerAddressHUD.class);
        if (srvHUD != null && srvHUD.isEnabled()) {
            String txt = "☁ " + srvHUD.getAddress(mc);
            ctx.fill(2, sh - 14, 6 + tr.getWidth(txt), sh - 2, COL_SHADOW);
            ctx.drawText(tr, txt, 4, sh - 12, COL_DIM, false);
        }

        // ChunkBorders info
        ChunkBorders chunkMod = getModule(ChunkBorders.class);
        if (chunkMod != null && chunkMod.isEnabled()) {
            int[] chunk = chunkMod.getCurrentChunk(mc);
            ctx.drawText(tr, "Chunk: " + chunk[0] + ", " + chunk[1], 4, sh - 26, COL_DIM, false);
        }
    }

    private static int renderArmorHUD(DrawContext ctx, MinecraftClient mc, ArmorHUD mod, int defaultY) {
        // Use slider positions; fall back to stacked position for Y if at default
        int x = (int) mod.xPos.getValue();
        int y = (mod.yPos.getValue() == 28.0) ? defaultY : (int) mod.yPos.getValue();
        ItemStack[] armor = mod.getArmorStacks(mc);
        ctx.fill(x - 2, y - 2, x + 76, y + 22, COL_SHADOW);
        for (int i = 0; i < armor.length && i < 4; i++) {
            ItemStack stack = armor[i];
            if (!stack.isEmpty()) {
                ctx.drawItem(stack, x + i * 18, y);
                if (mod.showDurability.getValue() && stack.isDamageable()) {
                    int max = stack.getMaxDamage(), cur = max - stack.getDamage();
                    int pct = (int)(100f * cur / max);
                    int col = pct > 60 ? COL_GREEN : pct > 25 ? COL_YELLOW : COL_RED;
                    ctx.drawText(mc.textRenderer, pct + "%", x + i * 18, y + 13, col, true);
                }
            }
        }
        return y + 26;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Module> T getModule(Class<T> clazz) {
        return (T) ModuleManager.getModules().stream()
            .filter(m -> m.getClass() == clazz)
            .findFirst().orElse(null);
    }
}
