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

        for (EntityRenderState state : worldState.entityRenderStates) {
            if (!(state instanceof LivingEntityRenderState) || state.invisible) continue;

            float r, g, b;
            if (state.entityType == EntityType.PLAYER) {
                r = 0.39f; g = 0.16f; b = 1.0f;
            } else {
                r = 0.1f; g = 0.9f; b = 0.1f;
            }

            matrices.push();
            matrices.translate(state.x - cam.x, state.y - cam.y, state.z - cam.z);

            float hw = state.width / 2f;
            Box box = new Box(-hw, 0, -hw, hw, state.height, hw);
            VertexConsumer lines = vcp.getBuffer(RenderLayers.LINES);
            drawBox(matrices, lines, box, r, g, b);

            matrices.pop();
        }
    }

    private void drawBox(MatrixStack ms, VertexConsumer vc, Box box, float r, float g, float b) {
        Matrix4f m = ms.peek().getPositionMatrix();
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;
        float a = 1.0f;
        l(vc,m, x1,y1,z1, x2,y1,z1, r,g,b,a); l(vc,m, x2,y1,z1, x2,y1,z2, r,g,b,a);
        l(vc,m, x2,y1,z2, x1,y1,z2, r,g,b,a); l(vc,m, x1,y1,z2, x1,y1,z1, r,g,b,a);
        l(vc,m, x1,y2,z1, x2,y2,z1, r,g,b,a); l(vc,m, x2,y2,z1, x2,y2,z2, r,g,b,a);
        l(vc,m, x2,y2,z2, x1,y2,z2, r,g,b,a); l(vc,m, x1,y2,z2, x1,y2,z1, r,g,b,a);
        l(vc,m, x1,y1,z1, x1,y2,z1, r,g,b,a); l(vc,m, x2,y1,z1, x2,y2,z1, r,g,b,a);
        l(vc,m, x2,y1,z2, x2,y2,z2, r,g,b,a); l(vc,m, x1,y1,z2, x1,y2,z2, r,g,b,a);
    }

    private void l(VertexConsumer vc, Matrix4f m,
                   float x1, float y1, float z1,
                   float x2, float y2, float z2,
                   float r, float g, float b, float a) {
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
        float len = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
        if (len > 0) { nx /= len; ny /= len; nz /= len; }
        vc.vertex(m, x1, y1, z1).color(r, g, b, a).normal(nx, ny, nz);
        vc.vertex(m, x2, y2, z2).color(r, g, b, a).normal(nx, ny, nz);
    }
}
