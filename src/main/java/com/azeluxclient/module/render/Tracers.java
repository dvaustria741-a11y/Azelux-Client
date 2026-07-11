package com.azeluxclient.module.render;

import com.azeluxclient.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class Tracers extends Module {

    public Tracers() {
        super("Tracers", "Draws a line from the center of your screen to nearby entities.", Category.RENDER);
        WorldRenderEvents.AFTER_ENTITIES.register(this::render);
    }

    private void render(WorldRenderContext ctx) {
        if (!isEnabled()) return;
        MinecraftClient mc = mc();
        if (mc.player == null || mc.world == null) return;

        MatrixStack matrices = ctx.matrices();
        VertexConsumerProvider vcp = ctx.consumers();
        if (matrices == null || vcp == null) return;

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        VertexConsumer lines = vcp.getBuffer(RenderLayers.LINES);
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f mat = entry.getPositionMatrix();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !(entity instanceof LivingEntity)) continue;

            float r, g, b;
            if (entity instanceof PlayerEntity) { r = 1f; g = 0.3f; b = 0.3f; }
            else                                { r = 0.3f; g = 1f;  b = 0.3f; }

            float ex = (float)(entity.getX() - cam.x);
            float ey = (float)(entity.getY() + entity.getHeight() * 0.5f - cam.y);
            float ez = (float)(entity.getZ() - cam.z);

            float len = (float)Math.sqrt(ex*ex + ey*ey + ez*ez);
            if (len < 0.001f) continue;
            float nx = ex/len, ny = ey/len, nz = ez/len;

            lines.vertex(mat, 0, 0, 0).color(r, g, b, 1f).normal(entry, nx, ny, nz);
            lines.vertex(mat, ex, ey, ez).color(r, g, b, 0.4f).normal(entry, nx, ny, nz);
        }
    }
}
