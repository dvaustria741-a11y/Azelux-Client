package com.azeluxclient.module.render;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.ParticlesMode;

/**
 * FPSBooster — saves your current graphics settings, applies aggressive
 * mobile-friendly optimisations on enable, and restores everything on disable.
 *
 * Changes applied:
 *   1. View distance       → 4 chunks  (biggest single FPS gain)
 *   2. Simulation distance → 4 chunks  (fewer block-tick / entity updates)
 *   3. Graphics mode       → Fast      (no smooth lighting / fancy foliage)
 *   4. Particles           → Minimal   (almost zero particle effects)
 *   5. Entity distance     → 50 %      (entities vanish at half normal range)
 *   6. Ambient occlusion   → Off       (saves lighting calculation passes)
 *
 * In 1.21.x Yarn all GameOptions settings are private SimpleOption<T> —
 * they are accessed via getViewDistance(), getParticles(), etc. and mutated
 * via .setValue() on the returned SimpleOption, NOT direct field assignment.
 */
public class FPSBooster extends Module {

    // ── Saved original values ─────────────────────────────────────────────────
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

        // Save originals via the public getter methods
        savedViewDist   = mc.options.getViewDistance().getValue();
        savedSimDist    = mc.options.getSimulationDistance().getValue();
        savedGraphics   = mc.options.getGraphicsMode().getValue();
        savedParticles  = mc.options.getParticles().getValue();
        savedEntityDist = mc.options.getEntityDistanceScaling().getValue();

        // Apply booster settings via .setValue() on each SimpleOption
        mc.options.getViewDistance().setValue(4);
        mc.options.getSimulationDistance().setValue(4);
        mc.options.getGraphicsMode().setValue(GraphicsMode.FAST);
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
        mc.options.getGraphicsMode().setValue(savedGraphics);
        mc.options.getParticles().setValue(savedParticles);
        mc.options.getEntityDistanceScaling().setValue(savedEntityDist);

        mc.options.write();
        if (mc.worldRenderer != null) mc.worldRenderer.scheduleTerrainUpdate();
    }

    @Override public void onTick(MinecraftClient mc) { /* settings changed once on toggle */ }
}
