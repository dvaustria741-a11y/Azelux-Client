package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import com.azeluxclient.module.ModuleManager;
import com.azeluxclient.setting.BooleanSetting;
import com.azeluxclient.setting.SliderSetting;
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
 * KillAura with optional Legit (Silent Aim) mode.
 *
 * Silent Aim design:
 *   - Your camera (what YOU see in 1st person) never moves.
 *   - A separate "server rotation" smoothly tracks the target.
 *   - setHeadYaw(serverYaw) makes your body/head visibly face the target
 *     (visible to the target in their 3rd-person or in your own 3rd-person).
 *   - The attack is fired while your entity temporarily holds the server
 *     rotation so the server's hit-validation passes, then the camera is
 *     restored before the frame is rendered — so you never see it move.
 *   - GCD fix, smooth rotation, random jitter and hit-delay are all kept
 *     for anti-cheat resistance.
 */
public class KillAura extends Module {

    // ── Settings ──────────────────────────────────────────────────────────────
    private final SliderSetting  range   = register(new SliderSetting ("Range",      4.0, 2.0, 6.0));
    private final BooleanSetting players = register(new BooleanSetting("Players",    true));
    private final BooleanSetting mobs    = register(new BooleanSetting("Mobs",       true));
    private final BooleanSetting crits   = register(new BooleanSetting("Criticals",  true));
    private final BooleanSetting legit   = register(new BooleanSetting("Legit Mode", false));

    // ── Silent-aim state ──────────────────────────────────────────────────────
    // These track the rotation the SERVER / others see.
    // They are completely separate from mc.player.getYaw()/getPitch() (the camera).
    private float serverYaw, serverPitch;
    private float prevSrvYaw, prevSrvPitch;
    private boolean srvRotReady = false;

    // Jitter & hit-delay
    private final Random rng     = new Random();
    private int   skipTicks      = 0;
    private float jitterYaw      = 0f;
    private float jitterPitch    = 0f;
    private int   jitterCooldown = 0;

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        srvRotReady = false;   // will be seeded from camera on first legit tick
    }

    @Override
    public void onDisable() {
        srvRotReady    = false;
        skipTicks      = 0;
        jitterYaw      = 0f;
        jitterPitch    = 0f;
        jitterCooldown = 0;
    }

    // ── Main tick ─────────────────────────────────────────────────────────────

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.getAttackCooldownProgress(0f) < 0.9f) return;

        double effectiveRange = legit.getValue()
            ? Math.min(range.getValue(), 3.0)
            : range.getValue();

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
    }

    // ── Legit / Silent-Aim handler ────────────────────────────────────────────

    private void handleLegit(MinecraftClient mc, LivingEntity target) {
        // Seed server rotation from current camera on first use (smooth start)
        if (!srvRotReady) {
            serverYaw    = mc.player.getYaw();
            serverPitch  = mc.player.getPitch();
            prevSrvYaw   = serverYaw;
            prevSrvPitch = serverPitch;
            srvRotReady  = true;
        }

        // 1. Ideal rotation toward target centre
        float[] ideal = getRotationsTo(mc.player, target);

        // 2. Random jitter every 12-18 ticks
        if (--jitterCooldown <= 0) {
            jitterYaw      = (rng.nextFloat() - 0.5f) * 3.5f;
            jitterPitch    = (rng.nextFloat() - 0.5f) * 2.0f;
            jitterCooldown = 12 + rng.nextInt(7);
        }
        ideal[0] += jitterYaw;
        ideal[1]  = MathHelper.clamp(ideal[1] + jitterPitch, -90f, 90f);

        // 3. Smooth-step the SERVER rotation toward target (16 deg yaw, 13 deg pitch/tick)
        serverYaw   = smoothStep(prevSrvYaw,   ideal[0], 16f);
        serverPitch = smoothStep(prevSrvPitch, ideal[1], 13f);

        // 4. GCD fix on the server rotation delta
        float gcd    = getGcd(mc);
        float dYaw   = Math.round((serverYaw   - prevSrvYaw)   / gcd) * gcd;
        float dPitch = Math.round((serverPitch - prevSrvPitch) / gcd) * gcd;
        serverYaw    = prevSrvYaw   + dYaw;
        serverPitch  = prevSrvPitch + dPitch;
        prevSrvYaw   = serverYaw;
        prevSrvPitch = serverPitch;

        // 5. Make the body/head visually face the target every tick.
        //    The TARGET sees this. You also see it in 3rd person.
        //    Your camera (1st person) is NOT touched here.
        mc.player.setHeadYaw(serverYaw);

        // 6. Angle check: use SERVER rotation, not camera rotation
        if (skipTicks > 0) { skipTicks--; return; }
        if (angleFromRot(serverYaw, serverPitch, mc.player, target) > 35f) return;

        // 7. Silent attack:
        //    a) Save your real camera rotation
        //    b) Temporarily apply the server rotation so hit-validation passes
        //    c) Fire the attack
        //    d) IMMEDIATELY restore camera — the frame hasn't rendered yet,
        //       so you never see any camera movement in first person
        float camYaw   = mc.player.getYaw();
        float camPitch = mc.player.getPitch();

        mc.player.setYaw(serverYaw);
        mc.player.setPitch(serverPitch);
        attack(mc, target);
        mc.player.setYaw(camYaw);       // restore before render
        mc.player.setPitch(camPitch);

        skipTicks = rng.nextInt(3);
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private void attack(MinecraftClient mc, LivingEntity target) {
        Criticals crit = ModuleManager.get(Criticals.class);
        if (crit != null && crit.isEnabled() && crits.getValue()) {
            Criticals.sendCritPackets(mc);
        }
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    /** Yaw + pitch from player eye to target centre. */
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

    /** Smooth step current → target capped at maxStep degrees. */
    private static float smoothStep(float current, float target, float maxStep) {
        float diff = MathHelper.wrapDegrees(target - current);
        return current + MathHelper.clamp(diff, -maxStep, maxStep);
    }

    /**
     * Angle between an explicit (yaw, pitch) rotation and the direction
     * to the target. Used to check the SERVER rotation, not the camera.
     */
    private static float angleFromRot(float yaw, float pitch,
                                      PlayerEntity player, LivingEntity target) {
        double yr  = Math.toRadians(yaw);
        double pr  = Math.toRadians(pitch);
        Vec3d look = new Vec3d(
            -Math.sin(yr) * Math.cos(pr),
            -Math.sin(pr),
             Math.cos(yr) * Math.cos(pr)
        ).normalize();

        Vec3d targetCenter = new Vec3d(
            target.getX(),
            target.getY() + target.getHeight() * 0.5,
            target.getZ()
        );
        Vec3d eyePos = new Vec3d(
            player.getX(),
            player.getY() + player.getEyeHeight(player.getPose()),
            player.getZ()
        );
        Vec3d to  = targetCenter.subtract(eyePos).normalize();
        double dot = MathHelper.clamp(look.dotProduct(to), -1.0, 1.0);
        return (float) Math.toDegrees(Math.acos(dot));
    }

    /** GCD from mouse sensitivity — makes rotation deltas match real mouse input. */
    private static float getGcd(MinecraftClient mc) {
        double sens = mc.options.getMouseSensitivity().getValue();
        double f    = sens * 0.6 + 0.2;
        return (float) (f * f * f * 1.2);
    }
}
