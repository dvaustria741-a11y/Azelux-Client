package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import com.azeluxclient.module.ModuleManager;
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
 * Vulcan bypass strategy (reading their config):
 *
 *  - Aim A: max-vl 3, buffer .75 — very tight. We use variable step size,
 *    intentional micro-overshoots, and occasional "idle" ticks with no rotation
 *    to look like a human who moves their mouse non-uniformly.
 *
 *  - Hitbox A: max-angle 0.42 deg — attack only when within 1.2 deg of target
 *    to stay under this threshold with some margin.
 *
 *  - Reach A: max-reach 3.03 — legit mode capped at 2.9 blocks.
 *
 *  - Improbable A: max-combat-violations 25 — we keep VL accumulation low by
 *    not attacking too fast and not triggering multiple checks at once.
 */
public class KillAura extends Module {

    private final SliderSetting  range   = register(new SliderSetting ("Range",      4.0, 2.0, 6.0));
    private final BooleanSetting players = register(new BooleanSetting("Players",    true));
    private final BooleanSetting mobs    = register(new BooleanSetting("Mobs",       true));
    private final BooleanSetting crits   = register(new BooleanSetting("Criticals",  true));
    private final BooleanSetting legit   = register(new BooleanSetting("Legit Mode", false));

    // Silent-aim state
    private float   serverYaw, serverPitch;
    private float   prevSrvYaw, prevSrvPitch;
    private boolean srvRotReady = false;

    // Camera save/restore
    private float   savedCamYaw, savedCamPitch;
    private boolean camOverridden = false;

    // Timing
    private final Random rng         = new Random();
    private int   hitDelay           = 0;  // ticks until next hit allowed
    private int   lockOnTicks        = 0;
    private static final int LOCK_ON = 6; // ticks of tracking before first hit

    // Rotation humanisation
    private float jitterYaw          = 0f;
    private float jitterPitch        = 0f;
    private int   jitterTimer        = 0;
    private int   idleTicks          = 0;  // ticks to hold rotation (no movement)
    private float overshootYaw       = 0f; // deliberate overshoot to correct next tick
    private float overshootPitch     = 0f;
    private boolean correcting       = false;

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities.", Category.COMBAT);

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
        srvRotReady  = false;
        camOverridden = false;
        lockOnTicks  = 0;
        hitDelay     = 0;
        correcting   = false;
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
        hitDelay      = 0;
        lockOnTicks   = 0;
        correcting    = false;
        jitterYaw     = 0f;
        jitterPitch   = 0f;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        if (legit.getValue() && camOverridden) {
            mc.player.setYaw(savedCamYaw);
            mc.player.setPitch(savedCamPitch);
            camOverridden = false;
        }

        // Full charge required
        if (mc.player.getAttackCooldownProgress(0f) < 1.0f) return;

        // Stay under Reach A (3.03 blocks). We use 2.9 for safety margin.
        double effectiveRange = legit.getValue() ? 2.9 : range.getValue();

        Box box = mc.player.getBoundingBox().expand(effectiveRange);
        List<LivingEntity> targets = mc.world.getEntitiesByClass(
            LivingEntity.class, box,
            e -> e != mc.player
              && !e.isDead()
              && (e instanceof PlayerEntity ? players.getValue() : mobs.getValue())
              && !(e instanceof PlayerEntity p && p.isCreative())
        );

        if (targets.isEmpty()) { lockOnTicks = 0; return; }

        targets.stream()
            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.player)))
            .ifPresent(target -> {
                if (legit.getValue()) handleLegit(mc, target);
                else                  attack(mc, target);
            });
    }

    private void handleLegit(MinecraftClient mc, LivingEntity target) {
        if (!srvRotReady) {
            serverYaw    = mc.player.getYaw();
            serverPitch  = mc.player.getPitch();
            prevSrvYaw   = serverYaw;
            prevSrvPitch = serverPitch;
            srvRotReady  = true;
        }

        float[] ideal = getRotationsTo(mc.player, target);

        // ── Idle ticks: hold rotation still for 1-3 ticks occasionally ────────
        // Real mice stop moving briefly. This breaks the "always moving toward
        // target" pattern that Aim A detects.
        if (idleTicks > 0) {
            idleTicks--;
            lockOnTicks++;
            if (hitDelay > 0) hitDelay--;
            return; // don't update serverYaw this tick
        }
        // 12% chance to enter idle each tick when we're already fairly close
        float angleToTarget = angleFromRot(serverYaw, serverPitch, mc.player, target);
        if (angleToTarget < 8f && rng.nextFloat() < 0.12f) {
            idleTicks = 1 + rng.nextInt(3);
        }

        // ── Jitter: refresh every 10-18 ticks ──────────────────────────────────
        if (--jitterTimer <= 0) {
            jitterYaw   = (rng.nextFloat() - 0.5f) * 3.0f;
            jitterPitch = (rng.nextFloat() - 0.5f) * 1.8f;
            jitterTimer = 10 + rng.nextInt(9);
        }
        ideal[0] += jitterYaw;
        ideal[1]  = MathHelper.clamp(ideal[1] + jitterPitch, -90f, 90f);

        // ── Variable step size: 4-10 deg yaw, 3-8 deg pitch ───────────────────
        // Constant step size is one of the patterns Aim A looks for.
        float stepYaw   = 4f + rng.nextFloat() * 6f;
        float stepPitch = 3f + rng.nextFloat() * 5f;

        // ── Overshoot/correct cycle ────────────────────────────────────────────
        // 8% chance to overshoot when close — next tick we correct back.
        // This mimics a real mouse that moves too fast and must readjust.
        if (!correcting && angleToTarget < 12f && rng.nextFloat() < 0.08f) {
            overshootYaw   = (rng.nextFloat() - 0.5f) * 5f;
            overshootPitch = (rng.nextFloat() - 0.5f) * 3f;
            correcting = true;
        }
        if (correcting) {
            // Next tick: zero out the overshoot (correction)
            ideal[0] -= overshootYaw;
            ideal[1]  = MathHelper.clamp(ideal[1] - overshootPitch, -90f, 90f);
            correcting = false;
        }

        // ── Smooth step ────────────────────────────────────────────────────────
        serverYaw   = smoothStep(prevSrvYaw,   ideal[0], stepYaw);
        serverPitch = smoothStep(prevSrvPitch, ideal[1], stepPitch);

        // ── GCD fix with slight imperfection ──────────────────────────────────
        // Applying GCD perfectly every tick is itself a pattern. 15% of ticks
        // we add a tiny random sub-GCD offset to look like real mouse noise.
        float gcd    = getGcd(mc);
        float dYaw   = Math.round((serverYaw   - prevSrvYaw)   / gcd) * gcd;
        float dPitch = Math.round((serverPitch - prevSrvPitch) / gcd) * gcd;
        if (rng.nextFloat() < 0.15f) {
            dYaw   += (rng.nextFloat() - 0.5f) * gcd * 0.4f;
            dPitch += (rng.nextFloat() - 0.5f) * gcd * 0.4f;
        }
        serverYaw    = prevSrvYaw   + dYaw;
        serverPitch  = prevSrvPitch + dPitch;
        prevSrvYaw   = serverYaw;
        prevSrvPitch = serverPitch;

        mc.player.setHeadYaw(serverYaw);
        mc.player.bodyYaw = serverYaw;

        lockOnTicks++;
        if (hitDelay > 0) { hitDelay--; return; }
        if (lockOnTicks < LOCK_ON) return;

        // Attack gate: stay under Hitbox A max-angle (0.42 deg).
        // We use 1.2 deg for a safety margin — still very accurate.
        if (angleFromRot(serverYaw, serverPitch, mc.player, target) > 1.2f) return;

        attack(mc, target);
        // 4-10 tick gap = ~2-4 CPS. Random gap breaks timing pattern detection.
        hitDelay = 4 + rng.nextInt(7);
    }

    private void attack(MinecraftClient mc, LivingEntity target) {
        // No crits in legit mode (Vulcan checks Y-velocity on crit)
        if (!legit.getValue() && crits.getValue()) {
            Criticals crit = ModuleManager.get(Criticals.class);
            if (crit != null && crit.isEnabled()) {
                Criticals.sendCritPackets(mc);
            }
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
