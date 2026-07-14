package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import com.azeluxclient.module.ModuleManager;
import com.azeluxclient.setting.BooleanSetting;
import com.azeluxclient.setting.EnumSetting;
import com.azeluxclient.setting.SliderSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class KillAura extends Module {

    // ── Targeting ─────────────────────────────────────────────────────────────
    private final SliderSetting  scanRange  = register(new SliderSetting ("Scan Range",  6.0,  2.0, 10.0));
    private final SliderSetting  range      = register(new SliderSetting ("Range",       3.0,  2.0,  6.0));
    private final SliderSetting  wallRange  = register(new SliderSetting ("Wall Range",  0.0,  0.0,  6.0));
    private final BooleanSetting players    = register(new BooleanSetting("Players",     true));
    private final BooleanSetting mobs       = register(new BooleanSetting("Mobs",        true));
    private final BooleanSetting teamCheck  = register(new BooleanSetting("Team Check",  true));
    private final BooleanSetting weaponOnly = register(new BooleanSetting("Weapon Only", false));
    private final EnumSetting<RaycastMode> raycast =
        register(new EnumSetting<>("Raycast", RaycastMode.ENEMY));

    // ── CPS — default 4-6 (human range, won't trigger Vulcan timing checks) ──
    private final SliderSetting minCPS = register(new SliderSetting("Min CPS",  4.0, 1.0, 20.0));
    private final SliderSetting maxCPS = register(new SliderSetting("Max CPS",  6.0, 1.0, 20.0));

    // ── Combat ────────────────────────────────────────────────────────────────
    private final BooleanSetting crits      = register(new BooleanSetting("Criticals",   true));
    private final BooleanSetting autoBlock  = register(new BooleanSetting("Auto Block",  false));
    private final BooleanSetting keepSprint = register(new BooleanSetting("Keep Sprint", true));
    private final BooleanSetting legit      = register(new BooleanSetting("Legit Mode",  false));

    public enum RaycastMode { NONE, ENEMY, ALL }

    private int     clickTimer    = 0;
    private int     nextClickTick = 1;
    private boolean isBlocking    = false;

    // ── Legit (silent-aim) state ──────────────────────────────────────────────
    private float   serverYaw, serverPitch;
    private float   prevSrvYaw, prevSrvPitch;
    private boolean srvRotReady   = false;
    private float   savedCamYaw, savedCamPitch;
    private boolean camOverridden = false;
    private int     lockOnTicks   = 0;

    private final Random rng      = new Random();
    private float jitterYaw       = 0f;
    private float jitterPitch     = 0f;
    private int   jitterCooldown  = 0;
    private int   idleTicks       = 0;

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
        clickTimer    = 0;
        srvRotReady   = false;
        camOverridden = false;
        isBlocking    = false;
        lockOnTicks   = 0;
        scheduleNextClick();
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = mc();
        if (mc != null && mc.player != null) {
            if (camOverridden) {
                mc.player.setYaw(savedCamYaw);
                mc.player.setPitch(savedCamPitch);
            }
            if (isBlocking) stopBlocking(mc);
        }
        srvRotReady   = false;
        camOverridden = false;
        isBlocking    = false;
        lockOnTicks   = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        // Restore real camera after packets sent
        if (legit.getValue() && camOverridden) {
            mc.player.setYaw(savedCamYaw);
            mc.player.setPitch(savedCamPitch);
            camOverridden = false;
        }

        // ── MUST be fully charged — this was missing, causing rapid attacks ──
        if (mc.player.getAttackCooldownProgress(0f) < 1.0f) {
            if (autoBlock.getValue()) startBlocking(mc);
            return;
        }

        if (weaponOnly.getValue() && !isHoldingWeapon(mc)) {
            stopBlockingIfNeeded(mc); return;
        }

        Box scanBox = mc.player.getBoundingBox().expand(scanRange.getValue());
        List<LivingEntity> candidates = mc.world.getEntitiesByClass(
            LivingEntity.class, scanBox, e -> isValidTarget(mc, e));

        LivingEntity target = candidates.stream()
            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.player)))
            .orElse(null);

        if (target == null) { lockOnTicks = 0; stopBlockingIfNeeded(mc); return; }

        double dist        = Math.sqrt(target.squaredDistanceTo(mc.player));
        boolean inRange    = dist <= range.getValue() && hasLineOfSight(mc, target);
        boolean inWallRange = wallRange.getValue() > 0 && dist <= wallRange.getValue();

        if (!inRange && !inWallRange) {
            if (autoBlock.getValue()) startBlocking(mc); return;
        }

        if (raycast.getValue() != RaycastMode.NONE) {
            LivingEntity ch = findCrosshairTarget(mc);
            if (ch != null && isValidTarget(mc, ch)) target = ch;
        }

        clickTimer++;
        if (clickTimer < nextClickTick) {
            if (autoBlock.getValue()) startBlocking(mc); return;
        }
        scheduleNextClick();

        stopBlockingIfNeeded(mc);
        if (legit.getValue()) handleLegit(mc, target);
        else                  attack(mc, target);
    }

    // ── CPS scheduling ────────────────────────────────────────────────────────
    private void scheduleNextClick() {
        clickTimer = 0;
        double lo  = Math.min(minCPS.getValue(), maxCPS.getValue());
        double hi  = Math.max(minCPS.getValue(), maxCPS.getValue());
        double cps = lo + rng.nextDouble() * (hi - lo);
        nextClickTick = Math.max(1, (int) Math.round(20.0 / cps));
    }

    // ── Legit / Silent-Aim ────────────────────────────────────────────────────
    private void handleLegit(MinecraftClient mc, LivingEntity target) {
        if (!srvRotReady) {
            serverYaw    = savedCamYaw;
            serverPitch  = savedCamPitch;
            prevSrvYaw   = serverYaw;
            prevSrvPitch = serverPitch;
            srvRotReady  = true;
        }

        float[] ideal = getRotationsTo(mc.player, target);

        // Idle ticks: hold rotation still occasionally (human micro-pause)
        if (idleTicks > 0) {
            idleTicks--;
            lockOnTicks++;
            return;
        }
        float angleDist = angleFromRot(serverYaw, serverPitch, mc.player, target);
        if (angleDist < 10f && rng.nextFloat() < 0.10f) {
            idleTicks = 1 + rng.nextInt(3);
        }

        // Jitter refresh every 12-20 ticks
        if (--jitterCooldown <= 0) {
            jitterYaw      = (rng.nextFloat() - 0.5f) * 3.0f;
            jitterPitch    = (rng.nextFloat() - 0.5f) * 1.8f;
            jitterCooldown = 12 + rng.nextInt(9);
        }
        ideal[0] += jitterYaw;
        ideal[1]  = MathHelper.clamp(ideal[1] + jitterPitch, -90f, 90f);

        // Variable step size (4-10 deg/tick) — constant speed is detectable
        float stepYaw   = 4f + rng.nextFloat() * 6f;
        float stepPitch = 3f + rng.nextFloat() * 5f;

        serverYaw   = smoothStep(prevSrvYaw,   ideal[0], stepYaw);
        serverPitch = smoothStep(prevSrvPitch, ideal[1], stepPitch);

        // GCD fix with slight imperfection (15% of ticks)
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
        if (lockOnTicks < 5) return; // warm-up: track before first hit

        // ── Attack only when crosshair is within 1.5° of target ──────────────
        // (Vulcan Hitbox A: max-angle 0.42 — we use 1.5 for margin)
        if (angleFromRot(serverYaw, serverPitch, mc.player, target) > 1.5f) return;

        attack(mc, target);
    }

    // ── Attack ────────────────────────────────────────────────────────────────
    private void attack(MinecraftClient mc, LivingEntity target) {
        if (!legit.getValue() && crits.getValue()) {
            Criticals crit = ModuleManager.get(Criticals.class);
            if (crit != null && crit.isEnabled()) Criticals.sendCritPackets(mc);
        }
        boolean wasSprinting = mc.player.isSprinting();
        mc.player.setSprinting(false);
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (keepSprint.getValue()) mc.player.setSprinting(wasSprinting);
    }

    // ── LoS / Raycast ─────────────────────────────────────────────────────────
    private boolean hasLineOfSight(MinecraftClient mc, LivingEntity target) {
        Vec3d eye = new Vec3d(mc.player.getX(),
            mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ());
        Vec3d tgt = new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ());
        HitResult hit = mc.world.raycast(new RaycastContext(eye, tgt,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS;
    }

    private LivingEntity findCrosshairTarget(MinecraftClient mc) {
        Vec3d eye  = new Vec3d(mc.player.getX(),
            mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ());
        Vec3d look = mc.player.getRotationVec(1f);
        Vec3d end  = eye.add(look.multiply(range.getValue()));
        LivingEntity best = null; double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : mc.world.getEntitiesByClass(LivingEntity.class,
                mc.player.getBoundingBox().expand(range.getValue()), en -> isValidTarget(mc, en))) {
            if (raycast.getValue() == RaycastMode.ENEMY && !(e instanceof PlayerEntity)) continue;
            var hit = e.getBoundingBox().expand(0.3).raycast(eye, end);
            if (hit.isPresent()) {
                double d = eye.squaredDistanceTo(hit.get());
                if (d < bestDist) { bestDist = d; best = e; }
            }
        }
        return best;
    }

    // ── Validation ────────────────────────────────────────────────────────────
    private boolean isValidTarget(MinecraftClient mc, LivingEntity e) {
        if (e == mc.player || !e.isAlive()) return false;
        if (e instanceof PlayerEntity p) {
            if (!players.getValue()) return false;
            if (p.isCreative() || p.isSpectator()) return false;
            if (teamCheck.getValue() && isTeammate(mc.player, p)) return false;
        } else { if (!mobs.getValue()) return false; }
        return true;
    }

    private static boolean isTeammate(PlayerEntity me, PlayerEntity other) {
        var a = me.getScoreboardTeam(); var b = other.getScoreboardTeam();
        return a != null && a == b;
    }
    private static boolean isHoldingWeapon(MinecraftClient mc) {
        ItemStack s = mc.player.getMainHandStack();
        return s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES);
    }

    // ── AutoBlock ─────────────────────────────────────────────────────────────
    private void startBlocking(MinecraftClient mc) {
        if (isBlocking || !(mc.player.getOffHandStack().getItem() instanceof ShieldItem)
                || mc.player.isUsingItem()) return;
        mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
        isBlocking = true;
    }
    private void stopBlocking(MinecraftClient mc) {
        if (!isBlocking) return;
        if (mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.OFF_HAND)
            mc.player.stopUsingItem();
        isBlocking = false;
    }
    private void stopBlockingIfNeeded(MinecraftClient mc) { if (autoBlock.getValue()) stopBlocking(mc); }

    // ── Rotation math ─────────────────────────────────────────────────────────
    private static float[] getRotationsTo(PlayerEntity player, LivingEntity target) {
        double dx = target.getX() - player.getX();
        double dy = (target.getY() + target.getHeight() * 0.5)
                  - (player.getY() + player.getEyeHeight(player.getPose()));
        double dz = target.getZ() - player.getZ();
        double h  = Math.sqrt(dx * dx + dz * dz);
        return new float[]{
            (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f,
            MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, h)), -90f, 90f)
        };
    }
    private static float smoothStep(float cur, float tgt, float max) {
        return cur + MathHelper.clamp(MathHelper.wrapDegrees(tgt - cur), -max, max);
    }
    private static float angleFromRot(float yaw, float pitch, PlayerEntity p, LivingEntity t) {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        Vec3d look = new Vec3d(-Math.sin(yr)*Math.cos(pr), -Math.sin(pr), Math.cos(yr)*Math.cos(pr)).normalize();
        Vec3d eye  = new Vec3d(p.getX(), p.getY()+p.getEyeHeight(p.getPose()), p.getZ());
        Vec3d dir  = new Vec3d(t.getX(), t.getY()+t.getHeight()*0.5, t.getZ()).subtract(eye).normalize();
        return (float) Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(dir), -1.0, 1.0)));
    }
    private static float getGcd(MinecraftClient mc) {
        double f = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        return (float)(f * f * f * 1.2);
    }
}
