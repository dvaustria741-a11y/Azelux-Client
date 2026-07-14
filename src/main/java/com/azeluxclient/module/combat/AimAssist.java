package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.BooleanSetting;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

public class AimAssist extends Module {

    private final SliderSetting  range     = register(new SliderSetting ("Range",           3.5,  1.0, 6.0));
    private final SliderSetting  smooth    = register(new SliderSetting ("Smooth",           5.0,  1.0, 15.0));
    private final BooleanSetting players   = register(new BooleanSetting("Players",          true));
    private final BooleanSetting mobs      = register(new BooleanSetting("Mobs",             true));
    private final BooleanSetting onAttack  = register(new BooleanSetting("Only On Attack",   true));
    private final BooleanSetting teamCheck      = register(new BooleanSetting("Team Check",       false));
    // When OFF: snaps instantly to target (useful for testing aim reach/hitbox).
    // When ON : smoothly interpolates at the speed set by Smooth slider.
    private final BooleanSetting interpolation  = register(new BooleanSetting("Interpolation",   true));
    // Target must be within this angle of where you're already looking.
    // Beyond this cone AimAssist stops pulling so you can look away freely.
    private final SliderSetting  fov            = register(new SliderSetting ("FOV",             90.0, 5.0, 180.0));

    public AimAssist() {
        super("AimAssist", "Smoothly aims at the nearest entity.", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        // onAttack gate: only applies in snap mode (interpolation OFF).
        // In smooth mode aim runs every tick; blocking it to 1-2 ticks/attack
        // makes 10-20 % movement invisible — that's why smooth appeared 'not working'.
        if (!interpolation.getValue() && onAttack.getValue()
                && client.player.getAttackCooldownProgress(0f) < 0.85f) return;

        double r = range.getValue();
        Box box = client.player.getBoundingBox().expand(r);

        List<LivingEntity> targets = client.world.getEntitiesByClass(
                LivingEntity.class, box,
                e -> e != client.player
                        && !e.isDead()
                        && isValidTarget(client, e)
                        && e.squaredDistanceTo(client.player) <= r * r
                        && !isTeammate(client, e)
                        && !isBehind(client, e)
                        && !isTooFarBelow(client, e)
        );

        targets.stream()
                .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(client.player)))
                .ifPresent(t -> {
                    // FOV gate: only assist if target is within the configured cone.
                    // When the player deliberately looks away (> FOV/2 degrees off)
                    // AimAssist pauses so they can retreat, eat, or look around freely.
                    // FOV gate: yaw-only, same axis as isBehind.
                    // Old code checked pitch too (AND logic) — a target at eye level
                    // with player tilted slightly blocked the assist entirely.
                    double dx2    = t.getX() - client.player.getX();
                    double dz2    = t.getZ() - client.player.getZ();
                    float tYaw2   = (float) Math.toDegrees(Math.atan2(-dx2, dz2));
                    float dYaw2   = Math.abs(MathHelper.wrapDegrees(tYaw2 - client.player.getYaw()));
                    if (dYaw2 <= fov.getValue() / 2.0) aimAt(client, t);
                });
    }

    private boolean isValidTarget(MinecraftClient client, LivingEntity e) {
        if (e instanceof PlayerEntity p) {
            return players.getValue() && !p.isCreative();
        }
        if (e instanceof MobEntity) {
            return mobs.getValue();
        }
        return false;
    }

    private boolean isTeammate(MinecraftClient client, LivingEntity entity) {
        if (!teamCheck.getValue()) return false;
        AbstractTeam myTeam = client.player.getScoreboardTeam();
        if (myTeam == null) return false;
        AbstractTeam theirTeam = entity.getScoreboardTeam();
        return theirTeam != null && theirTeam.getName().equals(myTeam.getName());
    }

    /** Returns true when the entity is more than 90 degrees behind the player's look direction. */
    private boolean isBehind(MinecraftClient client, LivingEntity entity) {
        // Use getX/getZ instead of getPos() - getPos() does not exist in Yarn 1.21.11
        double dx = entity.getX() - client.player.getX();
        double dz = entity.getZ() - client.player.getZ();
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return Math.abs(MathHelper.wrapDegrees(targetYaw - client.player.getYaw())) > 90f;
    }

    /**
     * Returns true when the target's centre is more than 0.8 blocks below the player's feet.
     * Prevents the aim assist from locking onto entities that have fallen into a pit below you.
     */
    private boolean isTooFarBelow(MinecraftClient client, LivingEntity entity) {
        double targetCenterY = entity.getY() + entity.getHeight() * 0.5;
        return client.player.getY() - targetCenterY > 0.8;
    }

    private void aimAt(MinecraftClient client, LivingEntity target) {
        Vec3d eyes   = client.player.getEyePos();
        Vec3d center = new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ());
        Vec3d d      = center.subtract(eyes);

        double horizDist   = Math.sqrt(d.x * d.x + d.z * d.z);
        float  targetYaw   = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float  targetPitch = (float) Math.toDegrees(-Math.atan2(d.y, horizDist));

        float curYaw   = client.player.getYaw();
        float curPitch = client.player.getPitch();

        // Compute the rotation delta we want to apply this tick
        float dYaw, dPitch;
        if (interpolation.getValue()) {
            float speed = (float)(smooth.getValue() / 20.0);
            dYaw   = MathHelper.wrapDegrees(lerpAngle(curYaw,   targetYaw,   speed) - curYaw);
            dPitch = lerpAngle(curPitch, targetPitch, speed) - curPitch;
        } else {
            dYaw   = MathHelper.wrapDegrees(targetYaw - curYaw);
            dPitch = MathHelper.clamp(targetPitch - curPitch, -90f - curPitch, 90f - curPitch);
        }

        // GCD quantization — snap delta to the minimum mouse step for the
        // current sensitivity. setYaw/setPitch bypassed this and produced
        // sub-GCD precision: a pattern Vulcan's Aim checks flag immediately.
        float gcd = getGcd(client);
        // Dead-zone: only round to the nearest GCD step when delta >= gcd/2.
        // Without this, sub-GCD deltas round to a full step → overshoot →
        // next tick rounds back → oscillation (the up/down drift bug).
        dYaw   = (Math.abs(dYaw)   >= gcd * 0.5f) ? Math.round(dYaw   / gcd) * gcd : 0f;
        dPitch = (Math.abs(dPitch) >= gcd * 0.5f) ? Math.round(dPitch / gcd) * gcd : 0f;
        if (dYaw == 0f && dPitch == 0f) return;

        // Apply via changeLookDirection — the identical code path used by
        // Mouse.updateMouse() for real hardware input. changeLookDirection
        // multiplies its args by 0.15 internally, so we divide by 0.15 to
        // produce exactly `dYaw` degrees of rotation.
        // Pitch sign: negative rawDY = pitch decreases = look up.
        client.player.changeLookDirection(dYaw / 0.15, -dPitch / 0.15);
    }

    /** Minimum rotation step a real mouse can produce at this sensitivity. */
    private static float getGcd(MinecraftClient mc) {
        double s = mc.options.getMouseSensitivity().getValue();
        double f = s * 0.6 + 0.2;
        return (float)(f * f * f * 1.2);
    }

    private static float lerpAngle(float from, float to, float t) {
        return from + MathHelper.wrapDegrees(to - from) * t;
    }
}





