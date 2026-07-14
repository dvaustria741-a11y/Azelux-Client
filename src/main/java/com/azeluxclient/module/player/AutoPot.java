package com.azeluxclient.module.player;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.util.Hand;

/**
 * AutoPot — automatically throws a splash potion when health drops below threshold.
 *
 * Sequence (3-tick state machine):
 *   Tick 0 (IDLE)       : detect low health → find splash potion → switch to it
 *   Tick 1 (LOOK_DOWN)  : set pitch to 85° (facing the ground)
 *   Tick 2 (THROW)      : right-click to throw → restore pitch → back to IDLE
 *
 * Facing down before throwing ensures the potion lands at your feet,
 * applying the effect to yourself (splash potions hit the impact area).
 */
public class AutoPot extends Module {

    private final SliderSetting threshold = register(
        new SliderSetting("Health", 14.0, 1.0, 19.0));

    // State machine
    private static final int IDLE      = 0;
    private static final int LOOK_DOWN = 1;
    private static final int THROW     = 2;

    private int   state       = IDLE;
    private float savedPitch  = 0f;
    private int   savedSlot   = -1;

    public AutoPot() {
        super("AutoPot", "Throws a splash potion when health is low.", Category.PLAYER);
    }

    @Override
    public void onDisable() {
        state = IDLE;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        switch (state) {

            case IDLE -> {
                // Only act when health is at or below the threshold
                if (mc.player.getHealth() > (float) threshold.getValue()) return;

                // Find a splash potion in the hotbar (slots 0-8)
                int slot = -1;
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getStack(i).getItem() instanceof SplashPotionItem) {
                        slot = i; break;
                    }
                }
                if (slot == -1) return; // no splash potion available

                savedSlot = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = slot;
                savedPitch = mc.player.getPitch();
                state = LOOK_DOWN;
            }

            case LOOK_DOWN -> {
                // Face straight down so the potion splashes at our feet
                mc.player.setPitch(85f);
                state = THROW;
            }

            case THROW -> {
                // Throw it
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                // Restore pitch and previous slot
                mc.player.setPitch(savedPitch);
                mc.player.getInventory().selectedSlot = savedSlot;
                state = IDLE;
            }
        }
    }
}
