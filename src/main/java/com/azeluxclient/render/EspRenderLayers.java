package com.azeluxclient.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/**
 * Custom render layers for the Azelux ESP system.
 *
 * In MC 1.21.11 the old RenderPhase API is gone. Depth behaviour is now
 * baked into a {@link RenderPipeline} via {@link DepthTestFunction}.
 * {@link DepthTestFunction#NO_DEPTH_TEST} makes every fragment pass the
 * depth test, so boxes and tracers draw through solid walls.
 *
 * We build the pipeline manually from the two public snippets that vanilla
 * {@code RENDERTYPE_LINES_SNIPPET} is composed of, since that snippet is
 * private in this build.
 */
public final class EspRenderLayers {

    /**
     * Draws lines with the vanilla "rendertype_lines" shader but with depth
     * testing disabled – renders through every block/entity.
     */
    public static final RenderPipeline ESP_LINES_NO_DEPTH_PIPELINE =
        RenderPipeline.builder(
                RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET,
                RenderPipelines.GLOBALS_SNIPPET
            )
            .withLocation(Identifier.of("azeluxclient", "esp_lines_no_depth"))
            .withVertexShader("core/rendertype_lines")
            .withFragmentShader("core/rendertype_lines")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.DrawMode.LINES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build();

    /**
     * A {@link RenderLayer} backed by {@link #ESP_LINES_NO_DEPTH_PIPELINE}.
     * Use this everywhere you want ESP lines/boxes that ignore walls.
     */
    public static final RenderLayer ESP_LINES = RenderLayer.of(
        "azeluxclient_esp_no_depth",
        RenderSetup.builder(ESP_LINES_NO_DEPTH_PIPELINE)
            .translucent()
            .build()
    );

    private EspRenderLayers() {}
}
