package com.azeluxclient.module.render;

import com.azeluxclient.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BowItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Trajectories — renders a predicted parabolic arrow path while drawing a bow.
 *
 * Physics (mirrors MC's ArrowEntity):
 *   • Initial speed  = charge × 3.0 blocks/tick  (full charge at 20 ticks)
 *   • Air resistance = velocity × 0.99 per tick
 *   • Gravity        = −0.05 blocks/tick² applied to Y velocity each tick
 *
 * Rendering:
 *   • Green  — clear path, no entity in the way
 *   • Red    — trajectory intersects a player or mob bounding box
 *   • Fades toward the end of the arc for depth cue
 *   • Simulation stops when the arrow would hit a solid block
 */
public class Trajectories extends Module {

    private static final int   MAX_TICKS   = 120; // simulate up to 6 seconds of flight
    private static final float ARROW_EXPAND = 0.3f; // arrow hitbox radius

    public Trajectories() {
        super("Trajectories", "Shows bow trajectory arc. Turns red when aimed at an entity.", Category.RENDER);
        WorldRenderEvents.AFTER_ENTITIES.register(this::render);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    private void render(WorldRenderContext ctx) {
        if (!isEnabled()) return;
        MinecraftClient mc = mc();
        if (mc.player == null || mc.world == null) return;

        // Only show while actively drawing a bow
        if (!mc.player.isUsingItem()) return;
        if (!(mc.player.getActiveItem().getItem() instanceof BowItem)) return;

        MatrixStack        matrices = ctx.matrices();
        VertexConsumerProvider vcp = ctx.consumers();
        if (matrices == null || vcp == null) return;

        // ── Bow charge → initial arrow speed ─────────────────────────────────
        // MC bow: full power at 20 ticks of draw. getItemUseTimeLeft() counts DOWN
        // from the item's max use time (72000 for bows), so ticks drawn =
        // 72000 − getItemUseTimeLeft().  We cap at 20 for the speed formula.
        int   usedTicks = Math.min(20, Math.max(0, 72000 - mc.player.getItemUseTimeLeft()));
        float charge    = usedTicks / 20.0f;
        float speed     = charge * 3.0f;
        if (speed < 0.05f) return; // barely drawn — skip

        // ── Start position: player eye ────────────────────────────────────────
        Vec3d startPos = new Vec3d(
            mc.player.getX(),
            mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()),
            mc.player.getZ()
        );

        // ── Initial velocity from yaw / pitch ─────────────────────────────────
        double yr  = Math.toRadians(mc.player.getYaw());
        double pr  = Math.toRadians(mc.player.getPitch());
        Vec3d  dir = new Vec3d(
            -Math.sin(yr) * Math.cos(pr),
            -Math.sin(pr),
             Math.cos(yr) * Math.cos(pr)
        ).normalize();
        Vec3d vel = dir.multiply(speed);

        // ── Simulate arc ──────────────────────────────────────────────────────
        List<Vec3d> points = new ArrayList<>(MAX_TICKS + 1);
        points.add(startPos);

        Vec3d    pos       = startPos;
        boolean  hitEntity = false;
        int      hitIndex  = -1; // index of segment where entity is hit

        for (int t = 0; t < MAX_TICKS; t++) {
            pos = pos.add(vel);
            vel = new Vec3d(vel.x * 0.99, vel.y * 0.99 - 0.05, vel.z * 0.99);
            points.add(pos);

            // Block collision — stop if the point lands inside solid geometry
            BlockPos  bp    = BlockPos.ofFloored(pos.x, pos.y, pos.z);
            BlockState state = mc.world.getBlockState(bp);
            if (!state.getCollisionShape(mc.world, bp).isEmpty()) break;

            // Entity collision — check if any mob / player bounding box is hit
            Box checkBox = new Box(
                pos.x - ARROW_EXPAND, pos.y - ARROW_EXPAND, pos.z - ARROW_EXPAND,
                pos.x + ARROW_EXPAND, pos.y + ARROW_EXPAND, pos.z + ARROW_EXPAND
            );
            boolean found = !mc.world.getEntitiesByClass(LivingEntity.class, checkBox,
                    e -> e != mc.player && !e.isDead()).isEmpty();
            if (found) {
                hitEntity = true;
                hitIndex  = t;
                break;
            }
        }

        if (points.size() < 2) return;

        // ── Draw line segments ────────────────────────────────────────────────
        Vec3d         cam   = mc.gameRenderer.getCamera().getCameraPos();
        VertexConsumer lines = vcp.getBuffer(RenderLayers.LINES);
        int            total = points.size();

        for (int i = 0; i < total - 1; i++) {
            Vec3d p1 = points.get(i);
            Vec3d p2 = points.get(i + 1);

            // Fade toward end of arc
            float alpha = Math.max(0.15f, 1.0f - (float) i / total);

            float r, g, b;
            if (hitEntity && i >= hitIndex) {
                // Red from the hit point onward (shows where the arrow lands)
                r = 1f; g = 0.15f; b = 0.15f;
            } else if (hitEntity) {
                // Leading section in orange when something is in the path
                r = 1f; g = 0.6f; b = 0f;
            } else {
                // Clean path — green
                r = 0.2f; g = 1f; b = 0.3f;
            }

            drawSegment(matrices, lines, p1, p2, cam, r, g, b, alpha);
        }

        // Draw a small cross / dot at the predicted impact point
        Vec3d impact = points.get(total - 1);
        drawDot(matrices, lines, impact, cam,
                hitEntity ? 1f : 0.2f,
                hitEntity ? 0.15f : 1f,
                hitEntity ? 0.15f : 0.3f);
    }

    // ── Draw helpers ──────────────────────────────────────────────────────────

    private static void drawSegment(MatrixStack ms, VertexConsumer vc,
                                    Vec3d p1, Vec3d p2, Vec3d cam,
                                    float r, float g, float b, float a) {
        float x1 = (float)(p1.x - cam.x), y1 = (float)(p1.y - cam.y), z1 = (float)(p1.z - cam.z);
        float x2 = (float)(p2.x - cam.x), y2 = (float)(p2.y - cam.y), z2 = (float)(p2.z - cam.z);
        float dx = x2-x1, dy = y2-y1, dz = z2-z1;
        float len = (float)Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len < 0.001f) return;
        dx /= len; dy /= len; dz /= len;
        MatrixStack.Entry e = ms.peek();
        Matrix4f          m = e.getPositionMatrix();
        vc.vertex(m, x1, y1, z1).color(r, g, b, a).normal(e, dx, dy, dz).lineWidth(1f);
        vc.vertex(m, x2, y2, z2).color(r, g, b, a).normal(e, dx, dy, dz).lineWidth(1f);
    }

    /** Small ± cross at the impact point so it's easy to see exactly where the arrow lands. */
    private static void drawDot(MatrixStack ms, VertexConsumer vc,
                                Vec3d p, Vec3d cam, float r, float g, float b) {
        float s = 0.1f; // cross arm length
        float cx = (float)(p.x - cam.x), cy = (float)(p.y - cam.y), cz = (float)(p.z - cam.z);
        MatrixStack.Entry e = ms.peek();
        Matrix4f          m = e.getPositionMatrix();
        // X axis arm
        vc.vertex(m, cx-s, cy, cz).color(r,g,b,1f).normal(e,1,0,0).lineWidth(1f);
        vc.vertex(m, cx+s, cy, cz).color(r,g,b,1f).normal(e,1,0,0).lineWidth(1f);
        // Y axis arm
        vc.vertex(m, cx, cy-s, cz).color(r,g,b,1f).normal(e,0,1,0).lineWidth(1f);
        vc.vertex(m, cx, cy+s, cz).color(r,g,b,1f).normal(e,0,1,0).lineWidth(1f);
        // Z axis arm
        vc.vertex(m, cx, cy, cz-s).color(r,g,b,1f).normal(e,0,0,1).lineWidth(1f);
        vc.vertex(m, cx, cy, cz+s).color(r,g,b,1f).normal(e,0,0,1).lineWidth(1f);
    }
}
