package com.azeluxclient.module.render;

import com.azeluxclient.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class ESP extends Module {
    public ESP() {
        super("ESP", "Draws boxes around entities through walls.", Category.RENDER);
        WorldRenderEvents.AFTER_ENTITIES.register(this::render);
    }

    private void render(WorldRenderContext ctx) {
        if (!isEnabled()) return;

        WorldRenderState worldState = ctx.worldState();
        if (worldState == null) return;

        MatrixStack matrices = ctx.matrices();
        VertexConsumerProvider vcp = ctx.consumers();
        if (matrices == null || vcp == null) return;

        CameraRenderState cameraState = worldState.cameraRenderState;
        if (cameraState == null || cameraState.pos == null) return;
        Vec3d cam = cameraState.pos;

        // WorldRenderer.drawBox handles the full VertexFormats.LINES vertex format
        // (including the LineWidth element required in 1.21.11 / Sodium 0.8.12)
        // so we no longer need to build vertices manually.
        VertexConsumer lines = vcp.getBuffer(RenderLayers.LINES);

        for (EntityRenderState state : worldState.entityRenderStates) {
            if (!(state instanceof LivingEntityRenderState) || state.invisible) continue;

            float r, g, b;
            if (state.entityType == EntityType.PLAYER) {
                r = 0.39f; g = 0.16f; b = 1.0f;  // violet
            } else {
                r = 0.1f;  g = 0.9f;  b = 0.1f;  // green
            }

            matrices.push();
            matrices.translate(state.x - cam.x, state.y - cam.y, state.z - cam.z);

            float hw = state.width / 2f;
            Box box = new Box(-hw, 0, -hw, hw, state.height, hw);
            WorldRenderer.drawBox(matrices, lines, box, r, g, b, 1.0f);

            matrices.pop();
        }
    }
}
