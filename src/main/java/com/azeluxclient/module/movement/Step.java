package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.attribute.EntityAttributes;

public class Step extends Module {
    private final SliderSetting height = register(new SliderSetting("Height", 1.0, 1.0, 3.0));

    public Step() {
        super("Step", "Step over blocks higher than one block.", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        var attr = client.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
        if (attr != null) attr.setBaseValue(height.getValue());
    }

    @Override
    public void onDisable() {
        MinecraftClient client = mc();
        if (client.player == null) return;
        var attr = client.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
        if (attr != null) attr.setBaseValue(0.6); // vanilla default
    }
}
