package com.azeluxclient.module.render;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;

public class Fullbright extends Module {
    private static Fullbright instance;
    private double prevGamma = 1.0;

    public Fullbright() {
        super("Fullbright", "Maximizes brightness for full visibility in dark areas.", Category.RENDER);
        instance = this;
    }

    public static Fullbright getInstance() { return instance; }

    @Override
    public void onEnable() {
        MinecraftClient client = mc();
        if (client != null && client.options != null)
            prevGamma = client.options.getGamma().getValue();
        // Actual brightness override is handled by LightmapTextureManagerMixin,
        // which forces all lightmap pixels to white every tick while enabled.
        // This bypasses Sodium's gamma clamping entirely.
    }

    @Override
    public void onDisable() {
        MinecraftClient client = mc();
        if (client != null && client.options != null)
            client.options.getGamma().setValue(prevGamma);
    }
}
