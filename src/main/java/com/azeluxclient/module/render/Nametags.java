package com.azeluxclient.module.render;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.BooleanSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

public class Nametags extends Module {
    private final BooleanSetting players = register(new BooleanSetting("Players", true));
    private final BooleanSetting mobs    = register(new BooleanSetting("Mobs", false));

    public Nametags() {
        super("Nametags", "Draws coloured health indicators above entities.", Category.RENDER);
        WorldRenderEvents.AFTER_ENTITIES.register(this::render);
    }

    private void render(WorldRenderContext ctx) {
        if (!isEnabled()) return;
        WorldRenderState ws = ctx.worldState();
        if (ws == null) return;
        MatrixStack matrices = ctx.matrices();
        VertexConsumerProvider vcp = ctx.consumers();
        if (matrices == null || vcp == null) return;
        CameraRenderState cam = ws.cameraRenderState;
        if (cam == null || cam.pos == null) return;
        Vec3d camPos = cam.pos;
        VertexConsumer lines = vcp.getBuffer(RenderLayers.LINES);

        MinecraftClient mc = MinecraftClient.getInstance();

        for (EntityRenderState state : ws.entityRenderStates) {
            if (!(state instanceof LivingEntityRenderState) || state.invisible) continue;
            boolean isPlayer = state.entityType == EntityType.PLAYER;
            if (isPlayer && !players.getValue()) continue;
            if (!isPlayer && !mobs.getValue()) continue;

            // 1.21.5+: health/maxHealth were removed from LivingEntityRenderState.
            // Look up the live entity from the world by position to get health info.
            float ratio = 0.5f;
            if (mc.world != null) {
                Box searchBox = Box.of(new Vec3d(state.x, state.y + 0.9, state.z), 1.0, 1.8, 1.0);
                List<LivingEntity> found = mc.world.getEntitiesByClass(
                        LivingEntity.class, searchBox, e -> e.getType() == state.entityType);
                if (!found.isEmpty()) {
                    LivingEntity le = found.get(0);
                    float max = le.getMaxHealth();
                    ratio = max > 0f ? le.getHealth() / max : 0f;
                }
            }
            ratio = Math.min(1f, Math.max(0f, ratio));

            float r = ratio < 0.5f ? 1f : 2f * (1f - ratio);
            float g = ratio > 0.5f ? 1f : 2f * ratio;

            matrices.push();
            matrices.translate(state.x - camPos.x, state.y - camPos.y + state.height + 0.2, state.z - camPos.z);
            float s = 0.18f;
            drawBox(matrices, lines, new Box(-s, 0, -s, s, s * 0.5f, s), r, g, 0f, 1f);
            matrices.pop();
        }
    }

    private static void drawBox(MatrixStack ms, VertexConsumer vc, Box box,
                                  float r, float g, float b, float a) {
        MatrixStack.Entry e = ms.peek();
        Matrix4f m = e.getPositionMatrix();
        float x1=(float)box.minX,y1=(float)box.minY,z1=(float)box.minZ;
        float x2=(float)box.maxX,y2=(float)box.maxY,z2=(float)box.maxZ;
        line(vc,m,e,x1,y1,z1,x2,y1,z1,r,g,b,a); line(vc,m,e,x2,y1,z1,x2,y1,z2,r,g,b,a);
        line(vc,m,e,x2,y1,z2,x1,y1,z2,r,g,b,a); line(vc,m,e,x1,y1,z2,x1,y1,z1,r,g,b,a);
        line(vc,m,e,x1,y2,z1,x2,y2,z1,r,g,b,a); line(vc,m,e,x2,y2,z1,x2,y2,z2,r,g,b,a);
        line(vc,m,e,x2,y2,z2,x1,y2,z2,r,g,b,a); line(vc,m,e,x1,y2,z2,x1,y2,z1,r,g,b,a);
        line(vc,m,e,x1,y1,z1,x1,y2,z1,r,g,b,a); line(vc,m,e,x2,y1,z1,x2,y2,z1,r,g,b,a);
        line(vc,m,e,x2,y1,z2,x2,y2,z2,r,g,b,a); line(vc,m,e,x1,y1,z2,x1,y2,z2,r,g,b,a);
    }

    private static void line(VertexConsumer vc, Matrix4f m, MatrixStack.Entry e,
                               float x1,float y1,float z1,float x2,float y2,float z2,
                               float r,float g,float b,float a) {
        float dx=x2-x1,dy=y2-y1,dz=z2-z1;
        float l=(float)Math.sqrt(dx*dx+dy*dy+dz*dz); if(l==0) return;
        vc.vertex(m,x1,y1,z1).color(r,g,b,a).normal(e,dx/l,dy/l,dz/l);
        vc.vertex(m,x2,y2,z2).color(r,g,b,a).normal(e,dx/l,dy/l,dz/l);
    }
}
