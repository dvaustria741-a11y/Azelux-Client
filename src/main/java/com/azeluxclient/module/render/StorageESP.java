package com.azeluxclient.module.render;

import com.azeluxclient.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

public class StorageESP extends Module {

    /**
     * A custom render layer identical to RenderLayers.LINES but with
     * ALWAYS_DEPTH_TEST so boxes and tracers render through walls.
     */
    private static final RenderLayer THROUGH_WALL_LINES = RenderLayer.of(
        "azelux_storage_esp",
        VertexFormats.LINES,
        VertexFormat.DrawMode.LINES,
        256,
        RenderLayer.MultiPhaseParameters.builder()
            .program(RenderPhase.LINES_PROGRAM)
            .lineWidth(new RenderPhase.LineWidth(OptionalDouble.empty()))
            .layering(RenderPhase.NO_LAYERING)
            .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
            .writeMaskState(RenderPhase.COLOR_MASK)
            .depthTest(RenderPhase.ALWAYS_DEPTH_TEST)
            .build(false)
    );

    private final List<BlockPos> cached = new ArrayList<>();
    private int scanDelay = 0;

    public StorageESP() {
        super("StorageESP", "Highlights nearby storage blocks through walls with tracer lines.", Category.RENDER);
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

        // THROUGH_WALL_LINES uses ALWAYS_DEPTH_TEST → renders behind blocks
        VertexConsumer lines = vcp.getBuffer(THROUGH_WALL_LINES);
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f mat = entry.getPositionMatrix();

        for (BlockPos pos : new ArrayList<>(cached)) {
            float ox = (float)(pos.getX() - camPos.x);
            float oy = (float)(pos.getY() - camPos.y);
            float oz = (float)(pos.getZ() - camPos.z);

            // Gold ESP box
            drawBox(lines, mat, entry, ox, oy, oz, 1f, 0.84f, 0f, 1f);

            // Yellow tracer from camera (0,0,0) to block centre
            float cx = ox + 0.5f, cy = oy + 0.5f, cz = oz + 0.5f;
            float len = (float)Math.sqrt(cx*cx + cy*cy + cz*cz);
            if (len > 0.001f) {
                float nx = cx/len, ny = cy/len, nz = cz/len;
                lines.vertex(mat, 0f, 0f, 0f).color(1f,1f,0f,0.8f).normal(entry,nx,ny,nz).lineWidth(1.5f);
                lines.vertex(mat, cx, cy, cz).color(1f,1f,0f,0.8f).normal(entry,nx,ny,nz).lineWidth(1.5f);
            }
        }
    }

    // Draws a 1×1×1 outline box with origin at (ox, oy, oz)
    private static void drawBox(VertexConsumer vc, Matrix4f m, MatrixStack.Entry e,
                                 float ox, float oy, float oz,
                                 float r, float g, float b, float a) {
        float x1=ox, y1=oy, z1=oz, x2=ox+1, y2=oy+1, z2=oz+1;
        line(vc,m,e, x1,y1,z1, x2,y1,z1, r,g,b,a);
        line(vc,m,e, x2,y1,z1, x2,y1,z2, r,g,b,a);
        line(vc,m,e, x2,y1,z2, x1,y1,z2, r,g,b,a);
        line(vc,m,e, x1,y1,z2, x1,y1,z1, r,g,b,a);
        line(vc,m,e, x1,y2,z1, x2,y2,z1, r,g,b,a);
        line(vc,m,e, x2,y2,z1, x2,y2,z2, r,g,b,a);
        line(vc,m,e, x2,y2,z2, x1,y2,z2, r,g,b,a);
        line(vc,m,e, x1,y2,z2, x1,y2,z1, r,g,b,a);
        line(vc,m,e, x1,y1,z1, x1,y2,z1, r,g,b,a);
        line(vc,m,e, x2,y1,z1, x2,y2,z1, r,g,b,a);
        line(vc,m,e, x2,y1,z2, x2,y2,z2, r,g,b,a);
        line(vc,m,e, x1,y1,z2, x1,y2,z2, r,g,b,a);
    }

    private static void line(VertexConsumer vc, Matrix4f m, MatrixStack.Entry e,
                              float x1,float y1,float z1, float x2,float y2,float z2,
                              float r,float g,float b,float a) {
        float dx=x2-x1, dy=y2-y1, dz=z2-z1;
        float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(len==0) return;
        vc.vertex(m,x1,y1,z1).color(r,g,b,a).normal(e,dx/len,dy/len,dz/len).lineWidth(1.5f);
        vc.vertex(m,x2,y2,z2).color(r,g,b,a).normal(e,dx/len,dy/len,dz/len).lineWidth(1.5f);
    }
}
