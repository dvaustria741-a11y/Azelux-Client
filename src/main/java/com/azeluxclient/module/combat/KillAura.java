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

public class KillAura extends Module {

    // Settings
    private final SliderSetting  range   = register(new SliderSetting ("Range",       4.0, 2.0, 6.0));
    private final BooleanSetting players = register(new BooleanSetting("Players",     true));
    private final BooleanSetting mobs    = register(new BooleanSetting("Mobs",        true));
    private final BooleanSetting crits   = register(new BooleanSetting("Criticals",   true));
    private final BooleanSetting legit   = register(new BooleanSetting("Legit Mode",  false));

    // Legit mode state
    private final Random rng      = new Random();
    private int   skipTicks       = 0;
    private float jitterYaw       = 0f;
    private float jitterPitch     = 0f;
    private int   jitterCooldown  = 0;

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities.", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        skipTicks      = 0;
        jitterYaw      = 0f;
        jitterPitch    = 0f;
        jitterCooldown = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.getAttackCooldownProgress(0f) < 0.9f) return;

        // Legit mode hard-caps range at 3 blocks
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

    // Legit mode handler
    private void handleLegit(MinecraftClient mc, LivingEntity target) {
        // 1. Ideal rotations toward target centre mass
        float[] ideal = getRotationsTo(mc.player, target);

        // 2. Refresh jitter every 12-18 ticks so aim is not perfectly steady
        if (--jitterCooldown <= 0) {
            jitterYaw      = (rng.nextFloat() - 0.5f) * 3.5f;
            jitterPitch    = (rng.nextFloat() - 0.5f) * 2.0f;
            jitterCooldown = 12 + rng.nextInt(7);
        }
        ideal[0] += jitterYaw;
        ideal[1]  = MathHelper.clamp(ideal[1] + jitterPitch, -90f, 90f);

        // 3. Smooth step toward ideal - max 16 deg yaw, 13 deg pitch per tick
        float newYaw   = smoothStep(mc.player.getYaw(),   ideal[0], 16f);
        float newPitch = smoothStep(mc.player.getPitch(), ideal[1], 13f);

        // 4. GCD fix - round rotation deltas to match real mouse sensitivity granularity
        //    so anticheat packet analysis cannot distinguish this from real mouse input
        float gcd    = getGcd(mc);
        float dYaw   = Math.round((newYaw   - mc.player.getYaw())   / gcd) * gcd;
        float dPitch = Math.round((newPitch - mc.player.getPitch()) / gcd) * gcd;
        newYaw   = mc.player.getYaw()   + dYaw;
        newPitch = mc.player.getPitch() + dPitch;

        // 5. Apply rotation - drives both camera (1st person) and body model (3rd person)
        mc.player.setYaw(newYaw);
        mc.player.setHeadYaw(newYaw);
        mc.player.setPitch(newPitch);

        // 6. Only attack when we are actually aimed at target (within 35 degrees)
        if (angleToTarget(mc.player, target) > 35f) return;

        // 7. Random 0-2 tick delay between hits to avoid machine-gun detection
        if (skipTicks > 0) { skipTicks--; return; }

        attack(mc, target);
        skipTicks = rng.nextInt(3);
    }

    // Shared helpers
    private void attack(MinecraftClient mc, LivingEntity target) {
        Criticals crit = ModuleManager.get(Criticals.class);
        if (crit != null && crit.isEnabled() && crits.getValue()) {
            Criticals.sendCritPackets(mc);
        }
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
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

    private static float angleToTarget(PlayerEntity player, LivingEntity target) {
        Vec3d look = player.getRotationVec(1f);
        Vec3d to   = target.getPos()
                           .add(0, target.getHeight() * 0.5, 0)
                           .subtract(player.getEyePos())
                           .normalize();
        double dot = MathHelper.clamp(look.dotProduct(to), -1.0, 1.0);
        return (float) Math.toDegrees(Math.acos(dot));
    }

    // GCD derived from mouse sensitivity - real mouse input is always a multiple of this
    private static float getGcd(MinecraftClient mc) {
        double sens = mc.options.getMouseSensitivity().getValue();
        double f    = sens * 0.6 + 0.2;
        return (float) (f * f * f * 1.2);
    }
}
