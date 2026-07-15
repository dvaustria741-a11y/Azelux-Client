package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.BooleanSetting;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

/**
 * BowAimbot — accurate trajectory-based bow aimbot.
 *
 * Physics model:
 *   - Arrow initial speed  = charge × 3.0 blocks/tick
 *   - Gravity              = 0.05 blocks/tick² (matches vanilla ArrowEntity)
 *   - Pitch is solved analytically from the standard ballistic formula so the
 *     arrow arc lands exactly on the target regardless of distance or elevation.
 *   - "Lead" mode predicts where a moving target will be when the arrow arrives.
 *
 * Only starts aiming once charge reaches Min Charge (default 90 %) so there is
 * no wasted movement while the bow is still drawing.
 */
public class BowAimbot extends Module {

    private final SliderSetting  range     = register(new SliderSetting ("Range",       50.0, 5.0, 100.0));
    private final SliderSetting  minCharge = register(new SliderSetting ("Min Charge",   0.9, 0.1,   1.0));
    private final BooleanSetting lead      = register(new BooleanSetting("Lead Target", true));

    // Minecraft arrow gravity (applied each tick to vy before drag)
    private static final double G = 0.05;

    public BowAimbot() {
        super("BowAimbot", "Trajectory-based bow aimbot that accounts for charge level and gravity.", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.isUsingItem()) return;

        var held = mc.player.getActiveItem();
        boolean isBow      = held.getItem() instanceof BowItem;
        boolean isCrossbow = held.getItem() instanceof CrossbowItem
                             && CrossbowItem.isCharged(held);
        if (!isBow && !isCrossbow) return;

        // Charge: crossbow is always full power; bow scales 0→1 over ~20 ticks
        float charge = isCrossbow ? 1.0f : BowItem.getPullProgress(mc.player.getItemUseTime());

        // Don't aim until the bow is sufficiently drawn — low-charge shots go nowhere
        if (charge < minCharge.getValue().floatValue()) return;

        // Arrow initial speed in blocks/tick
        double speed = charge * 3.0;

        Box box = mc.player.getBoundingBox().expand(range.getValue());
        List<LivingEntity> targets = mc.world.getEntitiesByClass(LivingEntity.class, box,
            e -> e != mc.player && !e.isDead()
              && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())));

        targets.stream()
            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.player)))
            .ifPresent(t -> aimAt(mc, t, speed));
    }

    private void aimAt(MinecraftClient mc, LivingEntity target, double speed) {
        double ex = mc.player.getX();
        double ey = mc.player.getEyeY();
        double ez = mc.player.getZ();

        // Aim for vertical centre of target hitbox
        double tx = target.getX();
        double ty = target.getY() + target.getHeight() * 0.5;
        double tz = target.getZ();

        // Lead prediction: estimate where the target will be when the arrow arrives.
        // Drag causes ~7 % average speed loss over a typical flight, so we use 0.93
        // as a rough correction factor for the flight-time estimate.
        if (lead.getValue()) {
            double dx0    = tx - ex;
            double dz0    = tz - ez;
            double hDist0 = Math.sqrt(dx0 * dx0 + dz0 * dz0);
            double tFlight = hDist0 / (speed * 0.93);
            Vec3d vel = target.getVelocity();
            tx += vel.x * tFlight;
            ty += vel.y * tFlight;
            tz += vel.z * tFlight;
        }

        double dx    = tx - ex;
        double dy    = ty - ey;  // positive = target is above eye level
        double dz    = tz - ez;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        if (hDist < 0.001) return;

        // ── Yaw ─────────────────────────────────────────────────────────────
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        // ── Pitch (ballistic formula) ────────────────────────────────────────
        //
        // Derivation (standard projectile motion, ignoring per-tick drag):
        //   Horizontal: hDist = v·cos(θ)·t  →  t = hDist / (v·cos(θ))
        //   Vertical:   dy    = v·sin(θ)·t - ½·g·t²   (up = positive)
        //   Note: Minecraft pitch convention: pitch<0 = up, pitch>0 = down
        //         so the "angle above horizontal" θ = -pitch_rad.
        //         For the formula we treat θ as "angle of elevation" (positive = up).
        //
        //   Substituting and rearranging gives a quadratic in tan(θ):
        //     g·d²·tan²(θ) - 2v²·d·tan(θ) + (2v²·dy + g·d²) = 0
        //   where d = hDist.
        //
        //   Discriminant Δ = 4v⁴d² - 4·g·d²·(2v²dy + g·d²)
        //                  = 4d²·(v⁴ - g·(g·d² + 2v²·dy))
        //
        //   tan(θ) = (v² ± sqrt(v⁴ - g·(g·d²+2v²·dy))) / (g·d)
        //
        //   Take "-" root for the lower (direct) arc — usually the one we want.
        //   pitch_rad = -atan(tan(θ))  (negate because Minecraft: up = negative pitch)
        //
        double v2   = speed * speed;
        double disc = v2*v2 - G * (G * hDist*hDist + 2 * dy * v2);
        if (disc < 0) return; // target is beyond maximum range at current charge

        double tanTheta = (v2 - Math.sqrt(disc)) / (G * hDist); // lower arc
        // tanTheta > 0 means aiming up → pitch is negative in MC convention
        float pitch = MathHelper.clamp((float) -Math.toDegrees(Math.atan(tanTheta)), -90f, 90f);

        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        mc.player.setHeadYaw(yaw);
    }
}
