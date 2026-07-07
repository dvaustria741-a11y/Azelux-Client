package com.azeluxclient.module.render;

import com.azeluxclient.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class StorageESP extends Module {
    private final List<BlockPos> cached = new ArrayList<>();
    private int scanDelay = 0;

    public StorageESP() {
        super("StorageESP", "Highlights nearby chests, barrels, and shulker boxes.", Category.RENDER);
        WorldRenderEvents.AFTER_ENTITIES.register(this::render);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (++scanDelay < 20) return;
        scanDelay = 0;

        cached.clear();
        BlockPos center = client.player.getBlockPos();
        int r = 8;
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.add(x, y, z);
                    Block blk = client.world.getBlockState(pos).getBlock();
                    if (blk instanceof ChestBlock || blk instanceof TrappedChestBlock
                     || blk instanceof BarrelBlock || blk instanceof EnderChestBlock
                     || blk instanceof ShulkerBoxBlock)
                        cached.add(pos.toImmutable());
                }
    }

    private void render(WorldRenderContext ctx) {
        if (!isEnabled() || cached.isEmpty()) return;
        WorldRenderState ws = ctx.worldState();
        MatrixStack matrices = ctx.matrices();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ws == null || matrices == null || vcp == null) return;
        CameraRenderState cam = ws.cameraRenderState;
        if (cam == null || cam.pos == null) return;
        Vec3d camPos = cam.pos;

        VertexConsumer lines = vcp.getBuffer(RenderLayers.LINES);
        for (BlockPos pos : cached) {
            matrices.push();
            matrices.translate(pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z);
            drawBox(matrices, lines, new Box(0, 0, 0, 1, 1, 1), 1f, 0.84f, 0f, 1f);
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
                              float x1,float y1,float z1,float x2,float y2,float z2,
                              float r,float g,float b,float a) {
        float dx=x2-x1, dy=y2-y1, dz=z2-z1;
        float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(len==0) return;
        vc.vertex(m,x1,y1,z1).color(r,g,b,a).normal(e,dx/len,dy/len,dz/len);
        vc.vertex(m,x2,y2,z2).color(r,g,b,a).normal(e,dx/len,dy/len,dz/len);
    }
}
