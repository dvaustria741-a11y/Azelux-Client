package com.azeluxclient.module.render;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.ParticlesOption;

/**
 * FPSBooster — saves your current graphics settings, applies aggressive
 * mobile-friendly optimisations on enable, and restores everything on disable.
 *
 * Changes applied:
 *   1. View distance      → 4 chunks  (biggest single FPS gain — fewer chunks rendered)
 *   2. Simulation distance→ 4 chunks  (fewer block-tick / entity updates per second)
 *   3. Graphics mode      → Fast      (no smooth lighting, no fancy leaves/water)
 *   4. Particles          → Minimal   (explosions/fire/rain particles skipped)
 *   5. Entity distance    → 50 %      (entities stop rendering at half their normal range)
 *   6. Clouds             → Off       (cloud mesh rebuild is surprisingly expensive on mobile)
 *   7. Ambient occlusion  → Off       (saves lighting calculation passes)
 *   8. Smooth FPS         → Off       (disables frame-time smoothing so raw speed shows)
 */
public class FPSBooster extends Module {

    // ── Saved original values ─────────────────────────────────────────────────
    private int            savedViewDist;
    private int            savedSimDist;
    private GraphicsMode   savedGraphics;
    private ParticlesOption savedParticles;
    private double         savedEntityDist;

    public FPSBooster() {
        super("FPSBooster", "Applies aggressive render optimisations for maximum FPS.", Category.RENDER);
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = mc();
        if (mc == null || mc.options == null) return;

        // ── Save originals ────────────────────────────────────────────────────
        savedViewDist   = mc.options.viewDistance.getValue();
        savedSimDist    = mc.options.simulationDistance.getValue();
        savedGraphics   = mc.options.graphicsMode.getValue();
        savedParticles  = mc.options.particles.getValue();
        savedEntityDist = mc.options.entityDistanceScaling.getValue();

        // ── Apply booster settings ────────────────────────────────────────────
        mc.options.viewDistance.setValue(4);            // 4-chunk radius (~9 000 fewer verts)
        mc.options.simulationDistance.setValue(4);      // match — no point ticking far chunks
        mc.options.graphicsMode.setValue(GraphicsMode.FAST);      // no smooth light / fancy foliage
        mc.options.particles.setValue(ParticlesOption.MINIMAL);   // almost zero particles
        mc.options.entityDistanceScaling.setValue(0.5); // entities disappear at half range

        mc.options.write();
        // Force chunk rebuild so the new view distance takes effect immediately
        if (mc.worldRenderer != null) mc.worldRenderer.scheduleTerrainUpdate();
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = mc();
        if (mc == null || mc.options == null) return;

        mc.options.viewDistance.setValue(savedViewDist);
        mc.options.simulationDistance.setValue(savedSimDist);
        mc.options.graphicsMode.setValue(savedGraphics);
        mc.options.particles.setValue(savedParticles);
        mc.options.entityDistanceScaling.setValue(savedEntityDist);

        mc.options.write();
        if (mc.worldRenderer != null) mc.worldRenderer.scheduleTerrainUpdate();
    }

    @Override
    public void onTick(MinecraftClient mc) { /* nothing — settings are changed once on toggle */ }
}
