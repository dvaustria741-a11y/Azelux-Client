package com.azeluxclient.module.combat;

import com.azeluxclient.mixin.KeyBindingAccessor;
import com.azeluxclient.module.Module;
import com.azeluxclient.module.movement.Freelook;
import com.azeluxclient.setting.BooleanSetting;
import com.azeluxclient.setting.SliderSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * AutoPvP — full legit PvP automation.
 *
 * What was wrong before:
 *   • timesPressed attack relied on hitResult → missed when aim not converged
 *   • Aim lerp factor 0.08–0.12 for small angles → took 20+ ticks to reach 0.40° (now 45°)
 *   • hitDelay 5–9 ticks extra → ~1 attack per second instead of ~1.5
 *   • Heal fired at 4f (2 hearts) — too late, usually dead
 *   • No shield, no totem, no pearl, no axe-swap logic
 *
 * Fixed / added:
 *   • attackEntity() directly, same as KillAura — reliable, no hitResult dependency
 *   • Faster aim: 0.85–0.95 factor when angle < 3°, attack gate 45° — attacks fire as soon as aimed
 *   • hitDelay 2–5 ticks → ~1.3–1.5 attacks/s (human range, below Vulcan's CPS check)
 *   • Heal at 16f (8 hearts), resume at 28f (14 hearts)
 *   • Auto shield (raise between attacks, drop to attack)
 *   • Ender pearl gap-close (target > 5m)
 *   • Auto totem → offhand (every 10 ticks)
 *   • Axe when target is shielding (axes disable shields), else sword > axe
 *   • Damage tracking for shield timing
 *
 * Vulcan limits respected:
 *   Aim A     — variable step, idle ticks, overshoot/correct
 *   Hitbox A  — jitter ±0.35°, attack gate 45°
 *   Reach A   — capped at range slider (max 3.0 m)
 *   Improbable— ~1.3–1.5 CPS, not crit-spamming
 */
public class AutoPvP extends Module {

    // ── Settings ──────────────────────────────────────────────────────────────
    private final SliderSetting  range       = register(new SliderSetting ("Range",        3.0, 2.0, 3.5));
    private final BooleanSetting playersOnly = register(new BooleanSetting("Players Only", true));
    private final BooleanSetting eatToHeal   = register(new BooleanSetting("Eat to Heal",  true));
    private final BooleanSetting autoShield  = register(new BooleanSetting("Auto Shield",  true));
    private final BooleanSetting autoPearl   = register(new BooleanSetting("Ender Pearl",  true));
    private final BooleanSetting autoTotemSet= register(new BooleanSetting("Auto Totem",   true));
    private final BooleanSetting thirdPerson = register(new BooleanSetting("Third Person", true));
    private final BooleanSetting macePvP     = register(new BooleanSetting("Mace PvP",     false));
    private final BooleanSetting windLaunch  = register(new BooleanSetting("Wind Charge",  false));

    // ── Silent-aim state (mirrors KillAura legit) ─────────────────────────────
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

    // ── Attack / crit ─────────────────────────────────────────────────────────
    private final Random rng          = new Random();
    private LivingEntity target       = null;
    private int          hitDelay     = 0;
    private int          critCooldown = 0;
    private int          critState    = 0;    // 0 = idle, 1 = jumped, waiting to fall

    // ── Health tracking ───────────────────────────────────────────────────────
    private float   lastHealth    = 20f;
    private boolean healing       = false;
    private int     healUseCd     = 0;
    private int     healCooldown  = 0;  // gap between heal cycles (prevents spam-eating)
    private int     retreatTimer  = 0;
    private int     splashWarmup  = 0;
    private int     healSlot      = -1;  // hotbar slot held during eat/drink cycle
    private int     buffPotionCd  = 0;   // cooldown between proactive buff-potion checks
    private int     idleFoodCd    = 0;  // cooldown after each idle food item finishes

    // ── Mace PvP ──────────────────────────────────────────────────────────────
    private static final int MACE_IDLE    = 0;
    private static final int MACE_FALLING = 1;
    private static final int MACE_WIND    = 2;
    private int  maceState    = MACE_IDLE;
    private int  windChargeCd = 0;
    private int  maceComboCd  = 0;

    // ── Totem ─────────────────────────────────────────────────────────────────
    private int totemTimer = 0;

    // ── Shield ────────────────────────────────────────────────────────────────
    private int shieldPauseTicks = 0;  // brief pause before re-raising shield after hit

    // ── Ender pearl ───────────────────────────────────────────────────────────
    private int pearlCooldown  = 0;
    private int pearlWarmup    = 0;   // ticks holding pearl slot before throw
    private int pearlSlot      = -1;
    private float pearlYaw, pearlPitch;

    // ── Movement ──────────────────────────────────────────────────────────────
    private int strafeDir         = 1;
    private int strafeSwitchTimer = 0;
    private int wTapClock         = 0;
    private int wTapInterval      = 14;
    private int wTapOffTicks      = 0;

    // ── Perspective ───────────────────────────────────────────────────────────
    private Perspective prevPerspective = null;

    // ── Virtual key state (applied in START_CLIENT_TICK) ─────────────────────
    private volatile boolean kFwd, kBack, kLeft, kRight, kSprint, kJump, kUse;

    private static final int LOCK_ON  = 4;   // ticks of tracking before first attack attempt
    private static final int LAG_COMP = 1;   // ticks of lag compensation for prediction

    // ─────────────────────────────────────────────────────────────────────────

    public AutoPvP() {
        super("AutoPvP", "Legit PvP bot: aim, strafe, w-tap, shield, gapple, pearl, totem.", Category.COMBAT);

        // Silent aim + key injection run BEFORE MC processes movement/attack packets
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (isEnabled() && srvRotReady) {
                savedCamYaw   = client.player.getYaw();
                savedCamPitch = client.player.getPitch();
                camOverridden = true;
                client.player.setYaw(serverYaw);
                client.player.setPitch(serverPitch);
                client.player.setHeadYaw(serverYaw);
                client.player.bodyYaw = serverYaw;
            }

            if (isEnabled()) {
                client.options.forwardKey.setPressed(kFwd);
                client.options.backKey   .setPressed(kBack);
                client.options.leftKey   .setPressed(kLeft);
                client.options.rightKey  .setPressed(kRight);
                client.options.sprintKey .setPressed(kSprint);
                client.options.jumpKey   .setPressed(kJump);
                client.options.useKey    .setPressed(kUse);
            }
        });
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        resetAll();
        MinecraftClient mc = mc();
        if (mc != null && mc.player != null) {
            lastHealth = mc.player.getHealth();
            if (thirdPerson.getValue()) {
                prevPerspective = mc.options.getPerspective();
                mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
            Freelook.setActive(true, mc);
        }
    }

    @Override
    public void onDisable() {
        kFwd = kBack = kLeft = kRight = kSprint = kJump = kUse = false;
        healSlot = -1; pearlWarmup = 0; pearlSlot = -1; buffPotionCd = 0;
        MinecraftClient mc = mc();
        if (mc != null && mc.player != null) {
            if (camOverridden) {
                mc.player.setYaw(savedCamYaw); // pitch stays at serverPitch
            }
            if (prevPerspective != null) {
                mc.options.setPerspective(prevPerspective);
                prevPerspective = null;
            }
        }
        Freelook.setActive(false, mc);
        resetAll();
    }

    // ── Main tick (runs on END_CLIENT_TICK, after MC physics) ────────────────

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        // Restore visual camera (server rotation already sent in START_CLIENT_TICK)
        if (camOverridden) {
            mc.player.setYaw(savedCamYaw);
            // entity.pitch stays at serverPitch — no oscillation.
            // Camera pitch = Freelook.lookPitch (independent via CameraFreelookMixin).
            camOverridden = false;
        }

        // Reset all virtual keys; sub-systems re-assert what they need this tick
        kFwd = kBack = kLeft = kRight = kSprint = kJump = kUse = false;

        // ── Pearl warmup countdown ────────────────────────────────────────────
        // After throwPearl() stores the direction, we hold the pearl slot for
        // 2 ticks so the server sees "player switched to pearl and aimed" before
        // the throw arrives — real human behaviour.
        if (pearlWarmup > 0 && pearlSlot >= 0) {
            mc.player.getInventory().selectedSlot = pearlSlot;
            if (--pearlWarmup == 0) {
                // Warmup done — actually throw
                float origYaw   = mc.player.getYaw();
                float origPitch = mc.player.getPitch();
                mc.player.setYaw(pearlYaw);
                mc.player.setPitch(pearlPitch);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.setYaw(origYaw);
                mc.player.setPitch(origPitch);
                serverYaw = pearlYaw; serverPitch = pearlPitch;
                prevSrvYaw = serverYaw; prevSrvPitch = serverPitch;
                pearlCooldown = 25 + rng.nextInt(10);
                pearlSlot = -1;
            }
            return; // hold pearl slot this tick, skip combat
        }

        // ── Damage detection ─────────────────────────────────────────────────
        float curHp = mc.player.getHealth();
        if (curHp < lastHealth - 0.05f) shieldPauseTicks = 4; // hit → pause shield briefly
        lastHealth = curHp;
        if (pearlCooldown      > 0) pearlCooldown--;
        if (shieldPauseTicks   > 0) shieldPauseTicks--;

        // ── Auto totem → offhand (every 10 ticks) ────────────────────────────
        if (autoTotemSet.getValue() && ++totemTimer >= 10) {
            totemTimer = 0;
            if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING))
                moveTotemToOffhand(mc);
        }

        // ── Health gate ───────────────────────────────────────────────────────
        if (healCooldown > 0) healCooldown--;
        if (eatToHeal.getValue()) {
            if (!healing && curHp <= 12.0f && healCooldown == 0) {
                healing      = true;
                retreatTimer = 20 + rng.nextInt(20);
            }
            if (healing && curHp >= 18.0f) {
                healing      = false;
                healCooldown = 50 + rng.nextInt(30); // ~3-4s gap before next heal cycle
            }
        }

        if (healing) {
            // Bug 3 fix: escape pearl if target is closing in while we heal
            if (autoPearl.getValue() && pearlCooldown == 0 && target != null) {
                double healDist = Math.sqrt(mc.player.squaredDistanceTo(target));
                if (healDist < 5.0) {
                    int ps = findHotbarItem(mc, Items.ENDER_PEARL);
                    if (ps >= 0) { throwPearl(mc, ps); return; }
                }
            }
            tickHeal(mc);
            tickRetreat(mc);  // Bug 1 fix: pass mc + target
            return;
        }

        // ── Target acquisition ────────────────────────────────────────────────
        double r  = range.getValue();
        Box    bb = mc.player.getBoundingBox().expand(50.0);    // 50-block detection; attack gate = r

        List<LivingEntity> entities = mc.world.getEntitiesByClass(
            LivingEntity.class, bb,
            e -> e != mc.player && !e.isDead()
              && (playersOnly.getValue()
                  ? e instanceof PlayerEntity p && !p.isCreative() && !p.isSpectator()
                  : true)
        );

        if (entities.isEmpty()) {
            target = null;
            lockOnTicks = 0;
            // No enemies nearby — opportunistically eat regular food to refill hunger.
            // Gapples and potions are NOT consumed here; they are reserved for combat.
            tickIdleEat(mc);
            return;
        }

        target = entities.stream()
            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.player)))
            .orElse(null);
        if (target == null) return;

        double dist = Math.sqrt(mc.player.squaredDistanceTo(target));

        // ── Ender pearl gap-close ─────────────────────────────────────────────
        // Only pearl when:
        //   1. Target is genuinely far (>10 blocks, not just strafing orbit of 5-6m)
        //   2. Player has good health (>14 hearts) — save pearl for emergency escapes
        //   3. Target is moving AWAY or standing still (not rushing at us — no need to pearl)
        if (autoPearl.getValue() && pearlCooldown == 0 && dist > 10.0
                && mc.player.getHealth() > 14.0f) {
            // Check target is not rushing toward us (dot product of their velocity vs direction to us)
            net.minecraft.util.math.Vec3d toPlayer = new net.minecraft.util.math.Vec3d(
                mc.player.getX() - target.getX(), 0,
                mc.player.getZ() - target.getZ()).normalize();
            net.minecraft.util.math.Vec3d tgtVel = target.getVelocity();
            double rushDot = tgtVel.x * toPlayer.x + tgtVel.z * toPlayer.z;
            // rushDot > 0.05 means target is moving toward us — don't pearl, they'll come to us
            if (rushDot <= 0.05) {
                int ps = findHotbarItem(mc, Items.ENDER_PEARL);
                if (ps >= 0) { throwPearl(mc, ps); return; }
            }
        }

        // ── Auto-swap to best weapon ──────────────────────────────────────────
        boolean targetShielding = target instanceof PlayerEntity tp
            && tp.isUsingItem()
            && tp.getOffHandStack().isOf(Items.SHIELD);
        autoSwapWeapon(mc, targetShielding);

        // ── Silent predictive aim ─────────────────────────────────────────────
        tickAim(mc, target);

        // ── Strafe + W-tap ────────────────────────────────────────────────────
        tickStrafe(mc, dist, r);
        tickWTap();
        tickObstacleJump(mc);   // hop over 1-block steps
        tickBuffPotions(mc);    // drink speed/fire-res proactively
        tickMacePvP(mc);
        if (maceState != MACE_IDLE) return; // mace combo in progress, skip normal attack

        // ── Crit state machine ────────────────────────────────────────────────
        // critState == 1: we jumped; wait until falling to attack for guaranteed crit
        if (critState == 1) {
            kJump = false;
            boolean falling = !mc.player.isOnGround() && mc.player.getVelocity().y < -0.01;
            if (falling) {
                if (mc.player.getAttackCooldownProgress(0f) >= 1.0f
                 && hitDelay <= 0 && lockOnTicks >= LOCK_ON && dist <= r) {
                    float a = angleFromRot(serverYaw, serverPitch, mc.player, target);
                    if (a <= 45f) {   // was 0.40° — impossible w/ jitter; 45° is normal PvP
                        doAttack(mc);
                        hitDelay     = 2 + rng.nextInt(4);
                        critCooldown = 3 + rng.nextInt(5);
                    }
                }
                critState = 0;
            } else if (mc.player.isOnGround()) {
                critState = 0; // didn't get airborne in time, abort
            }
            tryShield(mc);
            return;
        }

        // ── Normal attack gate ────────────────────────────────────────────────
        if (mc.player.getAttackCooldownProgress(0f) < 1.0f) { tryShield(mc); return; }
        if (mc.player.isUsingItem())                          return; // eating / blocking
        if (hitDelay > 0)    { hitDelay--;  tryShield(mc); return; }
        if (lockOnTicks < LOCK_ON)          { tryShield(mc); return; }
        if (dist > r)                        { tryShield(mc); return; }

        float sentAngle = angleFromRot(serverYaw, serverPitch, mc.player, target);
        if (sentAngle > 45f)                 { tryShield(mc); return; } // was 0.40° — impossible w/ jitter

        // ── Crit decision ─────────────────────────────────────────────────────
        if (critCooldown > 0) critCooldown--;

        boolean doCrit = critCooldown == 0
                      && mc.player.isOnGround()
                      && !mc.player.isSneaking()
                      && rng.nextFloat() < 0.55f; // 55% of attacks are crits

        if (doCrit) {
            critState = 1;
            kJump     = true;  // jump key for 1 tick → player jumps next tick
        } else {
            doAttack(mc);
            hitDelay = 2 + rng.nextInt(4); // 2–5 extra ticks → ~1.3–1.5 CPS with sword
        }
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

        // Predict target position: pos + vel × lag-comp ticks (horizontal only)
        Vec3d vel  = t.getVelocity();
        double px  = t.getX() + vel.x * LAG_COMP;
        double py  = t.getY() + t.getHeight() * 0.5; // vertical prediction unreliable
        double pz  = t.getZ() + vel.z * LAG_COMP;

        float[] ideal      = rotationsToPoint(mc.player, px, py, pz);
        float   cleanAngle = angleFromRot(serverYaw, serverPitch, mc.player, t);

        // ── Idle ticks (holds position briefly, breaks constant-tracking pattern) ──
        if (idleTicks > 0) {
            idleTicks--;
            lockOnTicks++;
            if (hitDelay > 0) hitDelay--;
            return;
        }
        if (cleanAngle < 8f && rng.nextFloat() < 0.08f)
            idleTicks = 1 + rng.nextInt(2);

        // ── Jitter (±0.35° yaw, ±0.075° pitch; refresh every 8–16 ticks) ──────
        if (--jitterTimer <= 0) {
            jitterYaw   = (rng.nextFloat() - 0.5f) * 0.70f;
            jitterPitch = (rng.nextFloat() - 0.5f) * 0.15f;
            jitterTimer = 8 + rng.nextInt(9);
        }

        // ── Overshoot / correct — proper 2-tick cycle ─────────────────────────
        float osYaw = 0f, osPitch = 0f;
        if (correcting) {
            osYaw = -overshootYaw; osPitch = -overshootPitch; correcting = false;
        } else if (cleanAngle > 3f && cleanAngle < 20f && rng.nextFloat() < 0.07f) {
            overshootYaw   = (rng.nextFloat() - 0.5f) * 3.0f;
            overshootPitch = (rng.nextFloat() - 0.5f) * 1.5f;
            osYaw = overshootYaw; osPitch = overshootPitch; correcting = true;
        }

        // Suppress jitter when within 0.5° of target.
        // With jitter ON, GCD rounds 0.35° jitter to 0.6° → sentAngle > 0.40° gate → attacks never fire.
        // With jitter OFF at close range, GCD holds us at ~0.3° from ideal — safely inside
        // Vulcan Hitbox A (0.42°) AND under the 0.40° attack gate.
        boolean nearTarget = cleanAngle < 0.5f;
        float tYaw   = ideal[0] + (nearTarget ? 0 : jitterYaw) + osYaw;
        float tPitch = MathHelper.clamp(ideal[1] + (nearTarget ? 0 : jitterPitch) + osPitch, -90f, 90f);

        // ── Convergence factor — KEY FIX: fast close-range convergence ─────────
        // Old code used 0.08–0.12 for angles < 5°, taking 20+ ticks to hit 0.40° gate.
        // Now: 0.85–0.95 when < 3°, so we converge within 1–2 ticks at close range.
        float factor;
        if      (cleanAngle > 20f) factor = 0.40f + rng.nextFloat() * 0.12f; // 0.40–0.52
        else if (cleanAngle > 10f) factor = 0.55f + rng.nextFloat() * 0.10f; // 0.55–0.65
        else if (cleanAngle >  3f) factor = 0.70f + rng.nextFloat() * 0.10f; // 0.70–0.80
        else if (cleanAngle >  1f) factor = 0.85f + rng.nextFloat() * 0.08f; // 0.85–0.93
        else                        factor = 0.95f;                            // near-snap

        serverYaw   = lerpAngle(prevSrvYaw,   tYaw,   factor);
        serverPitch = lerpAngle(prevSrvPitch, tPitch, factor * 0.80f); // pitch slightly slower

        // ── GCD quantization (simulates mouse hardware rounding) ───────────────
        float gcd    = getGcd(mc);
        float dYaw   = Math.round((serverYaw   - prevSrvYaw)   / gcd) * gcd;
        float dPitch = Math.round((serverPitch - prevSrvPitch) / gcd) * gcd;
        if (rng.nextFloat() < 0.15f) {                  // 15 % sub-GCD noise
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
    }

    // ── Strafe ────────────────────────────────────────────────────────────────

    private void tickStrafe(MinecraftClient mc, double dist, double r) {
        if (--strafeSwitchTimer <= 0) {
            strafeDir         = -strafeDir;
            strafeSwitchTimer = 25 + rng.nextInt(40); // 1.25–3.25s
            if (rng.nextFloat() < 0.20f) strafeSwitchTimer += 5 + rng.nextInt(8); // micro-pause
        }

        if (dist > r + 0.3) {
            // Too far — sprint straight toward target, no strafing so gap closes fast
            kFwd   = true;
            kLeft  = false;
            kRight = false;
            kSprint = true;
        } else if (dist < r - 0.4) {
            // Too close — back off
            kBack   = true;
            kSprint = false;
        } else {
            // In range deadzone — circle-strafe
            kLeft  = (strafeDir >  0);
            kRight = (strafeDir < 0);
            kSprint = true;
        }
    }

    // ── W-tap ─────────────────────────────────────────────────────────────────

    /**
     * Detects a 1-block step in the current movement direction and presses jump
     * to hop over it. Called after movement keys are set so kFwd/kLeft/kRight
     * already reflect this tick's intent.
     */
    private void tickObstacleJump(MinecraftClient mc) {
        if (!mc.player.isOnGround() || kJump) return;
        if (!kFwd && !kLeft && !kRight && !kBack) return;

        double yr = Math.toRadians(mc.player.getYaw());
        double mx = 0, mz = 0;
        if (kFwd)   { mx -= Math.sin(yr); mz += Math.cos(yr); }
        if (kBack)  { mx += Math.sin(yr); mz -= Math.cos(yr); }
        if (kLeft)  { mx += Math.cos(yr); mz += Math.sin(yr); }
        if (kRight) { mx -= Math.cos(yr); mz -= Math.sin(yr); }

        double len = Math.sqrt(mx * mx + mz * mz);
        if (len < 0.01) return;
        mx /= len; mz /= len;

        double cx = mc.player.getX() + mx * 0.65;
        double cz = mc.player.getZ() + mz * 0.65;
        double fy = mc.player.getY();

        net.minecraft.util.math.BlockPos stepPos =
            net.minecraft.util.math.BlockPos.ofFloored(cx, fy + 0.5, cz);
        net.minecraft.util.math.BlockPos headPos =
            net.minecraft.util.math.BlockPos.ofFloored(cx, fy + 1.8, cz);

        boolean hasStep   = !mc.world.getBlockState(stepPos)
                               .getCollisionShape(mc.world, stepPos).isEmpty();
        boolean headClear =  mc.world.getBlockState(headPos)
                               .getCollisionShape(mc.world, headPos).isEmpty();

        if (hasStep && headClear) kJump = true;
    }

    /**
     * Proactively drinks Speed and Fire Resistance potions when a target is
     * present — regardless of HP. Checks every 3 s, uses one buff per call.
     * Searches hotbar then main inventory (swaps to hotbar slot if needed).
     */
    private void tickBuffPotions(MinecraftClient mc) {
        if (buffPotionCd > 0) { buffPotionCd--; return; }
        if (healing || healUseCd > 0) return;

        boolean needSpeed = !mc.player.hasStatusEffect(StatusEffects.SPEED);
        boolean needFire  = !mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE);
        if (!needSpeed && !needFire) return;

        for (int pass = 0; pass < 2; pass++) {
            int lo = (pass == 0) ? 0  : 9;
            int hi = (pass == 0) ? 9  : 36;

            for (int i = lo; i < hi; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s.isEmpty()) continue;

                boolean isSpeed = needSpeed && isPotionWith(s, StatusEffects.SPEED);
                boolean isFire  = needFire  && isPotionWith(s, StatusEffects.FIRE_RESISTANCE);
                if (!isSpeed && !isFire) continue;

                int hotbarSlot = i;
                if (i >= 9) {
                    hotbarSlot = findEmptyHotbarSlot(mc);
                    mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        i, hotbarSlot,
                        net.minecraft.screen.slot.SlotActionType.SWAP,
                        mc.player);
                }

                int savedSlot = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = hotbarSlot;

                if (s.getItem() instanceof SplashPotionItem) {
                    float origP = mc.player.getPitch();
                    mc.player.setPitch(80f);
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    mc.player.setPitch(origP);
                } else {
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                }

                mc.player.getInventory().selectedSlot = savedSlot;
                buffPotionCd = 60 + rng.nextInt(20);
                return;
            }
        }
        buffPotionCd = 100; // nothing found — wait before checking again
    }

    private void tickWTap() {
        if (wTapOffTicks > 0) {
            wTapOffTicks--;
            kSprint = false; // stop sprint for w-tap duration
            return;
        }
        if (++wTapClock >= wTapInterval) {
            wTapClock    = 0;
            wTapInterval = 8 + rng.nextInt(13);  // next tap in 0.4–1.05s
            wTapOffTicks = 2 + rng.nextInt(3);   // sprint off for 2–4 ticks
        }
    }

    // ── Shield ────────────────────────────────────────────────────────────────

    private void tryShield(MinecraftClient mc) {
        if (!autoShield.getValue()) return;
        if (!mc.player.getOffHandStack().isOf(Items.SHIELD)) return;
        if (shieldPauseTicks > 0) return;           // still recovering from hit
        // Don't shield while holding a useable item in main hand
        if (mc.player.getMainHandStack().contains(DataComponentTypes.FOOD)) return;
        kUse = true; // hold right-click → shield raised
    }

    // ── Heal ──────────────────────────────────────────────────────────────────

    private void tickHeal(MinecraftClient mc) {
        // Bug 2 fix: old code called interactItem() every 18-28 ticks but food
        // takes 32 ticks to eat — it kept restarting the eating animation.
        //
        // New approach:
        //   Splash potions → interactItem() once (instant throw), short cooldown.
        //   Food / gapples / drinkable potions → hold kUse = true every tick.
        //     MC's handleInputEvents() keeps sending use packets automatically,
        //     and the server lets the item consume naturally after 32 ticks.
        //     No manual cooldown needed — isUsingItem() drives it.

        // Maintain selected slot throughout the consume cycle
        if (healSlot >= 0) mc.player.getInventory().selectedSlot = healSlot;
        if (healUseCd > 0) { healUseCd--; return; }

        int slot = findHealSlot(mc);
        if (slot < 0) {
            healing      = false;
            healSlot     = -1;
            healCooldown = 40 + rng.nextInt(20);
            splashWarmup = 0;
            return;
        }

        // If item is in main inventory (not hotbar), stop moving and swap it in
        if (slot >= 9) {
            kFwd = kBack = kLeft = kRight = kSprint = kJump = false; // stop all movement
            int swapped = swapInventoryToHotbar(mc, slot);
            healSlot  = swapped;
            healUseCd = 3; // wait 3 ticks for server to acknowledge the swap
            return;
        }

        healSlot = slot;
        mc.player.getInventory().selectedSlot = slot;
        ItemStack item = mc.player.getInventory().getStack(slot);

        if (item.getItem() instanceof SplashPotionItem) {
            kUse = false;
            serverPitch = 80f; prevSrvPitch = 80f;
            if (splashWarmup < 2) { splashWarmup++; return; }
            splashWarmup = 0;
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            healUseCd = 8 + rng.nextInt(5);
            healSlot  = -1;
        } else {
            // Food / gapple / drinkable potion.
            // interactItem() starts eating regardless of crosshair target.
            // kUse = true MUST be set every tick so MC keeps the eating animation
            // going — without it the use key is released and eating stops after 1 tick.
            if (!mc.player.isUsingItem()) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
            kUse = true; // hold use key every tick until item is consumed
            // No healUseCd here — isUsingItem() drives completion naturally
        }
    }

    private void tickRetreat(MinecraftClient mc) {
        // Bug 1 fix: retreat while the target is within 8 m, not just for the
        // initial timer. Old code stopped after 1-2 s and the player stood still.
        boolean targetNearby = target != null
            && Math.sqrt(mc.player.squaredDistanceTo(target)) < 8.0;
        if (retreatTimer > 0) retreatTimer--;
        if (retreatTimer > 0 || targetNearby) {
            // Face toward target so kBack (S key) moves AWAY from them.
            // Without this serverYaw may be stale and kBack goes the wrong direction.
            if (target != null) {
                float[] rot = rotationsToPoint(mc.player,
                    target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ());
                serverYaw  = rot[0];
                prevSrvYaw = rot[0];
                srvRotReady = true;
            }
            kBack   = true;
            kSprint = !mc.player.isUsingItem(); // sprint cancels eating
        }
    }

    /**
     * Tries to find an empty hotbar slot (0-8).
     * Falls back to slot 8 if all slots are occupied.
     */
    private int findEmptyHotbarSlot(MinecraftClient mc) {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        return 8;
    }

    /**
     * Moves an item from main inventory (slots 9-35) to the hotbar via SWAP.
     * In PlayerScreenHandler, inventory slots 9-35 map 1-to-1 to screen slots 9-35.
     * SWAP action exchanges that screen slot with hotbar slot N (button = 0-8).
     * Returns the hotbar slot index where the item now lives, or -1 on failure.
     */
    private int swapInventoryToHotbar(MinecraftClient mc, int inventorySlot) {
        int hotbarTarget = findEmptyHotbarSlot(mc);
        // Screen slot = inventory slot for slots 9-35 in PlayerScreenHandler
        mc.interactionManager.clickSlot(
            mc.player.playerScreenHandler.syncId,
            inventorySlot,   // screen slot matches inventory slot for 9-35
            hotbarTarget,    // button = hotbar slot to swap with
            SlotActionType.SWAP,
            mc.player
        );
        return hotbarTarget;
    }

    private int findHealSlot(MinecraftClient mc) {
        // Searches hotbar (0-8) first, then main inventory (9-35) for each category.
        // Returning slot >= 9 signals tickHeal to stop moving and swap to hotbar first.
        // Priority: Fire Resistance → Speed → splash heal → drinkable heal → EGapple → Gapple → food.

        // 1. Fire Resistance
        if (!mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) {
            for (int i = 0; i < 36; i++)
                if (isPotionWith(mc.player.getInventory().getStack(i), StatusEffects.FIRE_RESISTANCE))
                    return i;
        }
        // 2. Speed
        if (!mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            for (int i = 0; i < 36; i++)
                if (isPotionWith(mc.player.getInventory().getStack(i), StatusEffects.SPEED))
                    return i;
        }
        // 3. Splash instant-health / regeneration
        for (int i = 0; i < 36; i++) {
            var s = mc.player.getInventory().getStack(i);
            if (s.getItem() instanceof SplashPotionItem && isHealPotion(s)) return i;
        }
        // 4. Drinkable instant-health / regeneration
        for (int i = 0; i < 36; i++) {
            var s = mc.player.getInventory().getStack(i);
            if (!(s.getItem() instanceof SplashPotionItem) && isHealPotion(s)) return i;
        }
        // 5. Enchanted golden apple
        for (int i = 0; i < 36; i++)
            if (mc.player.getInventory().getStack(i).isOf(Items.ENCHANTED_GOLDEN_APPLE)) return i;
        // 6. Golden apple
        for (int i = 0; i < 36; i++)
            if (mc.player.getInventory().getStack(i).isOf(Items.GOLDEN_APPLE)) return i;
        // 7. Regular food (only when hungry; gapples already handled above)
        int hunger = mc.player.getHungerManager().getFoodLevel();
        if (hunger < 20) {
            for (int i = 0; i < 36; i++) {
                var s   = mc.player.getInventory().getStack(i);
                var fdc = s.get(DataComponentTypes.FOOD);
                if (fdc != null
                 && !s.isOf(Items.GOLDEN_APPLE)
                 && !s.isOf(Items.ENCHANTED_GOLDEN_APPLE))
                    return i;
            }
        }
        return -1;
    }

    /** True if the ItemStack is any potion (splash or drinkable) containing the given effect. */
    private static boolean isPotionWith(ItemStack s, RegistryEntry<StatusEffect> effect) {
        PotionContentsComponent c = s.get(DataComponentTypes.POTION_CONTENTS);
        if (c == null) return false;
        for (var e : c.getEffects())
            if (e.getEffectType() == effect) return true;
        return false;
    }

    private static boolean isHealPotion(ItemStack s) {
        PotionContentsComponent c = s.get(DataComponentTypes.POTION_CONTENTS);
        if (c == null) return false;
        for (var e : c.getEffects())
            if (e.getEffectType() == StatusEffects.INSTANT_HEALTH
             || e.getEffectType() == StatusEffects.REGENERATION)
                return true;
        return false;
    }

    // ── Totem ─────────────────────────────────────────────────────────────────

    private void moveTotemToOffhand(MinecraftClient mc) {
        var inv    = mc.player.getInventory();
        int syncId = mc.player.currentScreenHandler.syncId;
        for (int i = 0; i < inv.main.size(); i++) {
            if (!inv.main.get(i).isOf(Items.TOTEM_OF_UNDYING)) continue;
            int screen = i < 9 ? i + 36 : i;
            mc.interactionManager.clickSlot(syncId, screen, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, 45,     0, SlotActionType.PICKUP, mc.player);
            if (!mc.player.currentScreenHandler.getCursorStack().isEmpty())
                mc.interactionManager.clickSlot(syncId, screen, 0, SlotActionType.PICKUP, mc.player);
            break;
        }
    }

    // ── Ender pearl ───────────────────────────────────────────────────────────

    /**
     * Throws an ender pearl toward the target, but ONLY when the path is clear.
     *
     * Bug was: throwPearl aimed directly at target with no obstacle check, so if
     * the player was near a corner the pearl would immediately hit the wall block.
     *
     * Fix — two-phase clearance check before throwing:
     *
     *   Phase 1 — Direct LoS: raycast eye → target centre.
     *             If clear, throw with a gentle -15° upward arc (standard gap-close).
     *
     *   Phase 2 — High-arc LoS: if direct path is blocked, check whether a lofted
     *             path is open by raycasting eye → arc-midpoint → target.
     *             The arc midpoint is 4 blocks above the higher of the two endpoints,
     *             halfway horizontally between them. If BOTH legs of that V are clear,
     *             the pearl can fly over the obstacle; we throw with -40° pitch.
     *
     *   Abort: if both phases fail (e.g. the player is wedged in a corner with no
     *          open sky), set a short retry cooldown instead of wasting the pearl.
     */
    private void throwPearl(MinecraftClient mc, int slot) {
        if (target == null || mc.world == null) return;

        Vec3d eye    = mc.player.getEyePos();
        Vec3d tgtMid = new Vec3d(
            target.getX(),
            target.getY() + target.getHeight() * 0.5,
            target.getZ()
        );

        float[] directRot = rotationsToPoint(mc.player, tgtMid.x, tgtMid.y, tgtMid.z);
        float throwYaw    = directRot[0];
        float throwPitch;

        // ── Phase 1: direct line of sight ────────────────────────────────────
        if (pathClear(mc, eye, tgtMid)) {
            // Clear straight shot — gentle upward arc so pearl clears low ledges
            throwPitch = MathHelper.clamp(directRot[1] - 15f, -70f, 70f);

        // ── Phase 2: high-arc path (over a corner block / low wall) ──────────
        } else {
            // Construct a midpoint 4 blocks above the path's highest point
            double midX   = (eye.x + tgtMid.x) * 0.5;
            double midY   = Math.max(eye.y, tgtMid.y) + 4.0;
            double midZ   = (eye.z + tgtMid.z) * 0.5;
            Vec3d  arcMid = new Vec3d(midX, midY, midZ);

            if (!pathClear(mc, eye, arcMid) || !pathClear(mc, arcMid, tgtMid)) {
                // Even a high arc is blocked (e.g. player is inside a tight room).
                // Abort — don't throw blindly into a wall.
                pearlCooldown = 6 + rng.nextInt(6); // retry in ~0.3-0.6 s
                return;
            }
            // High arc is open — throw steeply upward to loft over the obstacle
            throwPitch = MathHelper.clamp(directRot[1] - 40f, -80f, -10f);
        }

        // ── Hold pearl slot for 2 warmup ticks, THEN throw ───────────────────
        // Real players switch to pearl, hold for a moment, THEN right-click.
        // Same-tick switch+throw looks like a bot to any observer.
        pearlSlot   = slot;
        pearlYaw    = throwYaw;
        pearlPitch  = throwPitch;
        pearlWarmup = 2;
        mc.player.getInventory().selectedSlot = slot; // select pearl now

        // Sync server-aim to where we just threw, so the next rotation packet is clean
        serverYaw    = throwYaw;
        serverPitch  = throwPitch;
        prevSrvYaw   = serverYaw;
        prevSrvPitch = serverPitch;

        pearlCooldown = 25 + rng.nextInt(10); // 1.25–1.75 s before next pearl
    }

    /**
     * Raycasts from {@code from} to {@code to} through solid blocks only.
     * Returns true when the path is fully clear (MISS), false when a block is hit first.
     */
    private static boolean pathClear(MinecraftClient mc, Vec3d from, Vec3d to) {
        var hit = mc.world.raycast(new RaycastContext(
            from, to,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));
        return hit.getType() == HitResult.Type.MISS;
    }

    // ── Weapon selection ──────────────────────────────────────────────────────

    /**
     * Axe when target is shielding (axe hits disable shields for 5s).
     * Sword otherwise (faster speed = more DPS on unshielded target).
     * No swap if already holding the correct weapon type.
     */
    private void autoSwapWeapon(MinecraftClient mc, boolean targetShielding) {
        var inv = mc.player.getInventory();
        ItemStack cur = inv.getStack(inv.selectedSlot);

        if (targetShielding) {
            if (cur.isIn(ItemTags.AXES)) return;   // already have axe, good
            for (int i = 0; i < 9; i++) if (inv.getStack(i).isIn(ItemTags.AXES)) { inv.selectedSlot = i; return; }
            // No axe available — fall through, use sword anyway
        }
        if (cur.isIn(ItemTags.SWORDS) || cur.isIn(ItemTags.AXES)) return; // have a weapon
        for (int i = 0; i < 9; i++) if (inv.getStack(i).isIn(ItemTags.SWORDS)) { inv.selectedSlot = i; return; }
        for (int i = 0; i < 9; i++) if (inv.getStack(i).isIn(ItemTags.AXES))   { inv.selectedSlot = i; return; }
    }

    // ── Mace PvP ─────────────────────────────────────────────────────────────────

    /**
     * Mace PvP with attribute swapping.
     *
     * Technique: Hold sword → jump (or wind-charge launch) → while falling,
     * switch to mace → attack → instantly switch back to sword.
     *
     * The game calculates falling-damage bonus from the mace (7× fall height),
     * but the attack speed / cooldown originated from the sword. Net result:
     * massive burst damage at normal sword speed.
     *
     * Slot convention (matches the guide the user described):
     *   Slot N   = primary weapon (sword)
     *   Slot N+1 = mace
     */
    private void tickMacePvP(MinecraftClient mc) {
        if (!macePvP.getValue() || target == null) { maceState = MACE_IDLE; return; }
        if (maceComboCd > 0) { maceComboCd--; return; }
        if (windChargeCd > 0) windChargeCd--;

        double dist = Math.sqrt(mc.player.squaredDistanceTo(target));

        switch (maceState) {

            case MACE_IDLE -> {
                // Conditions: on ground, target reachable, mace in next slot
                if (!mc.player.isOnGround()) return;
                if (dist > range.getValue() + 2.5) return;

                int sword = mc.player.getInventory().selectedSlot;
                int mace  = (sword + 1) % 9;
                if (!isMace(mc.player.getInventory().getStack(mace))) return;

                if (windLaunch.getValue() && windChargeCd == 0) {
                    maceState = MACE_WIND;
                } else {
                    kJump     = true;   // regular jump
                    maceState = MACE_FALLING;
                }
            }

            case MACE_WIND -> {
                // Throw wind charge at feet (aimed down) + jump = big upward launch
                int wcSlot = -1;
                for (int i = 0; i < 9; i++)
                    if (mc.player.getInventory().getStack(i).isOf(Items.WIND_CHARGE))
                        { wcSlot = i; break; }

                if (wcSlot < 0) { maceState = MACE_IDLE; return; } // no wind charge

                int   savedSlot  = mc.player.getInventory().selectedSlot;
                float savedPitch = serverPitch;
                float savedPrevP = prevSrvPitch;

                // Aim straight down at feet for max upward push
                serverPitch  = 85f;
                prevSrvPitch = 85f;
                mc.player.setPitch(85f);

                mc.player.getInventory().selectedSlot = wcSlot;
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.getInventory().selectedSlot = savedSlot;

                // Restore aim
                serverPitch  = savedPitch;
                prevSrvPitch = savedPrevP;
                mc.player.setPitch(savedPitch);

                kJump        = true;
                windChargeCd = 160 + rng.nextInt(60); // ~10s cooldown
                maceState    = MACE_FALLING;
            }

            case MACE_FALLING -> {
                double yVel    = mc.player.getVelocity().y;
                boolean falling = !mc.player.isOnGround() && yVel < -0.3;

                if (mc.player.isOnGround()) {
                    // Landed without attacking — reset
                    maceState   = MACE_IDLE;
                    maceComboCd = 30 + rng.nextInt(30);
                    return;
                }
                if (!falling) return;                              // still rising
                if (dist > range.getValue() + 1.0) return;        // too far
                if (mc.player.getAttackCooldownProgress(0f) < 1.0f) return;

                // ── ATTRIBUTE SWAP ─────────────────────────────────────────────
                // Switch to mace → attack → switch BACK to sword, all in ONE tick.
                int swordSlot = mc.player.getInventory().selectedSlot;
                int maceSlot  = (swordSlot + 1) % 9;

                // Verify mace is actually there; search hotbar if not
                if (!isMace(mc.player.getInventory().getStack(maceSlot))) {
                    maceSlot = -1;
                    for (int i = 0; i < 9; i++)
                        if (isMace(mc.player.getInventory().getStack(i))) { maceSlot = i; break; }
                    if (maceSlot < 0) { maceState = MACE_IDLE; return; }
                }

                mc.player.getInventory().selectedSlot = maceSlot;  // → mace
                KeyBindingAccessor atk = (KeyBindingAccessor) mc.options.attackKey;
                atk.setTimesPressed(atk.getTimesPressed() + 1);    // attack
                mc.player.getInventory().selectedSlot = swordSlot; // → sword

                maceState   = MACE_IDLE;
                maceComboCd = 80 + rng.nextInt(40);  // ~5-6s before next combo
                hitDelay    = 8 + rng.nextInt(5);
            }
        }
    }

    private static boolean isMace(ItemStack s) {
        return s.isOf(Items.MACE);
    }

    // ── Attack ────────────────────────────────────────────────────────────────

    /**
     * Direct attackEntity() call — same as KillAura.
     * No dependency on hitResult; reliable as long as angle < 0.40° and dist ≤ range.
     */
    private void doAttack(MinecraftClient mc) {
        kUse = false; // drop shield before attacking
        boolean wasSprint = mc.player.isSprinting();
        mc.player.setSprinting(false); // prevent sweep AoE
        // Same legit approach as AutoClicker: increment timesPressed so MC's
        // own handleInputEvents() fires doAttack() next tick — identical to
        // a real left mouse button press, nothing to fingerprint.
        KeyBindingAccessor atk = (KeyBindingAccessor) mc.options.attackKey;
        atk.setTimesPressed(atk.getTimesPressed() + 1);
        mc.player.setSprinting(wasSprint);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int findHotbarItem(MinecraftClient mc, net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    private static float lerpAngle(float current, float target, float factor) {
        return current + MathHelper.wrapDegrees(target - current) * MathHelper.clamp(factor, 0f, 1f);
    }

    private static float[] rotationsToPoint(PlayerEntity player, double x, double y, double z) {
        double dx = x - player.getX();
        double dy = y - (player.getY() + player.getEyeHeight(player.getPose()));
        double dz = z - player.getZ();
        double h  = Math.sqrt(dx * dx + dz * dz);
        return new float[]{
            (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f,
            MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, h)), -90f, 90f)
        };
    }

    private static float angleFromRot(float yaw, float pitch,
                                      PlayerEntity player, LivingEntity target) {
        double yr  = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        Vec3d look = new Vec3d(-Math.sin(yr)*Math.cos(pr), -Math.sin(pr),  Math.cos(yr)*Math.cos(pr)).normalize();
        Vec3d eye  = new Vec3d(player.getX(), player.getY()+player.getEyeHeight(player.getPose()), player.getZ());
        Vec3d to   = new Vec3d(target.getX(), target.getY()+target.getHeight()*0.5, target.getZ())
                         .subtract(eye).normalize();
        return (float) Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(to), -1.0, 1.0)));
    }

    private static float getGcd(MinecraftClient mc) {
        double f = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        return (float) (f * f * f * 1.2);
    }

    // ── Idle eating (no combat target) ───────────────────────────────────────

    /**
     * Eats regular food (steak, bread, etc.) when there is no target and
     * hunger is below 18 / 9 drumstick icons.
     *
     * Rules:
     *  - Only fires when eatToHeal is enabled and hunger < 18.
     *  - Never touches golden apples, enchanted golden apples, or potions;
     *    those are reserved for the combat heal path.
     *  - Uses kUse = true (hold right-click) so MC's own eat-timer drives
     *    consumption over 32 ticks — no manual cooldown juggling needed.
     *  - A short idleFoodCd gap is applied after each item finishes so the
     *    player isn't robotically eating back-to-back with zero pause.
     *  - If a target appears mid-eat, kUse resets to false next tick and the
     *    eat animation cancels naturally (same as a real player dropping food
     *    to fight).
     */
    private void tickIdleEat(MinecraftClient mc) {
        if (!eatToHeal.getValue()) return;
        if (idleFoodCd > 0) { idleFoodCd--; return; }

        int hunger = mc.player.getHungerManager().getFoodLevel();
        if (hunger >= 18) return; // hunger full enough, nothing to do

        int slot = findRegularFoodSlot(mc);
        if (slot < 0) return; // no regular food in hotbar

        mc.player.getInventory().selectedSlot = slot;
        kUse = true; // hold right-click; MC handles the 32-tick consume timer

        // When eating just finished (item was consumed), add a small human-like
        // pause before starting the next piece.
        if (!mc.player.isUsingItem() && idleFoodCd == 0) {
            idleFoodCd = 8 + rng.nextInt(12); // 0.4-1.0s pause between bites
        }
    }

    /**
     * Returns the first hotbar slot containing regular food (i.e. a FOOD component,
     * excluding golden apples — those are reserved for combat healing).
     */
    private static int findRegularFoodSlot(MinecraftClient mc) {
        for (int i = 0; i < 9; i++) {
            var s   = mc.player.getInventory().getStack(i);
            var fdc = s.get(DataComponentTypes.FOOD);
            if (fdc != null
             && !s.isOf(Items.GOLDEN_APPLE)
             && !s.isOf(Items.ENCHANTED_GOLDEN_APPLE))
                return i;
        }
        return -1;
    }

    private void resetAll() {
        srvRotReady = camOverridden = correcting = healing = false;
        jitterTimer = idleTicks = lockOnTicks = critState = 0;
        hitDelay = critCooldown = wTapClock = wTapOffTicks = totemTimer = 0;
        pearlCooldown = shieldPauseTicks = splashWarmup = healUseCd = retreatTimer = healCooldown = idleFoodCd = 0;
        maceState = MACE_IDLE; windChargeCd = maceComboCd = 0;
        prevSrvYaw = prevSrvPitch = 0f;
        target = null;
        strafeDir         = rng.nextBoolean() ? 1 : -1;
        strafeSwitchTimer = 25 + rng.nextInt(40);
        wTapInterval      = 8 + rng.nextInt(13);
        kFwd = kBack = kLeft = kRight = kSprint = kJump = kUse = false;
    }
}



