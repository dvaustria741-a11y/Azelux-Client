package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import com.azeluxclient.module.ModuleManager;
import com.azeluxclient.setting.BooleanSetting;
import com.azeluxclient.setting.SliderSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * AutoPvP — fights like a skilled human PvP player.
 *
 * Features (all with randomised entropy so no two fights look identical):
 *   1. Predictive aim    — leads target by velocity × lag-comp ticks
 *   2. Silent-aim        — identical to KillAura legit (GCD, jitter ±0.35°,
 *                          idle ticks, overshoot/correct 2-tick cycle)
 *   3. Circle-strafe     — orbits target at ~2.8 m with randomised
 *                          direction switches every 1.5–4 s
 *   4. W-tap             — brief sprint breaks (2–4 ticks) every 8–20 ticks
 *   5. Crit              — uses Criticals micro-jump packets on 55 % of attacks
 *   6. Health-aware heal — stops fighting, retreats, and eats/drinks when < 8 ♥;
 *                          resumes when > 14 ♥; priority: splash heal → gapple → food
 *
 * Vulcan limits respected (same as KillAura legit):
 *   Aim A     — step-size variance, idle ticks, overshoot/correct
 *   Hitbox A  — jitter capped ±0.35°, attack gate 0.40°
 *   Reach A   — range capped at 3.0 m (slider max)
 *   Improbable — no simultaneous check spam
 */
public class AutoPvP extends Module {

    // ── Settings ─────────────────────────────────────────────────────────────
    private final SliderSetting  range       = register(new SliderSetting ("Range",        3.0, 2.0, 3.5));
    private final BooleanSetting playersOnly = register(new BooleanSetting("Players Only", true));
    private final BooleanSetting eatToHeal   = register(new BooleanSetting("Eat to Heal",  true));

    // ── Silent-aim (mirrors KillAura legit) ──────────────────────────────────
    private float   serverYaw, serverPitch;
    private float   prevSrvYaw, prevSrvPitch;
    private boolean srvRotReady   = false;
    private float   savedCamYaw, savedCamPitch;
    private boolean camOverridden = false;
    private float   jitterYaw, jitterPitch;
    private int     jitterTimer   = 0;
    private int     idleTicks     = 0;
    private float   overshootYaw, overshootPitch;
    private boolean correcting    = false;
    private int     lockOnTicks   = 0;
    private static final int LOCK_ON = 5;

    // ── Entropy / RNG ─────────────────────────────────────────────────────────
    private final Random rng = new Random();

    // ── Attack ────────────────────────────────────────────────────────────────
    private LivingEntity target  = null;
    private int          hitDelay = 0;
    private int          critCooldown = 0;

    // ── Strafe ────────────────────────────────────────────────────────────────
    private double orbitAngle        = 0.0;   // radians, current orbit position
    private int    strafeDir         = 1;     // +1 or -1
    private int    strafeSwitchTimer = 0;     // ticks until direction flip
    private static final double ORBIT_RADIUS = 2.8;
    private static final double ORBIT_SPEED  = 0.055; // rad/tick ≈ 63°/s

    // ── W-tap ─────────────────────────────────────────────────────────────────
    private int wTapClock    = 0;  // counts up
    private int wTapInterval = 14; // ticks between w-tap triggers
    private int wTapOffTicks = 0;  // counts down during sprint-off phase

    // ── Heal / retreat ────────────────────────────────────────────────────────
    private boolean healing      = false;
    private int     healUseCd    = 0;   // ticks before next item use
    private int     retreatTimer = 0;

    // ── Lag compensation ──────────────────────────────────────────────────────
    /** Ticks of latency to predict ahead (≈ 150 ms at 20 TPS). */
    private static final int LAG_COMP = 3;

    // ──────────────────────────────────────────────────────────────────────────

    public AutoPvP() {
        super("AutoPvP", "Fights like a skilled human PvP player. Bypasses Vulcan.", Category.COMBAT);

        // Apply silent-aim rotation BEFORE the movement packet is sent this tick,
        // exactly as KillAura legit does via START_CLIENT_TICK.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (!isEnabled() || client.player == null || !srvRotReady) return;

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
        resetAimState();
        target           = null;
        healing          = false;
        wTapClock        = 0;
        wTapInterval     = 8 + rng.nextInt(13);
        wTapOffTicks     = 0;
        hitDelay         = 0;
        critCooldown     = 0;
        orbitAngle       = rng.nextDouble() * Math.PI * 2;
        strafeDir        = rng.nextBoolean() ? 1 : -1;
        strafeSwitchTimer = 30 + rng.nextInt(50);
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = mc();
        if (mc != null && mc.player != null && camOverridden) {
            mc.player.setYaw(savedCamYaw);
            mc.player.setPitch(savedCamPitch);
        }
        target        = null;
        healing       = false;
        camOverridden = false;
        srvRotReady   = false;
    }

    // ── Main tick ─────────────────────────────────────────────────────────────

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        // Restore visual camera after START_CLIENT_TICK sent the server rotation
        if (camOverridden) {
            mc.player.setYaw(savedCamYaw);
            mc.player.setPitch(savedCamPitch);
            camOverridden = false;
        }

        // ── Health-aware healing ──────────────────────────────────────────────
        float hp = mc.player.getHealth();
        if (eatToHeal.getValue()) {
            if (!healing && hp <= 16.0f)           // below 8 hearts
                startHeal();
            if (healing && hp >= 28.0f)            // above 14 hearts
                healing = false;
        }

        if (healing) {
            tickHeal(mc);
            tickRetreat(mc);
            return;
        }

        // ── Find nearest valid target ─────────────────────────────────────────
        double r  = range.getValue();
        Box    bb = mc.player.getBoundingBox().expand(r);

        List<LivingEntity> entities = mc.world.getEntitiesByClass(
            LivingEntity.class, bb,
            e -> e != mc.player
              && !e.isDead()
              && (playersOnly.getValue()
                  ? e instanceof PlayerEntity && !((PlayerEntity) e).isCreative()
                  : true)
        );

        if (entities.isEmpty()) {
            target      = null;
            lockOnTicks = 0;
            return;
        }

        target = entities.stream()
            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.player)))
            .orElse(null);
        if (target == null) return;

        // ── Movement ─────────────────────────────────────────────────────────
        tickStrafe(mc);
        tickWTap(mc);

        // ── Silent aim (predictive) ───────────────────────────────────────────
        tickAim(mc, target);

        // ── Attack gate ──────────────────────────────────────────────────────
        if (mc.player.getAttackCooldownProgress(0f) < 1.0f) return;
        if (mc.player.isUsingItem()) return;
        if (hitDelay > 0)           { hitDelay--; return; }
        if (lockOnTicks < LOCK_ON)  return;

        float sentAngle = angleFromRot(serverYaw, serverPitch, mc.player, target);
        if (sentAngle > 0.40f) return;

        // ── Crit (55 % chance, uses Criticals' micro-jump packets) ────────────
        if (critCooldown > 0) critCooldown--;
        if (critCooldown == 0 && mc.player.isOnGround() && rng.nextFloat() < 0.55f) {
            Criticals.sendCritPackets(mc);
            critCooldown = 3 + rng.nextInt(5); // 3–7 ticks between crits
        }

        doAttack(mc, target);
        hitDelay = 5 + rng.nextInt(5); // 250–500 ms (2–4 CPS)
    }

    // ── Silent aim ────────────────────────────────────────────────────────────

    private void tickAim(MinecraftClient mc, LivingEntity t) {
        if (!srvRotReady) {
            serverYaw    = mc.player.getYaw();
            serverPitch  = mc.player.getPitch();
            prevSrvYaw   = serverYaw;
            prevSrvPitch = serverPitch;
            srvRotReady  = true;
        }

        // Predictive position: extrapolate target by velocity × lag-comp ticks
        Vec3d vel  = t.getVelocity();
        double px  = t.getX() + vel.x * LAG_COMP;
        double py  = t.getY() + vel.y * LAG_COMP + t.getHeight() * 0.5;
        double pz  = t.getZ() + vel.z * LAG_COMP;

        float[] ideal      = rotationsToPoint(mc.player, px, py, pz);
        float   cleanAngle = angleFromRot(serverYaw, serverPitch, mc.player, t);

        // Idle ticks — real mice stop briefly (breaks "always tracking" pattern)
        if (idleTicks > 0) {
            idleTicks--;
            lockOnTicks++;
            if (hitDelay > 0) hitDelay--;
            return;
        }
        if (cleanAngle < 10f && rng.nextFloat() < 0.10f)
            idleTicks = 1 + rng.nextInt(2);

        // Jitter (±0.35° yaw, ±0.20° pitch) — refresh every 8–16 ticks
        if (--jitterTimer <= 0) {
            jitterYaw   = (rng.nextFloat() - 0.5f) * 0.70f;
            jitterPitch = (rng.nextFloat() - 0.5f) * 0.40f;
            jitterTimer = 8 + rng.nextInt(9);
        }

        // Overshoot/correct — proper 2-tick cycle
        float osYaw = 0f, osPitch = 0f;
        if (correcting) {
            osYaw      = -overshootYaw;
            osPitch    = -overshootPitch;
            correcting = false;
        } else if (cleanAngle > 3f && cleanAngle < 20f && rng.nextFloat() < 0.08f) {
            overshootYaw   = (rng.nextFloat() - 0.5f) * 3.5f;
            overshootPitch = (rng.nextFloat() - 0.5f) * 2.0f;
            osYaw          = overshootYaw;
            osPitch        = overshootPitch;
            correcting     = true;
        }

        float tYaw   = ideal[0] + jitterYaw + osYaw;
        float tPitch = MathHelper.clamp(ideal[1] + jitterPitch + osPitch, -90f, 90f);

        // Distance-adaptive step sizes (avoids constant-step fingerprint)
        float sY, sP;
        if      (cleanAngle > 15f) { sY = 7f + rng.nextFloat() * 5f;   sP = 5f + rng.nextFloat() * 4f; }
        else if (cleanAngle > 5f)  { sY = 3f + rng.nextFloat() * 4f;   sP = 2f + rng.nextFloat() * 3f; }
        else                        { sY = 0.8f + rng.nextFloat() * 1.8f; sP = 0.6f + rng.nextFloat() * 1.2f; }

        serverYaw   = smoothStep(prevSrvYaw,   tYaw,   sY);
        serverPitch = smoothStep(prevSrvPitch, tPitch, sP);

        // GCD quantization (mouse sensitivity emulation)
        float gcd    = getGcd(mc);
        float dYaw   = Math.round((serverYaw   - prevSrvYaw)   / gcd) * gcd;
        float dPitch = Math.round((serverPitch - prevSrvPitch) / gcd) * gcd;
        if (rng.nextFloat() < 0.15f) {          // 15 % sub-GCD noise
            dYaw   += (rng.nextFloat() - 0.5f) * gcd * 0.35f;
            dPitch += (rng.nextFloat() - 0.5f) * gcd * 0.35f;
        }
        serverYaw    = prevSrvYaw   + dYaw;
        serverPitch  = prevSrvPitch + dPitch;
        prevSrvYaw   = serverYaw;
        prevSrvPitch = serverPitch;

        lockOnTicks++;
    }

    // ── Circle-strafe ─────────────────────────────────────────────────────────

    private void tickStrafe(MinecraftClient mc) {
        // Randomised direction switch every 1.5–4 s (30–80 ticks at 20 TPS)
        if (--strafeSwitchTimer <= 0) {
            strafeDir         = -strafeDir;
            strafeSwitchTimer = 30 + rng.nextInt(50);
            // Occasional micro-pause before changing direction (looks human)
            if (rng.nextFloat() < 0.25f)
                strafeSwitchTimer += 5 + rng.nextInt(8);
        }

        // Advance orbit angle with slight speed variance each tick
        double speed = ORBIT_SPEED * (0.85 + rng.nextDouble() * 0.30);
        orbitAngle  += strafeDir * speed;

        // Desired position on the orbit circle around the target
        double desiredX = target.getX() + Math.sin(orbitAngle) * ORBIT_RADIUS;
        double desiredZ = target.getZ() + Math.cos(orbitAngle) * ORBIT_RADIUS;

        double dx   = desiredX - mc.player.getX();
        double dz   = desiredZ - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.05) return;

        // Scale movement speed with distance from orbit radius + variance
        double speed2 = 0.20 * (0.90 + rng.nextDouble() * 0.20) * Math.min(dist, 1.0);
        double nx     = dx / dist * speed2;
        double nz     = dz / dist * speed2;

        Vec3d vel = mc.player.getVelocity();
        mc.player.setVelocity(vel.x * 0.6 + nx, vel.y, vel.z * 0.6 + nz);
        mc.player.setSprinting(true);
    }

    // ── W-tap ─────────────────────────────────────────────────────────────────

    private void tickWTap(MinecraftClient mc) {
        // If in sprint-off phase, count down and then resume
        if (wTapOffTicks > 0) {
            wTapOffTicks--;
            mc.player.setSprinting(false);
            return;
        }

        // Count up to the next tap trigger
        if (++wTapClock >= wTapInterval) {
            wTapClock    = 0;
            wTapInterval = 8 + rng.nextInt(13); // 0.4–1.0 s
            wTapOffTicks = 2 + rng.nextInt(3);  // stop sprint for 2–4 ticks
        }
    }

    // ── Heal ──────────────────────────────────────────────────────────────────

    private void startHeal() {
        healing      = true;
        retreatTimer = 20 + rng.nextInt(20); // retreat for 1–2 s
        healUseCd    = 0;
    }

    private void tickHeal(MinecraftClient mc) {
        if (healUseCd > 0) { healUseCd--; return; }

        int slot = findHealSlot(mc);
        if (slot < 0) { healing = false; return; } // nothing to eat/drink

        mc.player.getInventory().selectedSlot = slot;
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        healUseCd = 15 + rng.nextInt(10); // brief wait between uses
    }

    private void tickRetreat(MinecraftClient mc) {
        if (target == null || retreatTimer <= 0) { retreatTimer = 0; return; }
        retreatTimer--;

        // Move directly away from target
        Vec3d toTarget  = target.getPos().subtract(mc.player.getPos());
        double dist     = toTarget.length();
        if (dist < 0.1) return;

        Vec3d   away    = toTarget.negate().normalize().multiply(0.22);
        Vec3d   vel     = mc.player.getVelocity();
        mc.player.setVelocity(vel.x + away.x, vel.y, vel.z + away.z);
        mc.player.setSprinting(true);
    }

    /**
     * Finds the best heal item in the hotbar (slots 0–8).
     * Priority: splash instant-health potion > enchanted golden apple
     *           > golden apple > any food.
     */
    private int findHealSlot(MinecraftClient mc) {
        // 1. Splash health potion
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() instanceof SplashPotionItem && isHealPotion(s)) return i;
        }
        // 2. Enchanted golden apple
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.ENCHANTED_GOLDEN_APPLE)) return i;
        }
        // 3. Golden apple
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.GOLDEN_APPLE)) return i;
        }
        // 4. Any food
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).contains(DataComponentTypes.FOOD)) return i;
        }
        return -1;
    }

    /**
     * Returns true if the potion contains an instant-health or regeneration effect.
     */
    private static boolean isHealPotion(ItemStack stack) {
        PotionContentsComponent c = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (c == null) return false;
        for (var eff : c.getEffects()) {
            // StatusEffects.INSTANT_HEALTH and REGENERATION are RegistryEntry<StatusEffect>
            if (eff.getEffectType() == StatusEffects.INSTANT_HEALTH
             || eff.getEffectType() == StatusEffects.REGENERATION)
                return true;
        }
        return false;
    }

    // ── Normal attack ─────────────────────────────────────────────────────────

    private void doAttack(MinecraftClient mc, LivingEntity t) {
        boolean wasSprinting = mc.player.isSprinting();
        mc.player.setSprinting(false);
        mc.interactionManager.attackEntity(mc.player, t);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.setSprinting(wasSprinting);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Rotation angles (yaw, pitch) to look from player eye toward (x, y, z).
     */
    private static float[] rotationsToPoint(PlayerEntity player, double x, double y, double z) {
        double dx    = x - player.getX();
        double dy    = y - (player.getY() + player.getEyeHeight(player.getPose()));
        double dz    = z - player.getZ();
        double hDist = Math.sqrt(dx * dx + dz * dz);
        float  yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float  pitch = (float) -Math.toDegrees(Math.atan2(dy, hDist));
        return new float[]{ yaw, MathHelper.clamp(pitch, -90f, 90f) };
    }

    private static float smoothStep(float current, float target, float maxStep) {
        float diff = MathHelper.wrapDegrees(target - current);
        return current + MathHelper.clamp(diff, -maxStep, maxStep);
    }

    /** Angle in degrees between the server look direction and the direction to target. */
    private static float angleFromRot(float yaw, float pitch,
                                      PlayerEntity player, LivingEntity target) {
        double yr   = Math.toRadians(yaw);
        double pr   = Math.toRadians(pitch);
        Vec3d  look = new Vec3d(
            -Math.sin(yr) * Math.cos(pr),
            -Math.sin(pr),
             Math.cos(yr) * Math.cos(pr)
        ).normalize();
        Vec3d eye      = new Vec3d(
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

    /** GCD derived from mouse sensitivity — quantizes rotation like real hardware. */
    private static float getGcd(MinecraftClient mc) {
        double sens = mc.options.getMouseSensitivity().getValue();
        double f    = sens * 0.6 + 0.2;
        return (float) (f * f * f * 1.2);
    }

    private void resetAimState() {
        srvRotReady   = false;
        camOverridden = false;
        correcting    = false;
        jitterTimer   = 0;
        idleTicks     = 0;
        lockOnTicks   = 0;
        prevSrvYaw    = 0f;
        prevSrvPitch  = 0f;
    }
}
