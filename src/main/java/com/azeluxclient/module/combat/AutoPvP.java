package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import com.azeluxclient.module.ModuleManager;
import com.azeluxclient.module.movement.Freelook;
import com.azeluxclient.setting.BooleanSetting;
import com.azeluxclient.setting.SliderSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
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
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * AutoPvP — fights like a skilled human PvP player.
 *
 * Activation: Hold C → activates while the key is held, deactivates on release.
 *             This keeps the toggle-based module system working normally but
 *             overlays a hold-detection layer on top.
 *
 * On enable:
 *   • Switches to third-person back view so you can see yourself fighting.
 *   • Activates Freelook so you can orbit the camera while combat runs.
 *
 * Combat features:
 *   1. Predictive silent aim  — leads target by velocity × lag-comp ticks,
 *                               GCD-quantized, with idle ticks, jitter,
 *                               and overshoot/correct cycle.
 *   2. Circle-strafe          — orbits target at ~2.8 m with random direction
 *                               switches every 1.5–4 s.
 *   3. W-tap                  — brief sprint breaks (2–4 ticks) every 0.4–1 s.
 *   4. Crit                   — micro-jump packets on 55 % of full-cooldown hits.
 *   5. Health-aware healing   — retreats and eats/drinks below 8 hearts;
 *                               priority: splash heal → gapple → golden apple → food.
 */
public class AutoPvP extends Module {

    // ── Settings ──────────────────────────────────────────────────────────────
    private final SliderSetting  range       = register(new SliderSetting ("Range",        3.0,  2.0, 3.5));
    private final BooleanSetting playersOnly = register(new BooleanSetting("Players Only", true));
    private final BooleanSetting eatToHeal   = register(new BooleanSetting("Eat to Heal",  true));
    private final BooleanSetting thirdPerson = register(new BooleanSetting("Third Person", true));

    // ── Silent-aim state ──────────────────────────────────────────────────────
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

    // ── State ─────────────────────────────────────────────────────────────────
    private final Random  rng         = new Random();
    private LivingEntity  target      = null;
    private int           hitDelay    = 0;
    private int           critCooldown = 0;
    private Perspective   prevPerspective = null;

    // ── Hold-C key tracking ───────────────────────────────────────────────────
    private boolean holdActive = false;

    // ── Strafe ────────────────────────────────────────────────────────────────
    private double orbitAngle        = 0.0;
    private int    strafeDir         = 1;
    private int    strafeSwitchTimer = 0;
    private static final double ORBIT_RADIUS = 2.8;
    private static final double ORBIT_SPEED  = 0.055;

    // ── W-tap ─────────────────────────────────────────────────────────────────
    private int wTapClock    = 0;
    private int wTapInterval = 14;
    private int wTapOffTicks = 0;

    // ── Heal / retreat ────────────────────────────────────────────────────────
    private boolean healing      = false;
    private int     healUseCd    = 0;
    private int     retreatTimer = 0;

    // ── Lag comp ──────────────────────────────────────────────────────────────
    private static final int LAG_COMP = 3;

    // ─────────────────────────────────────────────────────────────────────────

    public AutoPvP() {
        super("AutoPvP", "Hold C to fight — auto third-person + Freelook camera.", Category.COMBAT);

        // Apply silent-aim rotation BEFORE movement packets are sent each tick
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
        target        = null;
        healing       = false;
        holdActive    = false;
        wTapClock     = 0;
        wTapInterval  = 8 + rng.nextInt(13);
        wTapOffTicks  = 0;
        hitDelay      = 0;
        critCooldown  = 0;
        orbitAngle    = rng.nextDouble() * Math.PI * 2;
        strafeDir     = rng.nextBoolean() ? 1 : -1;
        strafeSwitchTimer = 30 + rng.nextInt(50);

        MinecraftClient mc = mc();
        if (mc != null) {
            // Save and switch to third-person back view
            if (thirdPerson.getValue()) {
                prevPerspective = mc.options.getPerspective();
                mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
            // Enable Freelook so the camera can orbit freely while combat runs
            Freelook.setActive(true, mc);
        }
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = mc();
        if (mc != null && mc.player != null) {
            // Restore camera rotation
            if (camOverridden) {
                mc.player.setYaw(savedCamYaw);
                mc.player.setPitch(savedCamPitch);
            }
            // Restore original perspective
            if (prevPerspective != null) {
                mc.options.setPerspective(prevPerspective);
                prevPerspective = null;
            }
        }
        // Deactivate freelook
        Freelook.setActive(false, mc);

        target        = null;
        healing       = false;
        holdActive    = false;
        camOverridden = false;
        srvRotReady   = false;
    }

    // ── Main tick ─────────────────────────────────────────────────────────────

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        // ── Hold-C detection ─────────────────────────────────────────────────
        // The module can be toggled normally via the GUI, OR held via C key.
        long win = mc.getWindow().getHandle();
        boolean cHeld = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;

        if (cHeld && !holdActive) {
            holdActive = true;
            if (!isEnabled()) toggle(); // enable if not already on
        } else if (!cHeld && holdActive) {
            holdActive = false;
            if (isEnabled()) toggle(); // release key → disable
        }

        if (!isEnabled()) return;

        // ── Restore visual camera (after movement packet, before render) ──────
        if (camOverridden) {
            mc.player.setYaw(savedCamYaw);
            mc.player.setPitch(savedCamPitch);
            camOverridden = false;
        }

        // ── Health-aware healing ──────────────────────────────────────────────
        float hp = mc.player.getHealth();
        if (eatToHeal.getValue()) {
            if (!healing && hp <= 16.0f) startHeal();
            if (healing && hp >= 28.0f)  healing = false;
        }
        if (healing) {
            tickHeal(mc);
            tickRetreat(mc);
            return;
        }

        // ── Find nearest valid target ─────────────────────────────────────────
        Box bb = mc.player.getBoundingBox().expand(range.getValue());
        List<LivingEntity> entities = mc.world.getEntitiesByClass(
            LivingEntity.class, bb,
            e -> e != mc.player && !e.isDead()
              && (playersOnly.getValue()
                  ? e instanceof PlayerEntity p && !p.isCreative() && !p.isSpectator()
                  : true)
        );

        if (entities.isEmpty()) { target = null; lockOnTicks = 0; return; }

        target = entities.stream()
            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.player)))
            .orElse(null);
        if (target == null) return;

        // ── Combat ───────────────────────────────────────────────────────────
        tickStrafe(mc);
        tickWTap(mc);
        tickAim(mc, target);

        if (mc.player.getAttackCooldownProgress(0f) < 1.0f) return;
        if (mc.player.isUsingItem()) return;
        if (hitDelay > 0)           { hitDelay--; return; }
        if (lockOnTicks < LOCK_ON)  return;

        float sentAngle = angleFromRot(serverYaw, serverPitch, mc.player, target);
        if (sentAngle > 0.40f) return;

        if (critCooldown > 0) critCooldown--;
        if (critCooldown == 0 && mc.player.isOnGround() && rng.nextFloat() < 0.55f) {
            Criticals.sendCritPackets(mc);
            critCooldown = 3 + rng.nextInt(5);
        }

        doAttack(mc, target);
        hitDelay = 5 + rng.nextInt(5);
    }

    // ── Silent-aim (predictive, GCD-fixed, LiquidBounce-style) ───────────────

    private void tickAim(MinecraftClient mc, LivingEntity t) {
        if (!srvRotReady) {
            serverYaw    = mc.player.getYaw();
            serverPitch  = mc.player.getPitch();
            prevSrvYaw   = serverYaw;
            prevSrvPitch = serverPitch;
            srvRotReady  = true;
        }

        // Predictive: lead target by velocity × lag-comp ticks
        Vec3d vel = t.getVelocity();
        double px = t.getX() + vel.x * LAG_COMP;
        double py = t.getY() + vel.y * LAG_COMP + t.getHeight() * 0.5;
        double pz = t.getZ() + vel.z * LAG_COMP;

        float[] ideal      = rotationsToPoint(mc.player, px, py, pz);
        float   cleanAngle = angleFromRot(serverYaw, serverPitch, mc.player, t);

        // Idle ticks — real mice momentarily stop tracking
        if (idleTicks > 0) {
            idleTicks--;
            lockOnTicks++;
            if (hitDelay > 0) hitDelay--;
            return;
        }
        if (cleanAngle < 10f && rng.nextFloat() < 0.10f)
            idleTicks = 1 + rng.nextInt(2);

        // Jitter ±0.35° yaw / ±0.20° pitch, refresh every 8–16 ticks
        if (--jitterTimer <= 0) {
            jitterYaw   = (rng.nextFloat() - 0.5f) * 0.70f;
            jitterPitch = (rng.nextFloat() - 0.5f) * 0.40f;
            jitterTimer = 8 + rng.nextInt(9);
        }

        // Overshoot / correct — 2-tick cycle like LiquidBounce's aim smoothing
        float osYaw = 0f, osPitch = 0f;
        if (correcting) {
            osYaw = -overshootYaw; osPitch = -overshootPitch;
            correcting = false;
        } else if (cleanAngle > 3f && cleanAngle < 20f && rng.nextFloat() < 0.08f) {
            overshootYaw = (rng.nextFloat() - 0.5f) * 3.5f;
            overshootPitch = (rng.nextFloat() - 0.5f) * 2.0f;
            osYaw = overshootYaw; osPitch = overshootPitch;
            correcting = true;
        }

        float tYaw   = ideal[0] + jitterYaw + osYaw;
        float tPitch = MathHelper.clamp(ideal[1] + jitterPitch + osPitch, -90f, 90f);

        // Distance-adaptive smooth step — mirrors LiquidBounce's speed variance
        float sY, sP;
        if      (cleanAngle > 15f) { sY = 7f  + rng.nextFloat() * 5f;   sP = 5f  + rng.nextFloat() * 4f; }
        else if (cleanAngle >  5f) { sY = 3f  + rng.nextFloat() * 4f;   sP = 2f  + rng.nextFloat() * 3f; }
        else                        { sY = 0.8f + rng.nextFloat() * 1.8f; sP = 0.6f + rng.nextFloat() * 1.2f; }

        serverYaw   = smoothStep(prevSrvYaw,   tYaw,   sY);
        serverPitch = smoothStep(prevSrvPitch, tPitch, sP);

        // GCD quantization + 15 % sub-GCD noise
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

        lockOnTicks++;
    }

    // ── Circle-strafe ─────────────────────────────────────────────────────────

    private void tickStrafe(MinecraftClient mc) {
        if (--strafeSwitchTimer <= 0) {
            strafeDir         = -strafeDir;
            strafeSwitchTimer = 30 + rng.nextInt(50);
            if (rng.nextFloat() < 0.25f) strafeSwitchTimer += 5 + rng.nextInt(8);
        }
        double speed = ORBIT_SPEED * (0.85 + rng.nextDouble() * 0.30);
        orbitAngle  += strafeDir * speed;

        double desiredX = target.getX() + Math.sin(orbitAngle) * ORBIT_RADIUS;
        double desiredZ = target.getZ() + Math.cos(orbitAngle) * ORBIT_RADIUS;
        double dx = desiredX - mc.player.getX();
        double dz = desiredZ - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.05) return;

        double mv = 0.20 * (0.90 + rng.nextDouble() * 0.20) * Math.min(dist, 1.0);
        Vec3d vel = mc.player.getVelocity();
        mc.player.setVelocity(vel.x * 0.6 + dx / dist * mv, vel.y, vel.z * 0.6 + dz / dist * mv);
        mc.player.setSprinting(true);
    }

    // ── W-tap ─────────────────────────────────────────────────────────────────

    private void tickWTap(MinecraftClient mc) {
        if (wTapOffTicks > 0) {
            wTapOffTicks--;
            mc.player.setSprinting(false);
            return;
        }
        if (++wTapClock >= wTapInterval) {
            wTapClock    = 0;
            wTapInterval = 8 + rng.nextInt(13);
            wTapOffTicks = 2 + rng.nextInt(3);
        }
    }

    // ── Heal ──────────────────────────────────────────────────────────────────

    private void startHeal() {
        healing      = true;
        retreatTimer = 20 + rng.nextInt(20);
        healUseCd    = 0;
    }

    private void tickHeal(MinecraftClient mc) {
        if (healUseCd > 0) { healUseCd--; return; }
        int slot = findHealSlot(mc);
        if (slot < 0) { healing = false; return; }
        mc.player.getInventory().selectedSlot = slot;
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        healUseCd = 15 + rng.nextInt(10);
    }

    private void tickRetreat(MinecraftClient mc) {
        if (target == null || retreatTimer <= 0) { retreatTimer = 0; return; }
        retreatTimer--;
        Vec3d toTarget = new Vec3d(
            target.getX() - mc.player.getX(), 0,
            target.getZ() - mc.player.getZ()
        );
        double dist = toTarget.length();
        if (dist < 0.1) return;
        Vec3d away = toTarget.negate().normalize().multiply(0.22);
        Vec3d vel  = mc.player.getVelocity();
        mc.player.setVelocity(vel.x + away.x, vel.y, vel.z + away.z);
        mc.player.setSprinting(true);
    }

    private int findHealSlot(MinecraftClient mc) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() instanceof SplashPotionItem && isHealPotion(s)) return i;
        }
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).isOf(Items.ENCHANTED_GOLDEN_APPLE)) return i;
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).isOf(Items.GOLDEN_APPLE)) return i;
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).contains(DataComponentTypes.FOOD)) return i;
        return -1;
    }

    private static boolean isHealPotion(ItemStack stack) {
        PotionContentsComponent c = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (c == null) return false;
        for (var eff : c.getEffects())
            if (eff.getEffectType() == StatusEffects.INSTANT_HEALTH
             || eff.getEffectType() == StatusEffects.REGENERATION) return true;
        return false;
    }

    // ── Attack ────────────────────────────────────────────────────────────────

    private void doAttack(MinecraftClient mc, LivingEntity t) {
        boolean wasSprinting = mc.player.isSprinting();
        mc.player.setSprinting(false);
        mc.interactionManager.attackEntity(mc.player, t);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.setSprinting(wasSprinting);
    }

    // ── Math helpers ──────────────────────────────────────────────────────────

    private static float[] rotationsToPoint(PlayerEntity player, double x, double y, double z) {
        double dx    = x - player.getX();
        double dy    = y - (player.getY() + player.getEyeHeight(player.getPose()));
        double dz    = z - player.getZ();
        double hDist = Math.sqrt(dx * dx + dz * dz);
        return new float[]{
            (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f,
            MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, hDist)), -90f, 90f)
        };
    }

    private static float smoothStep(float current, float target, float maxStep) {
        float diff = MathHelper.wrapDegrees(target - current);
        return current + MathHelper.clamp(diff, -maxStep, maxStep);
    }

    private static float angleFromRot(float yaw, float pitch,
                                      PlayerEntity player, LivingEntity target) {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        Vec3d look = new Vec3d(
            -Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)
        ).normalize();
        Vec3d eye = new Vec3d(player.getX(),
            player.getY() + player.getEyeHeight(player.getPose()), player.getZ());
        Vec3d to = new Vec3d(target.getX(),
            target.getY() + target.getHeight() * 0.5, target.getZ())
            .subtract(eye).normalize();
        return (float) Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(to), -1.0, 1.0)));
    }

    private static float getGcd(MinecraftClient mc) {
        double f = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        return (float) (f * f * f * 1.2);
    }

    private void resetAimState() {
        srvRotReady = false; camOverridden = false; correcting = false;
        jitterTimer = 0; idleTicks = 0; lockOnTicks = 0;
        prevSrvYaw = 0f; prevSrvPitch = 0f;
    }
}
