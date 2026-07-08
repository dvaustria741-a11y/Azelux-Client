package com.azeluxclient.module.render;

import com.azeluxclient.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class Breadcrumbs extends Module {
    private final List<Vec3d> trail = new ArrayList<>();
    private static final int MAX_TRAIL = 500;
    private Vec3d lastPos = null;

    public Breadcrumbs() {
        super("Breadcrumbs", "Leaves a visible trail showing where you have walked.", Category.RENDER);
        WorldRenderEvents.AFTER_ENTITIES.register(this::render);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        // Use getX/Y/Z instead of getPos()
        Vec3d pos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        if (lastPos == null || lastPos.squaredDistanceTo(pos) > 0.25) {
            trail.add(pos);
            if (trail.size() > MAX_TRAIL) trail.remove(0);
            lastPos = pos;
        }
    }

    @Override
    public void onDisable() { trail.clear(); lastPos = null; }

    private void render(WorldRenderContext ctx) {
        if (!isEnabled() || trail.size() < 2) return;
        WorldRenderState ws = ctx.worldState();
        MatrixStack matrices = ctx.matrices();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ws == null || matrices == null || vcp == null) return;
        CameraRenderState cam = ws.cameraRenderState;
        if (cam == null || cam.pos == null) return;
        Vec3d camPos = cam.pos;

        VertexConsumer lines = vcp.getBuffer(RenderLayers.LINES);
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f mat = entry.getPositionMatrix();

        for (int i = 1; i < trail.size(); i++) {
            Vec3d a = trail.get(i - 1);
            Vec3d b = trail.get(i);
            float ax=(float)(a.x-camPos.x), ay=(float)(a.y-camPos.y+0.1), az=(float)(a.z-camPos.z);
            float bx=(float)(b.x-camPos.x), by=(float)(b.y-camPos.y+0.1), bz=(float)(b.z-camPos.z);
            float dx=bx-ax, dy=by-ay, dz=bz-az;
            float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            if (len == 0) continue;
            float progress = (float) i / trail.size();
            lines.vertex(mat,ax,ay,az).color(0.4f,0.6f,1f,progress).normal(entry,dx/len,dy/len,dz/len);
            lines.vertex(mat,bx,by,bz).color(0.4f,0.6f,1f,progress).normal(entry,dx/len,dy/len,dz/len);
        }
    }
}
