package com.azeluxclient.module.player;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;

public class AutoClicker extends Module {
    private final SliderSetting cps = register(new SliderSetting("CPS", 10.0, 1.0, 20.0));
    private int tickAccum = 0;

    public AutoClicker() {
        super("AutoClicker", "Automatically left-clicks at a set clicks-per-second.", Category.PLAYER);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;

        int ticksPerClick = Math.max(1, (int)(20.0 / cps.getValue()));
        if (++tickAccum < ticksPerClick) return;
        tickAccum = 0;

        if (client.player.getAttackCooldownProgress(0f) < 1.0f) return;

        if (client.targetedEntity != null) {
            client.interactionManager.attackEntity(client.player, client.targetedEntity);
            client.player.swingHand(Hand.MAIN_HAND);
        }
    }

    @Override
    public void onDisable() { tickAccum = 0; }
}
