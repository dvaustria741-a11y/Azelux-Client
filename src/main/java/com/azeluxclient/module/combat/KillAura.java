package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.BooleanSetting;
import com.azeluxclient.setting.SliderSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * KillAura — Normal mode + Legit (Silent Aim) mode.
 *
 * Legit mode attack rules (Vulcan bypass):
 *   1. Smooth rotation toward target — max 8 deg/tick yaw, 7 deg/tick pitch
 *   2. GCD fix — delta snapped to mouse-sensitivity granularity
 *   3. Human jitter — small random drift refreshed every 14-22 ticks
 *   4. Attack ONLY when crosshair is within 5° of target centre
 *   5. No artificial criticals in legit mode (Vulcan checks jump/fall pattern)
 *   6. CPS throttled: 3-8 tick gap between hits (≈ 2.5-6 CPS at 20 TPS)
 *   7. Legit range capped at 2.8 blocks
 */
public class KillAura extends Module {

    // ── Settings ───────────────────────────────────────────────────────────────
    private final SliderSetting  range   = register(new SliderSetting ("Range",      4.0, 2.0, 6.0));
    private final BooleanSetting players = register(new BooleanSetting("Players",    true));
    private final BooleanSetting mobs    = register(new BooleanSetting("Mobs",       true));
    private final BooleanSetting crits   = register(new BooleanSetting("Criticals",  true));
    private final BooleanSetting legit   = register(new BooleanSetting("Legit Mode", false));

    // ── Silent-aim rotation state ──────────────────────────────────────────────
    private float   serverYaw, serverPitch;
    private float   prevSrvYaw, prevSrvPitch;
    private boolean srvRotReady = false;

    // ── Camera save/restore ────────────────────────────────────────────────────
    private float   savedCamYaw, savedCamPitch;
    private boolean camOverridden = false;

    // ── Timing & jitter ────────────────────────────────────────────────────────
    private final Random rng     = new Random();
    private int   skipTicks      = 0;   // ticks to wait before next hit
    private float jitterYaw      = 0f;
    private float jitterPitch    = 0f;
    private int   jitterCooldown = 0;

    // ── Lock-on: must track target for N ticks before first hit ───────────────
    private int   lockOnTicks    = 0;
    private static final int LOCK_ON_REQUIRED = 4; // ~200ms of tracking before hitting

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities.", Category.COMBAT);

        // START fires BEFORE vanilla sendMovementPackets() —
        // we apply serverYaw here so the look packet carries the correct facing.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (!isEnabled() || !legit.getValue()) return;
            if (client.player == null || !srvRotReady) return;

            savedCamYaw   = client.player.getYaw();
            savedCamPitch = client.player.getPitch();
            camOverridden = true;

            client.player.setYaw(serverYaw);
            client.player.setPitch(serverPitch);
            client.player.setHeadYaw(serverYaw);
            client.player.bodyYaw = serverYaw;
        });
    }

    @Override
    public void onEnable() {
        srvRotReady   = false;
        camOverridden = false;
        lockOnTicks   = 0;
        skipTicks     = 0;
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = mc();
        if (mc != null && mc.player != null && camOverridden) {
            mc.player.setYaw(savedCamYaw);
            mc.player.setPitch(savedCamPitch);
        }
        srvRotReady   = false;
        camOverridden = false;
        skipTicks     = 0;
        lockOnTicks   = 0;
        jitterYaw     = 0f;
        jitterPitch   = 0f;
        jitterCooldown = 0;
    }

    // ── Main tick (END — after sendMovementPackets) ────────────────────────────

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        // Restore real camera before rendering
        if (legit.getValue() && camOverridden) {
            mc.player.setYaw(savedCamYaw);
            mc.player.setPitch(savedCamPitch);
            camOverridden = false;
        }

        // Must be fully charged before attacking
        if (mc.player.getAttackCooldownProgress(0f) < 1.0f) return;

        double effectiveRange = legit.getValue() ? 2.8 : range.getValue();

        Box box = mc.player.getBoundingBox().expand(effectiveRange);
        List<LivingEntity> targets = mc.world.getEntitiesByClass(
            LivingEntity.class, box,
            e -> e != mc.player
              && !e.isDead()
              && (e instanceof PlayerEntity ? players.getValue() : mobs.getValue())
              && !(e instanceof PlayerEntity p && p.isCreative())
        );

        targets.stream()
            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.player)))
            .ifPresent(target -> {
                if (legit.getValue()) handleLegit(mc, target);
                else                  attack(mc, target);
            });

        // Reset lock-on if no target found this tick
        if (targets.isEmpty()) lockOnTicks = 0;
    }

    // ── Legit / Silent-Aim handler ─────────────────────────────────────────────

    private void handleLegit(MinecraftClient mc, LivingEntity target) {
        // Seed server rotation from real camera on first use
        if (!srvRotReady) {
            serverYaw    = mc.player.getYaw();
            serverPitch  = mc.player.getPitch();
            prevSrvYaw   = serverYaw;
            prevSrvPitch = serverPitch;
            srvRotReady  = true;
        }

        // 1. Ideal rotation toward target centre
        float[] ideal = getRotationsTo(mc.player, target);

        // 2. Refresh jitter every 14-22 ticks
        if (--jitterCooldown <= 0) {
            jitterYaw      = (rng.nextFloat() - 0.5f) * 2.5f;
            jitterPitch    = (rng.nextFloat() - 0.5f) * 1.5f;
            jitterCooldown = 14 + rng.nextInt(9);
        }
        ideal[0] += jitterYaw;
        ideal[1]  = MathHelper.clamp(ideal[1] + jitterPitch, -90f, 90f);

        // 3. Slow smooth step — max 8 deg yaw / 7 deg pitch per tick (human-like)
        serverYaw   = smoothStep(prevSrvYaw,   ideal[0], 8f);
        serverPitch = smoothStep(prevSrvPitch, ideal[1], 7f);

        // 4. GCD fix
        float gcd    = getGcd(mc);
        float dYaw   = Math.round((serverYaw   - prevSrvYaw)   / gcd) * gcd;
        float dPitch = Math.round((serverPitch - prevSrvPitch) / gcd) * gcd;
        serverYaw    = prevSrvYaw   + dYaw;
        serverPitch  = prevSrvPitch + dPitch;
        prevSrvYaw   = serverYaw;
        prevSrvPitch = serverPitch;

        // 5. Update head/body visuals
        mc.player.setHeadYaw(serverYaw);
        mc.player.bodyYaw = serverYaw;

        // 6. Increment lock-on counter (must track before first hit)
        lockOnTicks++;

        // 7. Throttle hits
        if (skipTicks > 0) { skipTicks--; return; }

        // 8. ONLY attack when crosshair is within 5° of target — legit-looking aim
        float angle = angleFromRot(serverYaw, serverPitch, mc.player, target);
        if (angle > 5f) return;

        // 9. Require lock-on warm-up before first hit
        if (lockOnTicks < LOCK_ON_REQUIRED) return;

        attack(mc, target);
        // 3-8 tick gap between hits (≈ 2.5–6 CPS) — randomised to avoid pattern detection
        skipTicks = 3 + rng.nextInt(6);
    }

    // ── Shared helpers ─────────────────────────────────────────────────────────

    private void attack(MinecraftClient mc, LivingEntity target) {
        // No artificial criticals in legit mode — Vulcan monitors jump/fall state
        if (!legit.getValue() && crits.getValue()) {
            Criticals crit = net.minecraft.util.registry.Registry.class.cast(null) == null
                ? null : null; // unused path
        }

        boolean wasSprinting = mc.player.isSprinting();
        mc.player.setSprinting(false);
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.setSprinting(wasSprinting);
    }

    private static float[] getRotationsTo(PlayerEntity player, LivingEntity target) {
        double dx    = target.getX() - player.getX();
        double dy    = (target.getY() + target.getHeight() * 0.5)
                     - (player.getY() + player.getEyeHeight(player.getPose()));
        double dz    = target.getZ() - player.getZ();
        double hDist = Math.sqrt(dx * dx + dz * dz);
        float  yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float  pitch = (float) -Math.toDegrees(Math.atan2(dy, hDist));
        return new float[]{ yaw, MathHelper.clamp(pitch, -90f, 90f) };
    }

    private static float smoothStep(float current, float target, float maxStep) {
        float diff = MathHelper.wrapDegrees(target - current);
        return current + MathHelper.clamp(diff, -maxStep, maxStep);
    }

    private static float angleFromRot(float yaw, float pitch,
                                      PlayerEntity player, LivingEntity target) {
        double yr  = Math.toRadians(yaw);
        double pr  = Math.toRadians(pitch);
        Vec3d look = new Vec3d(
            -Math.sin(yr) * Math.cos(pr),
            -Math.sin(pr),
             Math.cos(yr) * Math.cos(pr)
        ).normalize();
        Vec3d eyePos = new Vec3d(
            player.getX(),
            player.getY() + player.getEyeHeight(player.getPose()),
            player.getZ()
        );
        Vec3d toTarget = new Vec3d(
            target.getX(),
            target.getY() + target.getHeight() * 0.5,
            target.getZ()
        ).subtract(eyePos).normalize();
        double dot = MathHelper.clamp(look.dotProduct(toTarget), -1.0, 1.0);
        return (float) Math.toDegrees(Math.acos(dot));
    }

    private static float getGcd(MinecraftClient mc) {
        double sens = mc.options.getMouseSensitivity().getValue();
        double f    = sens * 0.6 + 0.2;
        return (float) (f * f * f * 1.2);
    }
}
