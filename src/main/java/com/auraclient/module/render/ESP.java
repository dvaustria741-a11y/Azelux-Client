package com.auraclient.module.render;

import com.auraclient.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class ESP extends Module {
    public ESP() {
        super("ESP", "Draws boxes around entities through walls.", Category.RENDER);
        WorldRenderEvents.AFTER_ENTITIES.register(this::render);
    }

    private void render(WorldRenderContext ctx) {
        if (!isEnabled() || ctx.world() == null || ctx.matrixStack() == null) return;

        MatrixStack matrices = ctx.matrixStack();
        VertexConsumerProvider.Immediate vcp = ctx.consumers();
        if (vcp == null) return;

        Vec3d cam = ctx.camera().getPos();

        for (Entity entity : ctx.world().getEntities()) {
            if (!(entity instanceof LivingEntity living) || living.isDead()) continue;

            float r, g, b;
            if (entity instanceof PlayerEntity) {
                r = 0.39f; g = 0.16f; b = 1.0f;
            } else {
                r = 0.1f; g = 0.9f; b = 0.1f;
            }

            matrices.push();
            matrices.translate(entity.getX() - cam.x, entity.getY() - cam.y, entity.getZ() - cam.z);

            Box box = entity.getBoundingBox().offset(-entity.getX(), -entity.getY(), -entity.getZ());
            VertexConsumer lines = vcp.getBuffer(RenderLayer.LINES);
            drawBox(matrices, lines, box, r, g, b);

            matrices.pop();
        }
    }

    private void drawBox(MatrixStack ms, VertexConsumer vc, Box box, float r, float g, float b) {
        Matrix4f m = ms.peek().getPositionMatrix();
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;
        float a = 1.0f;
        // bottom
        l(vc,m, x1,y1,z1, x2,y1,z1, r,g,b,a); l(vc,m, x2,y1,z1, x2,y1,z2, r,g,b,a);
        l(vc,m, x2,y1,z2, x1,y1,z2, r,g,b,a); l(vc,m, x1,y1,z2, x1,y1,z1, r,g,b,a);
        // top
        l(vc,m, x1,y2,z1, x2,y2,z1, r,g,b,a); l(vc,m, x2,y2,z1, x2,y2,z2, r,g,b,a);
        l(vc,m, x2,y2,z2, x1,y2,z2, r,g,b,a); l(vc,m, x1,y2,z2, x1,y2,z1, r,g,b,a);
        // verticals
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
