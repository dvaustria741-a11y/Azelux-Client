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

/**
 * KillAura — ported from LiquidBounce with Azelux-Client framework.
 *
 * Features ported from LiquidBounce:
 *  - CPS Clicker    : randomised Min/Max CPS with tick-based scheduling
 *  - Scan Range     : wide acquisition radius; attack only within Range
 *  - Wall Range     : separate range for hitting through blocks
 *  - Raycast        : NONE / ENEMY / ALL crosshair-entity selection
 *  - AutoBlock      : hold off-hand shield between hits
 *  - KeepSprint     : restore sprint state after attack
 *  - TeamCheck      : never hit scoreboard-teammates
 *  - WeaponOnly     : require sword or axe in main hand
 *  - Legit Mode     : silent aim — START_CLIENT_TICK rotation trick,
 *                     camera never moves, body faces target, GCD fix
 */
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

    // ── CPS ───────────────────────────────────────────────────────────────────
    private final SliderSetting minCPS = register(new SliderSetting("Min CPS",  8.0, 1.0, 20.0));
    private final SliderSetting maxCPS = register(new SliderSetting("Max CPS", 12.0, 1.0, 20.0));

    // ── Combat ────────────────────────────────────────────────────────────────
    private final BooleanSetting crits      = register(new BooleanSetting("Criticals",   true));
    private final BooleanSetting autoBlock  = register(new BooleanSetting("Auto Block",  false));
    private final BooleanSetting keepSprint = register(new BooleanSetting("Keep Sprint", true));
    private final BooleanSetting legit      = register(new BooleanSetting("Legit Mode",  false));

    // ── Raycast enum (mirrors LiquidBounce RaycastMode) ──────────────────────
    public enum RaycastMode { NONE, ENEMY, ALL }

    // ── CPS state ─────────────────────────────────────────────────────────────
    private int clickTimer    = 0;
    private int nextClickTick = 1;

    // ── AutoBlock state ───────────────────────────────────────────────────────
    private boolean isBlocking = false;

    // ── Silent-aim (Legit Mode) state ─────────────────────────────────────────
    private float   serverYaw, serverPitch;
    private float   prevSrvYaw, prevSrvPitch;
    private boolean srvRotReady = false;

    private float   savedCamYaw, savedCamPitch;
    private boolean camOverridden = false;

    private final Random rng     = new Random();
    private float jitterYaw      = 0f;
    private float jitterPitch    = 0f;
    private int   jitterCooldown = 0;

    // ─────────────────────────────────────────────────────────────────────────

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities.", Category.COMBAT);

        /*
         * START_CLIENT_TICK fires BEFORE vanilla sendMovementPackets().
         * By setting the entity yaw/pitch here, the look/position packet
         * the game sends this tick carries the target-facing direction.
         * We save the real camera first and restore it in onTick (END tick,
         * after packets but before rendering) so the player's first-person
         * view never visually moves.
         */
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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        clickTimer    = 0;
        srvRotReady   = false;
        camOverridden = false;
        isBlocking    = false;
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
    }

    // ── Main tick (END — after sendMovementPackets, before rendering) ─────────

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        // Step 1: Restore real camera before the frame renders
        if (legit.getValue() && camOverridden) {
            mc.player.setYaw(savedCamYaw);
            mc.player.setPitch(savedCamPitch);
            camOverridden = false;
        }

        // Step 2: WeaponOnly gate
        if (weaponOnly.getValue() && !isHoldingWeapon(mc)) {
            stopBlockingIfNeeded(mc);
            return;
        }

        // Step 3: Scan for targets in the wider scan radius
        Box scanBox = mc.player.getBoundingBox().expand(scanRange.getValue());
        List<LivingEntity> candidates = mc.world.getEntitiesByClass(
            LivingEntity.class, scanBox, e -> isValidTarget(mc, e)
        );

        LivingEntity target = candidates.stream()
            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.player)))
            .orElse(null);

        if (target == null) {
            stopBlockingIfNeeded(mc);
            return;
        }

        // Step 4: Check if target is within attackable range
        double dist   = Math.sqrt(target.squaredDistanceTo(mc.player));
        boolean inRange     = dist <= range.getValue() && hasLineOfSight(mc, target);
        boolean inWallRange = wallRange.getValue() > 0 && dist <= wallRange.getValue();

        if (!inRange && !inWallRange) {
            // In scan range but not yet close enough — hold shield
            if (autoBlock.getValue()) startBlocking(mc);
            return;
        }

        // Step 5: Raycast crosshair entity selection
        if (raycast.getValue() != RaycastMode.NONE) {
            LivingEntity crosshairTarget = findCrosshairTarget(mc);
            if (crosshairTarget != null && isValidTarget(mc, crosshairTarget)) {
                target = crosshairTarget;
            }
        }

        // Step 6: CPS tick gate
        clickTimer++;
        if (clickTimer < nextClickTick) {
            if (autoBlock.getValue()) startBlocking(mc);
            return;
        }
        scheduleNextClick();

        // Step 7: Attack
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

    // ── Range & LoS helpers ───────────────────────────────────────────────────

    private boolean hasLineOfSight(MinecraftClient mc, LivingEntity target) {
        Vec3d eye = new Vec3d(
            mc.player.getX(),
            mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()),
            mc.player.getZ()
        );
        Vec3d tgt = new Vec3d(
            target.getX(),
            target.getY() + target.getHeight() * 0.5,
            target.getZ()
        );
        HitResult hit = mc.world.raycast(new RaycastContext(
            eye, tgt,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));
        return hit.getType() == HitResult.Type.MISS;
    }

    /**
     * Finds the entity in the player's current crosshair within attack range.
     * Used for ENEMY (players only) and ALL raycast modes.
     */
    private LivingEntity findCrosshairTarget(MinecraftClient mc) {
        Vec3d eye  = new Vec3d(
            mc.player.getX(),
            mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()),
            mc.player.getZ()
        );
        Vec3d look = mc.player.getRotationVec(1f);
        Vec3d end  = eye.add(look.multiply(range.getValue()));

        LivingEntity best   = null;
        double       bestDist = Double.MAX_VALUE;

        for (LivingEntity e : mc.world.getEntitiesByClass(
                LivingEntity.class,
                mc.player.getBoundingBox().expand(range.getValue()),
                en -> isValidTarget(mc, en))) {

            if (raycast.getValue() == RaycastMode.ENEMY && !(e instanceof PlayerEntity)) continue;

            Box expanded = e.getBoundingBox().expand(0.3);
            var hit = expanded.raycast(eye, end);
            if (hit.isPresent()) {
                double d = eye.squaredDistanceTo(hit.get());
                if (d < bestDist) { bestDist = d; best = e; }
            }
        }
        return best;
    }

    // ── Target validation ─────────────────────────────────────────────────────

    private boolean isValidTarget(MinecraftClient mc, LivingEntity e) {
        if (e == mc.player) return false;
        if (!e.isAlive()) return false;
        if (e instanceof PlayerEntity p) {
            if (!players.getValue()) return false;
            if (p.isCreative() || p.isSpectator()) return false;
            if (teamCheck.getValue() && isTeammate(mc.player, p)) return false;
        } else {
            if (!mobs.getValue()) return false;
        }
        return true;
    }

    private static boolean isTeammate(PlayerEntity me, PlayerEntity other) {
        var myTeam    = me.getScoreboardTeam();
        var theirTeam = other.getScoreboardTeam();
        return myTeam != null && myTeam == theirTeam;
    }

    private static boolean isHoldingWeapon(MinecraftClient mc) {
        ItemStack stack = mc.player.getMainHandStack();
        return stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES);
    }

    // ── AutoBlock ─────────────────────────────────────────────────────────────

    private boolean hasShield(MinecraftClient mc) {
        return mc.player.getOffHandStack().getItem() instanceof ShieldItem;
    }

    private void startBlocking(MinecraftClient mc) {
        if (isBlocking || !hasShield(mc) || mc.player.isUsingItem()) return;
        mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
        isBlocking = true;
    }

    private void stopBlocking(MinecraftClient mc) {
        if (!isBlocking) return;
        if (mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.OFF_HAND) {
            mc.player.stopUsingItem();
        }
        isBlocking = false;
    }

    private void stopBlockingIfNeeded(MinecraftClient mc) {
        if (autoBlock.getValue()) stopBlocking(mc);
    }

    // ── Attack ────────────────────────────────────────────────────────────────

    private void attack(MinecraftClient mc, LivingEntity target) {
        Criticals crit = ModuleManager.get(Criticals.class);
        if (crit != null && crit.isEnabled() && crits.getValue()) {
            Criticals.sendCritPackets(mc);
        }

        boolean wasSprinting = mc.player.isSprinting();
        // Disable sprint momentarily to prevent sword sweep hitting a 2nd entity
        mc.player.setSprinting(false);

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        // KeepSprint: restore sprint right after the attack packet so
        // the server never sees us stop moving fast
        if (keepSprint.getValue()) {
            mc.player.setSprinting(wasSprinting);
        }
    }

    // ── Legit / Silent-Aim ────────────────────────────────────────────────────
    // Per-activation state (declared here to avoid cluttering the class header)
    private int     legitLockOn   = 0;
    private int     legitHitDelay = 0;
    private float   legitOsYaw    = 0f, legitOsPitch = 0f;
    private boolean legitCorr     = false;
    private static final int LEGIT_LOCK = 6;

    private void handleLegit(MinecraftClient mc, LivingEntity target) {
        if (!srvRotReady) {
            serverYaw    = savedCamYaw;
            serverPitch  = savedCamPitch;
            prevSrvYaw   = serverYaw;
            prevSrvPitch = serverPitch;
            srvRotReady  = true;
        }

        float[] ideal      = getRotationsTo(mc.player, target);
        float   cleanAngle = angleFromRot(serverYaw, serverPitch, mc.player, target);

        // ── Jitter ±0.35° yaw / ±0.20° pitch, refresh 8-16 ticks ─────────
        // Old values (±1.75° / ±1.0°) were 3-4× too large for Vulcan
        // Hitbox A which flags at 0.42°. With old jitter the attack gate
        // (now 0.40°) was almost never cleared so legit mode barely attacked.
        if (--jitterCooldown <= 0) {
            jitterYaw      = (rng.nextFloat() - 0.5f) * 0.70f;  // ±0.35°
            jitterPitch    = (rng.nextFloat() - 0.5f) * 0.40f;  // ±0.20°
            jitterCooldown = 8 + rng.nextInt(9);
        }

        // ── Overshoot / correct — proper 2-tick cycle ─────────────────────
        float osY = 0f, osP = 0f;
        if (legitCorr) {
            osY = -legitOsYaw; osP = -legitOsPitch;
            legitCorr = false;
        } else if (cleanAngle > 3f && cleanAngle < 20f && rng.nextFloat() < 0.08f) {
            legitOsYaw = (rng.nextFloat() - 0.5f) * 3.5f;
            legitOsPitch = (rng.nextFloat() - 0.5f) * 2.0f;
            osY = legitOsYaw; osP = legitOsPitch;
            legitCorr = true;
        }

        float tYaw   = ideal[0] + jitterYaw + osY;
        float tPitch = MathHelper.clamp(ideal[1] + jitterPitch + osP, -90f, 90f);

        // ── Distance-adaptive step sizes (old: constant 16°/13°) ──────────
        float sY, sP;
        if      (cleanAngle > 15f) { sY = 7f  + rng.nextFloat() * 5f;   sP = 5f  + rng.nextFloat() * 4f; }
        else if (cleanAngle >  5f) { sY = 3f  + rng.nextFloat() * 4f;   sP = 2f  + rng.nextFloat() * 3f; }
        else                        { sY = 0.8f + rng.nextFloat() * 1.8f; sP = 0.6f + rng.nextFloat() * 1.2f; }

        serverYaw   = smoothStep(prevSrvYaw,   tYaw,   sY);
        serverPitch = smoothStep(prevSrvPitch, tPitch, sP);

        // ── GCD + 15 % sub-GCD noise ───────────────────────────────────────
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

        legitLockOn++;
        if (legitHitDelay > 0) { legitHitDelay--; return; }
        if (legitLockOn < LEGIT_LOCK) return;

        // ── Attack gate: Vulcan Hitbox A = 0.42°, we use 0.40° ────────────
        // Old gate was 35° — effectively always attack regardless of aim.
        float sentAngle = angleFromRot(serverYaw, serverPitch, mc.player, target);
        if (sentAngle > 0.40f) return;

        attack(mc, target);
        legitHitDelay = 5 + rng.nextInt(6);
    }

    // ── Rotation math ─────────────────────────────────────────────────────────

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
        Vec3d tgt = new Vec3d(
            target.getX(),
            target.getY() + target.getHeight() * 0.5,
            target.getZ()
        );
        Vec3d eye = new Vec3d(
            player.getX(),
            player.getY() + player.getEyeHeight(player.getPose()),
            player.getZ()
        );
        double dot = MathHelper.clamp(look.dotProduct(tgt.subtract(eye).normalize()), -1.0, 1.0);
        return (float) Math.toDegrees(Math.acos(dot));
    }

    private static float getGcd(MinecraftClient mc) {
        double sens = mc.options.getMouseSensitivity().getValue();
        double f    = sens * 0.6 + 0.2;
        return (float) (f * f * f * 1.2);
    }
}

