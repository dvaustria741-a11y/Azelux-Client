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
    private final BooleanSetting teamCheck = register(new BooleanSetting("Team Check",       false));

    public AimAssist() {
        super("AimAssist", "Smoothly aims at the nearest entity.", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        if (onAttack.getValue() && client.player.getAttackCooldownProgress(0f) < 0.85f) return;

        double r = range.getValue();
        Box box = client.player.getBoundingBox().expand(r);

        List<LivingEntity> targets = client.world.getEntitiesByClass(
                LivingEntity.class, box,
                e -> e != client.player
                        && !e.isDead()
                        && isValidTarget(client, e)
                        && e.squaredDistanceTo(client.player) <= r * r
                        && !isTeammate(client, e)
        );

        targets.stream()
                .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(client.player)))
                .ifPresent(t -> aimAt(client, t));
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

    private void aimAt(MinecraftClient client, LivingEntity target) {
        Vec3d eyes   = client.player.getEyePos();
        Vec3d center = new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ());
        Vec3d d      = center.subtract(eyes);

        double horizDist   = Math.sqrt(d.x * d.x + d.z * d.z);
        float  targetYaw   = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float  targetPitch = (float) Math.toDegrees(-Math.atan2(d.y, horizDist));

        float curYaw   = client.player.getYaw();
        float curPitch = client.player.getPitch();
        float speed    = (float) (smooth.getValue() / 10.0);

        client.player.setYaw  (lerpAngle(curYaw,   targetYaw,   speed));
        client.player.setPitch(MathHelper.clamp(lerpAngle(curPitch, targetPitch, speed), -90f, 90f));
    }

    private static float lerpAngle(float from, float to, float t) {
        return from + MathHelper.wrapDegrees(to - from) * t;
    }
}
