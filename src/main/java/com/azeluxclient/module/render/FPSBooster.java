package com.azeluxclient.module.render;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.particle.ParticlesMode;

/**
 * FPSBooster — saves your current graphics settings, applies aggressive
 * mobile-friendly optimisations on enable, and restores everything on disable.
 *
 * In 1.21.x Yarn:
 *   - ParticlesMode is in net.minecraft.particle (not client.option)
 *   - All GameOptions settings are private SimpleOption<T>; use getX().setValue()
 */
public class FPSBooster extends Module {

    private int          savedViewDist;
    private int          savedSimDist;
    private GraphicsMode savedGraphics;
    private ParticlesMode savedParticles;
    private double       savedEntityDist;

    public FPSBooster() {
        super("FPSBooster", "Applies aggressive render optimisations for maximum FPS.", Category.RENDER);
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = mc();
        if (mc == null || mc.options == null) return;

        savedViewDist   = mc.options.getViewDistance().getValue();
        savedSimDist    = mc.options.getSimulationDistance().getValue();
        savedGraphics   = mc.options.getPreset().getValue();
        savedParticles  = mc.options.getParticles().getValue();
        savedEntityDist = mc.options.getEntityDistanceScaling().getValue();

        mc.options.getViewDistance().setValue(4);
        mc.options.getSimulationDistance().setValue(4);
        mc.options.getPreset().setValue(GraphicsMode.FAST);
        mc.options.getParticles().setValue(ParticlesMode.MINIMAL);
        mc.options.getEntityDistanceScaling().setValue(0.5);

        mc.options.write();
        if (mc.worldRenderer != null) mc.worldRenderer.scheduleTerrainUpdate();
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = mc();
        if (mc == null || mc.options == null) return;

        mc.options.getViewDistance().setValue(savedViewDist);
        mc.options.getSimulationDistance().setValue(savedSimDist);
        mc.options.getPreset().setValue(savedGraphics);
        mc.options.getParticles().setValue(savedParticles);
        mc.options.getEntityDistanceScaling().setValue(savedEntityDist);

        mc.options.write();
        if (mc.worldRenderer != null) mc.worldRenderer.scheduleTerrainUpdate();
    }

    @Override public void onTick(MinecraftClient mc) {}
}
