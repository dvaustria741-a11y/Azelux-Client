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
        MinecraftClient mc = mc();
        if (mc.player == null || mc.world == null) return;

        MatrixStack matrices = ctx.matrices();
        VertexConsumerProvider vcp = ctx.consumers();
        if (matrices == null || vcp == null) return;

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        VertexConsumer lines = vcp.getBuffer(RenderLayers.LINES);

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !(entity instanceof LivingEntity)) continue;

            float r, g, b;
            if (entity instanceof PlayerEntity) { r = 0.39f; g = 0.16f; b = 1.0f; }
            else                                { r = 0.1f;  g = 0.9f;  b = 0.1f; }

            matrices.push();
            matrices.translate(entity.getX() - cam.x, entity.getY() - cam.y, entity.getZ() - cam.z);
            float hw = entity.getWidth() / 2f;
            drawBox(matrices, lines, new Box(-hw, 0, -hw, hw, entity.getHeight(), hw), r, g, b, 1f);
            matrices.pop();
        }
    }

    private static void drawBox(MatrixStack ms, VertexConsumer vc, Box box,
                                 float r, float g, float b, float a) {
        MatrixStack.Entry e = ms.peek();
        Matrix4f m = e.getPositionMatrix();
        float x1=(float)box.minX, y1=(float)box.minY, z1=(float)box.minZ;
        float x2=(float)box.maxX, y2=(float)box.maxY, z2=(float)box.maxZ;
        line(vc,m,e,x1,y1,z1,x2,y1,z1,r,g,b,a); line(vc,m,e,x2,y1,z1,x2,y1,z2,r,g,b,a);
        line(vc,m,e,x2,y1,z2,x1,y1,z2,r,g,b,a); line(vc,m,e,x1,y1,z2,x1,y1,z1,r,g,b,a);
        line(vc,m,e,x1,y2,z1,x2,y2,z1,r,g,b,a); line(vc,m,e,x2,y2,z1,x2,y2,z2,r,g,b,a);
        line(vc,m,e,x2,y2,z2,x1,y2,z2,r,g,b,a); line(vc,m,e,x1,y2,z2,x1,y2,z1,r,g,b,a);
        line(vc,m,e,x1,y1,z1,x1,y2,z1,r,g,b,a); line(vc,m,e,x2,y1,z1,x2,y2,z1,r,g,b,a);
        line(vc,m,e,x2,y1,z2,x2,y2,z2,r,g,b,a); line(vc,m,e,x1,y1,z2,x1,y2,z2,r,g,b,a);
    }

    private static void line(VertexConsumer vc, Matrix4f m, MatrixStack.Entry e,
                              float x1, float y1, float z1, float x2, float y2, float z2,
                              float r, float g, float b, float a) {
        float dx=x2-x1, dy=y2-y1, dz=z2-z1;
        float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(len==0) return;
        vc.vertex(m,x1,y1,z1).color(r,g,b,a).normal(e,dx/len,dy/len,dz/len);
        vc.vertex(m,x2,y2,z2).color(r,g,b,a).normal(e,dx/len,dy/len,dz/len);
    }
}

