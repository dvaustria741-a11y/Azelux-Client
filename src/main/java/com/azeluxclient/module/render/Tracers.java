package com.azeluxclient.module.render;

import com.azeluxclient.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class Tracers extends Module {

    public Tracers() {
        super("Tracers", "Draws a line from the center of your screen to nearby entities.", Category.RENDER);
        WorldRenderEvents.AFTER_ENTITIES.register(this::render);
    }

    private void render(WorldRenderContext ctx) {
        if (!isEnabled()) return;

        WorldRenderState worldState = ctx.worldState();
        if (worldState == null) return;

        MatrixStack matrices = ctx.matrices();
        VertexConsumerProvider vcp = ctx.consumers();
        if (matrices == null || vcp == null) return;

        CameraRenderState cam = worldState.cameraRenderState;
        if (cam == null || cam.pos == null) return;
        Vec3d camPos = cam.pos;

        VertexConsumer lines = vcp.getBuffer(RenderLayers.LINES);
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f mat = entry.getPositionMatrix();

        for (EntityRenderState state : worldState.entityRenderStates) {
            if (!(state instanceof LivingEntityRenderState) || state.invisible) continue;

            float r, g, b;
            if (state.entityType == EntityType.PLAYER) { r = 1f; g = 0.3f; b = 0.3f; }
            else                                       { r = 0.3f; g = 1f;  b = 0.3f; }

            float ex = (float)(state.x - camPos.x);
            float ey = (float)(state.y + state.height * 0.5f - camPos.y);
            float ez = (float)(state.z - camPos.z);

            float len = (float) Math.sqrt(ex*ex + ey*ey + ez*ez);
            if (len < 0.001f) continue;
            float nx = ex/len, ny = ey/len, nz = ez/len;

            lines.vertex(mat, 0, 0, 0).color(r, g, b, 1f).normal(entry, nx, ny, nz);
            lines.vertex(mat, ex, ey, ez).color(r, g, b, 0.4f).normal(entry, nx, ny, nz);
        }
    }
}
