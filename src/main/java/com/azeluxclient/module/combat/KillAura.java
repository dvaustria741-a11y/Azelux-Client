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
 * Vulcan bypass notes (from their config):
 *
 *  Aim A         max-vl 3, buffer-max 3, multiple .75, decay .5
 *                → very tight. Countered by variable step sizes, idle ticks,
 *                  overshoot/correct cycles, and sub-GCD noise.
 *
 *  Hitbox A      max-angle 0.42°, buffer-max 4, multiple .25, decay .125
 *                → jitter is capped at ±0.35° yaw so we can still clear 0.42°.
 *                  Attacks only fire when the ACTUAL sent rotation is < 0.40°
 *                  from target. Buffer math: decay 0.125/tick × ~7 ticks between
 *                  attacks = 0.875 recovery per cycle; ~30 % failure rate at
 *                  ±0.35° jitter means net buffer stays near 0.
 *
 *  Reach A       max 3.03 blocks → legit mode capped at 2.9.
 *
 *  Improbable A  max-combat-violations 25 → kept low by not triggering
 *                multiple checks simultaneously.
 */
public class KillAura extends Module {

    private final SliderSetting  range   = register(new SliderSetting ("Range",      4.0, 2.0, 6.0));
    private final BooleanSetting players = register(new BooleanSetting("Players",    true));
    private final BooleanSetting mobs    = register(new BooleanSetting("Mobs",       true));
    private final BooleanSetting crits   = register(new BooleanSetting("Criticals",  true));
    private final BooleanSetting legit   = register(new BooleanSetting("Legit Mode", false));

    // ── Silent-aim rotation sent to server ──────────────────────────────────
    private float   serverYaw, serverPitch;
    private float   prevSrvYaw, prevSrvPitch;
    private boolean srvRotReady = false;

    // ── Visual camera save/restore ──────────────────────────────────────────
    private float   savedCamYaw, savedCamPitch;
    private boolean camOverridden = false;

    // ── Timing ──────────────────────────────────────────────────────────────
    private final Random rng  = new Random();
    private int hitDelay      = 0;
    private int lockOnTicks   = 0;
    private static final int LOCK_ON = 6; // ticks of tracking before first hit

    // ── Rotation humanisation state ─────────────────────────────────────────
    private float jitterYaw   = 0f;
    private float jitterPitch = 0f;
    private int   jitterTimer = 0;
    private int   idleTicks   = 0;

    /**
     * Overshoot/correct: a proper 2-tick cycle.
     *
     * Bug in the old code: correcting was set true and immediately handled
     * in the same if-chain that tick — the overshoot and the correction both
     * fired at once and cancelled out. Fix: store the overshoot offset and
     * apply it THIS tick; next tick when correcting==true we reverse it.
     */
    private float   overshootYaw   = 0f;
    private float   overshootPitch = 0f;
    private boolean correcting     = false;

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities.", Category.COMBAT);

        // Apply server rotation BEFORE MC sends the movement packet this tick.
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
        hitDelay      = 0;
        correcting    = false;
        jitterTimer   = 0;
        idleTicks     = 0;
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
        idleTicks     = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        // Restore visual camera (server packets already sent in START_CLIENT_TICK)
        if (legit.getValue() && camOverridden) {
            mc.player.setYaw(savedCamYaw);
            mc.player.setPitch(savedCamPitch);
            camOverridden = false;
        }

        if (mc.player.getAttackCooldownProgress(0f) < 1.0f) return;

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

    // ── Legit (silent-aim) handler ───────────────────────────────────────────

    private void handleLegit(MinecraftClient mc, LivingEntity target) {
        // Seed server rotation from current look on first target acquisition
        if (!srvRotReady) {
            serverYaw    = mc.player.getYaw();
            serverPitch  = mc.player.getPitch();
            prevSrvYaw   = serverYaw;
            prevSrvPitch = serverPitch;
            srvRotReady  = true;
        }

        float[] ideal = getRotationsTo(mc.player, target);

        // How far our CURRENT server rotation is from the target (no jitter added yet)
        float cleanAngle = angleFromRot(serverYaw, serverPitch, mc.player, target);

        // ── Idle ticks: hold rotation still for 1-2 ticks occasionally ──────
        // Real mice stop briefly. Breaks the "always tracking" pattern Aim A flags.
        if (idleTicks > 0) {
            idleTicks--;
            lockOnTicks++;
            if (hitDelay > 0) hitDelay--;
            return;
        }
        if (cleanAngle < 10f && rng.nextFloat() < 0.10f) {
            idleTicks = 1 + rng.nextInt(2);
        }

        // ── Jitter (refresh every 8-16 ticks) ────────────────────────────────
        // Kept very small (±0.35° yaw, ±0.2° pitch) so that Hitbox A (0.42°)
        // is clearable ~70 % of the time. The old code used ±1.5°/±0.9° which
        // made the 1.2° attack gate almost never trigger.
        if (--jitterTimer <= 0) {
            jitterYaw   = (rng.nextFloat() - 0.5f) * 0.70f;  // ±0.35°
            jitterPitch = (rng.nextFloat() - 0.5f) * 0.40f;  // ±0.20°
            jitterTimer = 8 + rng.nextInt(9);
        }

        // ── Overshoot / correct  (proper 2-tick cycle) ───────────────────────
        // OLD bug: correcting was set true and immediately consumed in the same
        // tick, so no overshoot ever actually propagated to the server.
        // FIX:
        //   Tick N   → set correcting=true, add overshootYaw to target rotation
        //   Tick N+1 → correcting==true, subtract overshootYaw (pull back)
        float osYaw = 0f, osPitch = 0f;
        if (correcting) {
            // Pull back from last tick's overshoot
            osYaw   = -overshootYaw;
            osPitch = -overshootPitch;
            correcting = false;
        } else if (cleanAngle > 3f && cleanAngle < 20f && rng.nextFloat() < 0.08f) {
            // Overshoot this tick — server sees us go slightly past target
            overshootYaw   = (rng.nextFloat() - 0.5f) * 3.5f;
            overshootPitch = (rng.nextFloat() - 0.5f) * 2.0f;
            osYaw          = overshootYaw;
            osPitch        = overshootPitch;
            correcting     = true;
        }

        float targetYaw   = ideal[0] + jitterYaw + osYaw;
        float targetPitch = MathHelper.clamp(ideal[1] + jitterPitch + osPitch, -90f, 90f);

        // ── Distance-adaptive step size ───────────────────────────────────────
        // Constant step is one of the patterns Aim A looks for.
        // Far (>15°) → big sweep; Close (<5°) → fine micro-adjust.
        float stepYaw, stepPitch;
        if (cleanAngle > 15f) {
            stepYaw   = 7f + rng.nextFloat() * 5f;   // 7–12°/tick
            stepPitch = 5f + rng.nextFloat() * 4f;   // 5–9°/tick
        } else if (cleanAngle > 5f) {
            stepYaw   = 3f + rng.nextFloat() * 4f;   // 3–7°/tick
            stepPitch = 2f + rng.nextFloat() * 3f;   // 2–5°/tick
        } else {
            stepYaw   = 0.8f + rng.nextFloat() * 1.8f; // 0.8–2.6°/tick
            stepPitch = 0.6f + rng.nextFloat() * 1.2f; // 0.6–1.8°/tick
        }

        serverYaw   = smoothStep(prevSrvYaw,   targetYaw,   stepYaw);
        serverPitch = smoothStep(prevSrvPitch, targetPitch, stepPitch);

        // ── GCD quantization (mouse sensitivity) ─────────────────────────────
        // Skipping GCD entirely looks like packet injection. Applying it
        // perfectly every tick is also detectable. 15 % of ticks we add a
        // tiny sub-GCD offset — real mouse hardware has sub-step noise.
        float gcd    = getGcd(mc);
        float dYaw   = Math.round((serverYaw   - prevSrvYaw)   / gcd) * gcd;
        float dPitch = Math.round((serverPitch - prevSrvPitch) / gcd) * gcd;
        if (rng.nextFloat() < 0.15f) {
            dYaw   += (rng.nextFloat() - 0.5f) * gcd * 0.35f;
            dPitch += (rng.nextFloat() - 0.5f) * gcd * 0.35f;
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

        // ── Attack gate ───────────────────────────────────────────────────────
        // Vulcan Hitbox A: max-angle 0.42°. We gate on 0.40° (slight margin).
        // We check the ACTUAL sent rotation (serverYaw/serverPitch after jitter
        // + GCD) because that is exactly what Vulcan compares against the target
        // position when the attack packet arrives.
        // At ±0.35° jitter: ~30 % of attacks exceed 0.40° → safe given the
        // buffer decay math (decay 0.125/tick × 7 ticks = 0.875 recovery/cycle).
        float sentAngle = angleFromRot(serverYaw, serverPitch, mc.player, target);
        if (sentAngle > 0.40f) return;

        attack(mc, target);
        hitDelay = 5 + rng.nextInt(6); // 250–550 ms between hits (2–4 CPS)
    }

    // ── Normal attack ────────────────────────────────────────────────────────

    private void attack(MinecraftClient mc, LivingEntity target) {
        // No crits in legit mode — Vulcan verifies Y-velocity on critical hits
        if (!legit.getValue() && crits.getValue()) {
            Criticals crit = ModuleManager.get(Criticals.class);
            if (crit != null && crit.isEnabled()) Criticals.sendCritPackets(mc);
        }
        boolean wasSprinting = mc.player.isSprinting();
        mc.player.setSprinting(false);
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.setSprinting(wasSprinting);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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

    /** Angle in degrees between look direction and direction to target. */
    private static float angleFromRot(float yaw, float pitch,
                                      PlayerEntity player, LivingEntity target) {
        double yr  = Math.toRadians(yaw);
        double pr  = Math.toRadians(pitch);
        Vec3d look = new Vec3d(
            -Math.sin(yr) * Math.cos(pr),
            -Math.sin(pr),
             Math.cos(yr) * Math.cos(pr)
        ).normalize();
        Vec3d eye = new Vec3d(
            player.getX(),
            player.getY() + player.getEyeHeight(player.getPose()),
            player.getZ()
        );
        Vec3d toTarget = new Vec3d(
            target.getX(),
            target.getY() + target.getHeight() * 0.5,
            target.getZ()
        ).subtract(eye).normalize();
        double dot = MathHelper.clamp(look.dotProduct(toTarget), -1.0, 1.0);
        return (float) Math.toDegrees(Math.acos(dot));
    }

    /**
     * GCD derived from mouse sensitivity — the minimum rotation delta
     * the client can produce. Used to quantize our rotations like a real mouse.
     */
    private static float getGcd(MinecraftClient mc) {
        double sens = mc.options.getMouseSensitivity().getValue();
        double f    = sens * 0.6 + 0.2;
        return (float) (f * f * f * 1.2);
    }
}
