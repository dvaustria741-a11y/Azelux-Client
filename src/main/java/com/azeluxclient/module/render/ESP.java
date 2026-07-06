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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

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
            drawBox(matrices, lines, box, r, g, b, 1.0f);

            matrices.pop();
        }
    }

    // WorldRenderer.drawBox was removed in 1.21.2+; draw the 12 box edges manually.
    // normal() now takes MatrixStack.Entry (not Matrix3f) in 1.21.11.
    private static void drawBox(MatrixStack matrices, VertexConsumer lines, Box box,
                                 float r, float g, float b, float a) {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f mat = entry.getPositionMatrix();

        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        // Bottom face
        drawLine(lines, mat, entry, x1, y1, z1, x2, y1, z1, r, g, b, a);
        drawLine(lines, mat, entry, x2, y1, z1, x2, y1, z2, r, g, b, a);
        drawLine(lines, mat, entry, x2, y1, z2, x1, y1, z2, r, g, b, a);
        drawLine(lines, mat, entry, x1, y1, z2, x1, y1, z1, r, g, b, a);
        // Top face
        drawLine(lines, mat, entry, x1, y2, z1, x2, y2, z1, r, g, b, a);
        drawLine(lines, mat, entry, x2, y2, z1, x2, y2, z2, r, g, b, a);
        drawLine(lines, mat, entry, x2, y2, z2, x1, y2, z2, r, g, b, a);
        drawLine(lines, mat, entry, x1, y2, z2, x1, y2, z1, r, g, b, a);
        // Vertical edges
        drawLine(lines, mat, entry, x1, y1, z1, x1, y2, z1, r, g, b, a);
        drawLine(lines, mat, entry, x2, y1, z1, x2, y2, z1, r, g, b, a);
        drawLine(lines, mat, entry, x2, y1, z2, x2, y2, z2, r, g, b, a);
        drawLine(lines, mat, entry, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void drawLine(VertexConsumer lines, Matrix4f mat, MatrixStack.Entry entry,
                                  float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  float r, float g, float b, float a) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0) return;
        float nx = dx / len, ny = dy / len, nz = dz / len;

        lines.vertex(mat, x1, y1, z1).color(r, g, b, a).normal(entry, nx, ny, nz);
        lines.vertex(mat, x2, y2, z2).color(r, g, b, a).normal(entry, nx, ny, nz);
    }
}
