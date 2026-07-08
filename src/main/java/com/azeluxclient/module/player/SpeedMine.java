package com.azeluxclient.module.player;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class SpeedMine extends Module {
    private final SliderSetting level = register(new SliderSetting("Haste Level", 2.0, 1.0, 5.0));

    public SpeedMine() {
        super("SpeedMine", "Apply Haste to mine blocks significantly faster.", Category.PLAYER);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        int amplifier = (int) level.getValue() - 1;
        var eff = client.player.getStatusEffect(StatusEffects.HASTE);
        if (eff != null && eff.getAmplifier() >= amplifier && eff.getDuration() > 20) return;
        client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 60, amplifier, false, false));
    }

    @Override
    public void onDisable() {
        MinecraftClient client = mc();
        if (client.player != null) client.player.removeStatusEffect(StatusEffects.HASTE);
    }
}
