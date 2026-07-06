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
        if (client != null && client.options != null) {
            prevGamma = client.options.getGamma().getValue();
            // Set once on enable — calling setValue() every tick caused Sodium 0.8.x
            // to spam "Illegal option value 16.0 for Brightness" every frame.
            client.options.getGamma().setValue(16.0);
        }
    }

    @Override
    public void onDisable() {
        MinecraftClient client = mc();
        if (client != null && client.options != null)
            client.options.getGamma().setValue(prevGamma);
    }

    // onTick intentionally removed — gamma only needs to be applied once.
}
