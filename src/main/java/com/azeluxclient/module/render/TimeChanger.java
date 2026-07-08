package com.azeluxclient.module.render;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;

public class TimeChanger extends Module {
    // 0 = dawn, 6000 = noon, 12000 = dusk, 18000 = midnight
    private final SliderSetting time = register(new SliderSetting("Time", 6000.0, 0.0, 24000.0));
    private long savedTime = -1;

    public TimeChanger() {
        super("TimeChanger", "Forces a client-side time of day for lighting changes.", Category.RENDER);
    }

    @Override
    public void onEnable() {
        MinecraftClient client = mc();
        if (client.world != null) savedTime = client.world.getTime();
    }

    @Override
    public void onDisable() {
        MinecraftClient client = mc();
        if (client.world != null && savedTime >= 0)
            client.world.getLevelProperties().setTimeOfDay(savedTime);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.world == null) return;
        client.world.getLevelProperties().setTimeOfDay((long) time.getValue());
    }
}
