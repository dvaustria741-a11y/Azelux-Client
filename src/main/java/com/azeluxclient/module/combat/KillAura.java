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
 * Silent Aim tick order (critical for Vulcan bypass):
 *
 *   START_CLIENT_TICK  → save real camera, apply serverYaw to player entity
 *   sendMovementPackets (vanilla) → packets carry serverYaw to server  ✓
 *   END_CLIENT_TICK / onTick → restore real camera, compute next serverYaw,
 *                              set head+body to serverYaw, fire attack
 *   Rendering           → player.getYaw() = real camera (no visible movement) ✓
 *                         headYaw / bodyYaw = serverYaw (body faces target)   ✓
 *
 * This way the server always receives the correct facing direction in its
 * movement packets, so Vulcan's rotation-vs-attack check passes cleanly.
 */
public class KillAura extends Module {

    // ── Settings ──────────────────────────────────────────────────────────────
    private final SliderSetting  range   = register(new SliderSetting ("Range",      4.0, 2.0, 6.0));
    private final BooleanSetting players = register(new BooleanSetting("Players",    true));
    private final BooleanSetting mobs    = register(new BooleanSetting("Mobs",       true));
    private final BooleanSetting crits   = register(new BooleanSetting("Criticals",  true));
    private final BooleanSetting legit   = register(new BooleanSetting("Legit Mode", false));

    // ── Silent-aim rotation state ─────────────────────────────────────────────
    private float   serverYaw, serverPitch;   // what the server / others see
    private float   prevSrvYaw, prevSrvPitch;
    private boolean srvRotReady = false;

    // ── Camera save/restore across START → END tick boundary ─────────────────
    private float   savedCamYaw, savedCamPitch;
    private boolean camOverridden = false;

    // ── Jitter & hit-delay ────────────────────────────────────────────────────
    private final Random rng     = new Random();
    private int   skipTicks      = 0;
    private float jitterYaw      = 0f;
    private float jitterPitch    = 0f;
    private int   jitterCooldown = 0;

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities.", Category.COMBAT);

        /*
         * START_CLIENT_TICK fires BEFORE vanilla sendMovementPackets().
         * We apply serverYaw here so the look packet the game sends this tick
         * already carries the target-facing direction. We save the real camera
         * first so we can restore it in onTick (END) before rendering.
         */
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (!isEnabled() || !legit.getValue()) return;
            if (client.player == null || !srvRotReady) return;

            // Capture real camera before overriding
            savedCamYaw   = client.player.getYaw();
            savedCamPitch = client.player.getPitch();
            camOverridden = true;

            // Apply server rotation — picked up by sendMovementPackets this tick
            client.player.setYaw(serverYaw);
            client.player.setPitch(serverPitch);

            // Head + body face target (visible to others and in your 3rd person)
            client.player.setHeadYaw(serverYaw);
            client.player.bodyYaw = serverYaw;
        });
    }

    @Override
    public void onEnable() {
        srvRotReady   = false;
        camOverridden = false;
    }

    @Override
    public void onDisable() {
        // Restore camera in case we disabled mid-tick
        MinecraftClient mc = mc();
        if (mc != null && mc.player != null && camOverridden) {
            mc.player.setYaw(savedCamYaw);
            mc.player.setPitch(savedCamPitch);
        }
        srvRotReady    = false;
        camOverridden  = false;
        skipTicks      = 0;
        jitterYaw      = 0f;
        jitterPitch    = 0f;
        jitterCooldown = 0;
    }

    // ── Main tick (fires at END — after sendMovementPackets) ──────────────────

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        // Restore real camera FIRST, before rendering picks it up.
        // sendMovementPackets already ran this tick with serverYaw — camera is safe to restore.
        if (legit.getValue() && camOverridden) {
            mc.player.setYaw(savedCamYaw);
            mc.player.setPitch(savedCamPitch);
            camOverridden = false;
        }

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

        // Only ever touch the single nearest target
        targets.stream()
            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.player)))
            .ifPresent(target -> {
                if (legit.getValue()) handleLegit(mc, target);
                else                  attack(mc, target);
            });
    }

    // ── Legit / Silent-Aim handler ────────────────────────────────────────────

    private void handleLegit(MinecraftClient mc, LivingEntity target) {
        // Seed server rotation from real camera on first use
        if (!srvRotReady) {
            serverYaw    = savedCamYaw;
            serverPitch  = savedCamPitch;
            prevSrvYaw   = serverYaw;
            prevSrvPitch = serverPitch;
            srvRotReady  = true;
        }

        // 1. Ideal rotation toward target centre
        float[] ideal = getRotationsTo(mc.player, target);

        // 2. Random jitter — refreshed every 12-18 ticks
        if (--jitterCooldown <= 0) {
            jitterYaw      = (rng.nextFloat() - 0.5f) * 3.5f;
            jitterPitch    = (rng.nextFloat() - 0.5f) * 2.0f;
            jitterCooldown = 12 + rng.nextInt(7);
        }
        ideal[0] += jitterYaw;
        ideal[1]  = MathHelper.clamp(ideal[1] + jitterPitch, -90f, 90f);

        // 3. Smooth step — max 16 deg yaw / 13 deg pitch per tick
        serverYaw   = smoothStep(prevSrvYaw,   ideal[0], 16f);
        serverPitch = smoothStep(prevSrvPitch, ideal[1], 13f);

        // 4. GCD fix — delta must match mouse-sensitivity granularity
        float gcd    = getGcd(mc);
        float dYaw   = Math.round((serverYaw   - prevSrvYaw)   / gcd) * gcd;
        float dPitch = Math.round((serverPitch - prevSrvPitch) / gcd) * gcd;
        serverYaw    = prevSrvYaw   + dYaw;
        serverPitch  = prevSrvPitch + dPitch;
        prevSrvYaw   = serverYaw;
        prevSrvPitch = serverPitch;

        // 5. Push head + body visual for this tick (START already set it,
        //    but we update to the new value so it reflects the latest position)
        mc.player.setHeadYaw(serverYaw);
        mc.player.bodyYaw = serverYaw;

        // 6. Only attack when server rotation is close enough to target
        if (skipTicks > 0) { skipTicks--; return; }
        if (angleFromRot(serverYaw, serverPitch, mc.player, target) > 35f) return;

        attack(mc, target);
        skipTicks = rng.nextInt(3);
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private void attack(MinecraftClient mc, LivingEntity target) {
        Criticals crit = ModuleManager.get(Criticals.class);
        if (crit != null && crit.isEnabled() && crits.getValue()) {
            Criticals.sendCritPackets(mc);
        }

        // Prevent sword sweep AoE from hitting a second nearby entity
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
        Vec3d tgtCenter = new Vec3d(
            target.getX(),
            target.getY() + target.getHeight() * 0.5,
            target.getZ()
        );
        Vec3d eyePos = new Vec3d(
            player.getX(),
            player.getY() + player.getEyeHeight(player.getPose()),
            player.getZ()
        );
        double dot = MathHelper.clamp(look.dotProduct(tgtCenter.subtract(eyePos).normalize()), -1.0, 1.0);
        return (float) Math.toDegrees(Math.acos(dot));
    }

    private static float getGcd(MinecraftClient mc) {
        double sens = mc.options.getMouseSensitivity().getValue();
        double f    = sens * 0.6 + 0.2;
        return (float) (f * f * f * 1.2);
    }
}
