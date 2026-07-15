package com.azeluxclient.module.combat;

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
import net.minecraft.entity.effect.StatusEffects;
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

import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * AutoPvP — full legit PvP automation.
 *
 * What was wrong before:
 *   • timesPressed attack relied on hitResult → missed when aim not converged
 *   • Aim lerp factor 0.08–0.12 for small angles → took 20+ ticks to reach 0.40°
 *   • hitDelay 5–9 ticks extra → ~1 attack per second instead of ~1.5
 *   • Heal fired at 4f (2 hearts) — too late, usually dead
 *   • No shield, no totem, no pearl, no axe-swap logic
 *
 * Fixed / added:
 *   • attackEntity() directly, same as KillAura — reliable, no hitResult dependency
 *   • Faster aim: 0.85–0.95 factor when angle < 3°, attack gate reliably hit by tick 4–5
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
 *   Hitbox A  — jitter ±0.35°, attack gate 0.40°
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
    private int     retreatTimer  = 0;
    private int     splashWarmup  = 0;

    // ── Totem ─────────────────────────────────────────────────────────────────
    private int totemTimer = 0;

    // ── Shield ────────────────────────────────────────────────────────────────
    private int shieldPauseTicks = 0;  // brief pause before re-raising shield after hit

    // ── Ender pearl ───────────────────────────────────────────────────────────
    private int pearlCooldown = 0;

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
    private static final int LAG_COMP = 3;   // ticks of lag compensation for prediction

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
        MinecraftClient mc = mc();
        if (mc != null && mc.player != null) {
            if (camOverridden) {
                mc.player.setYaw(savedCamYaw);
                mc.player.setPitch(savedCamPitch);
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
            mc.player.setPitch(savedCamPitch);
            camOverridden = false;
        }

        // Reset all virtual keys; sub-systems re-assert what they need this tick
        kFwd = kBack = kLeft = kRight = kSprint = kJump = kUse = false;

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
        if (eatToHeal.getValue()) {
            if (!healing && curHp <= 16.0f) {                  // < 8 hearts
                healing      = true;
                retreatTimer = 20 + rng.nextInt(20);           // 1–2s retreat
            }
            if (healing  && curHp >= 28.0f) healing = false;  // > 14 hearts
        }

        if (healing) {
            tickHeal(mc);
            tickRetreat();
            return;
        }

        // ── Target acquisition ────────────────────────────────────────────────
        double r  = range.getValue();
        Box    bb = mc.player.getBoundingBox().expand(r + 4.0); // wider scan, attack only within r

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

        double dist = Math.sqrt(mc.player.squaredDistanceTo(target));

        // ── Ender pearl gap-close (target > 5m, pearl off cooldown) ──────────
        if (autoPearl.getValue() && dist > 5.0 && pearlCooldown == 0) {
            int ps = findHotbarItem(mc, Items.ENDER_PEARL);
            if (ps >= 0) { throwPearl(mc, ps); return; }
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

        // ── Crit state machine ────────────────────────────────────────────────
        // critState == 1: we jumped; wait until falling to attack for guaranteed crit
        if (critState == 1) {
            kJump = false;
            boolean falling = !mc.player.isOnGround() && mc.player.getVelocity().y < -0.01;
            if (falling) {
                if (mc.player.getAttackCooldownProgress(0f) >= 1.0f
                 && hitDelay <= 0 && lockOnTicks >= LOCK_ON && dist <= r) {
                    float a = angleFromRot(serverYaw, serverPitch, mc.player, target);
                    if (a <= 0.40f) {
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
        if (sentAngle > 0.40f)               { tryShield(mc); return; }

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

        float tYaw   = ideal[0] + jitterYaw + osYaw;
        float tPitch = MathHelper.clamp(ideal[1] + jitterPitch + osPitch, -90f, 90f);

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

        if (dist > r + 0.5) {
            // Too far — sprint toward target
            kFwd = true; kSprint = true;
        } else if (dist < r - 0.5) {
            // Too close — back off, no sprint
            kBack = true;
        } else {
            // In range — circle-strafe
            kLeft  = (strafeDir >  0);
            kRight = (strafeDir < 0);
            kSprint = true;
        }
    }

    // ── W-tap ─────────────────────────────────────────────────────────────────

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
        kUse = false; // don't accidentally shield/eat via use-key; we call interactItem directly
        if (healUseCd > 0) { healUseCd--; return; }

        int slot = findHealSlot(mc);
        if (slot < 0) { healing = false; return; } // nothing found, give up

        mc.player.getInventory().selectedSlot = slot;
        ItemStack item = mc.player.getInventory().getStack(slot);

        // Splash potion: aim down at feet before throwing (2 warmup ticks)
        if (item.getItem() instanceof SplashPotionItem) {
            serverPitch = 80f; prevSrvPitch = 80f;
            if (splashWarmup < 2) { splashWarmup++; return; }
            splashWarmup = 0;
        }

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        healUseCd = 18 + rng.nextInt(10); // wait ~0.9–1.4s before next use attempt
    }

    private void tickRetreat() {
        if (retreatTimer <= 0) return;
        retreatTimer--;
        kBack   = true;
        kSprint = true;
    }

    private int findHealSlot(MinecraftClient mc) {
        // Priority: splash instant-health / regen → enchanted gapple → gapple → food
        for (int i = 0; i < 9; i++) {
            var s = mc.player.getInventory().getStack(i);
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

    private void throwPearl(MinecraftClient mc, int slot) {
        int saved = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;
        // Aim slightly downward toward target's feet for proper arc
        float[] rot = rotationsToPoint(mc.player,
            target.getX(), target.getY(), target.getZ());
        serverYaw = rot[0]; serverPitch = Math.min(rot[1] + 10f, 80f);
        prevSrvYaw = serverYaw; prevSrvPitch = serverPitch;
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.getInventory().selectedSlot = saved;
        pearlCooldown = 25 + rng.nextInt(10); // 1.25–1.75s before next pearl
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

    // ── Attack ────────────────────────────────────────────────────────────────

    /**
     * Direct attackEntity() call — same as KillAura.
     * No dependency on hitResult; reliable as long as angle < 0.40° and dist ≤ range.
     */
    private void doAttack(MinecraftClient mc) {
        kUse = false; // drop shield before attacking
        boolean wasSprint = mc.player.isSprinting();
        mc.player.setSprinting(false); // prevent sweep AoE
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
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

    private void resetAll() {
        srvRotReady = camOverridden = correcting = healing = false;
        jitterTimer = idleTicks = lockOnTicks = critState = 0;
        hitDelay = critCooldown = wTapClock = wTapOffTicks = totemTimer = 0;
        pearlCooldown = shieldPauseTicks = splashWarmup = healUseCd = retreatTimer = 0;
        prevSrvYaw = prevSrvPitch = 0f;
        target = null;
        strafeDir         = rng.nextBoolean() ? 1 : -1;
        strafeSwitchTimer = 25 + rng.nextInt(40);
        wTapInterval      = 8 + rng.nextInt(13);
        kFwd = kBack = kLeft = kRight = kSprint = kJump = kUse = false;
    }
}
