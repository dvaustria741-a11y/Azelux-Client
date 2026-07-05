package com.azeluxclient.module.render;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;

public class Fullbright extends Module {
    private double prevGamma = 1.0;

    public Fullbright() {
        super("Fullbright", "Maximizes brightness for full visibility in dark areas.", Category.RENDER);
    }

    @Override
    public void onEnable() {
        MinecraftClient client = mc();
        if (client != null && client.options != null)
            prevGamma = client.options.getGamma().getValue();
    }

    @Override
    public void onDisable() {
        MinecraftClient client = mc();
        if (client != null && client.options != null)
            client.options.getGamma().setValue(prevGamma);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.options != null) client.options.getGamma().setValue(16.0);
    }
}
