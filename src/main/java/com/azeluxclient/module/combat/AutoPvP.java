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
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.util.Hand;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

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

    // ── Combat state ──────────────────────────────────────────────────────────
    private final Random rng          = new Random();
    private LivingEntity target       = null;
    private int          hitDelay     = 0;
    private int          critCooldown = 0;
    private int          critState    = 0; // 0 = idle, 1 = jumped (waiting to fall)

    // ── Perspective ───────────────────────────────────────────────────────────
    private Perspective prevPerspective = null;

    // ── Hold-C ────────────────────────────────────────────────────────────────
    private boolean holdActive = false;

    // ── Strafe ────────────────────────────────────────────────────────────────
    private int strafeDir         = 1;
    private int strafeSwitchTimer = 0;

    // ── W-tap ─────────────────────────────────────────────────────────────────
    private int wTapClock    = 0;
    private int wTapInterval = 14;
    private int wTapOffTicks = 0;

    // ── Heal / retreat ────────────────────────────────────────────────────────
    private boolean healing      = false;
    private int     healUseCd    = 0;
    private int     retreatTimer = 0;
    private int     splashWarmup = 0;

    // ── Keyboard flags ────────────────────────────────────────────────────────
    private volatile boolean kForward = false;
    private volatile boolean kBack    = false;
    private volatile boolean kLeft    = false;
    private volatile boolean kRight   = false;
    private volatile boolean kSprint  = false;
    private volatile boolean kJump    = false;

    private static final int LAG_COMP = 3;

    public AutoPvP() {
        super("AutoPvP", "Hold C — legit attack input, real keyboard movement.", Category.COMBAT);

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Silent aim applied before movement packets
            if (isEnabled() && srvRotReady) {
                savedCamYaw   = client.player.getYaw();
                savedCamPitch = client.player.getPitch();
                camOverridden = true;
                client.player.setYaw(serverYaw);
                client.player.setPitch(serverPitch);
                client.player.setHeadYaw(serverYaw);
                client.player.bodyYaw = serverYaw;
            }

            // Only override keys when enabled — never call setPressed when disabled
            // (calling setPressed(false) every tick overrides real user key presses)
            if (isEnabled()) {
                client.options.forwardKey.setPressed(kForward);
                client.options.backKey.setPressed(kBack);
                client.options.leftKey.setPressed(kLeft);
                client.options.rightKey.setPressed(kRight);
                client.options.sprintKey.setPressed(kSprint);
                client.options.jumpKey.setPressed(kJump);
            }
        });
    }

    @Override
    public void onEnable() {
        resetAll();
        MinecraftClient mc = mc();
        if (mc != null) {
            if (thirdPerson.getValue()) {
                prevPerspective = mc.options.getPerspective();
                mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
            Freelook.setActive(true, mc);
        }
    }

    @Override
    public void onDisable() {
        // Release all keys immediately
        kForward = kBack = kLeft = kRight = kSprint = kJump = false;

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

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        // Hold-C gate
        long win   = mc.getWindow().getHandle();
        boolean cHeld = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;
        if (cHeld && !holdActive) { holdActive = true;  if (!isEnabled()) toggle(); }
        else if (!cHeld && holdActive) { holdActive = false; if (isEnabled()) toggle(); }
        if (!isEnabled()) return;

        // Restore visual camera before rendering
        if (camOverridden) {
            mc.player.setYaw(savedCamYaw);
            mc.player.setPitch(savedCamPitch);
            camOverridden = false;
        }

        // Reset keys at start of tick; sub-systems re-assert what they need
        kForward = kBack = kLeft = kRight = kSprint = kJump = false;

        // Heal flow
        float hp = mc.player.getHealth();
        if (eatToHeal.getValue()) {
            if (!healing && hp <= 4.0f)  healing = true;
            if (healing  && hp >= 10.0f) healing = false;
        }
        if (healing) { tickHeal(mc); tickRetreat(mc); return; }

        // Target acquisition
        Box bb = mc.player.getBoundingBox().expand(range.getValue() + 3.0); // scan slightly wider than attack range
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

        // Sub-systems
        tickAim(mc, target);
        tickStrafe(mc);
        tickWTap();

        // ── Crit state machine ────────────────────────────────────────────────
        if (critState == 1) {
            kJump = false;
            boolean airborne = !mc.player.isOnGround();
            boolean falling  = mc.player.getVelocity().y < 0;

            if (airborne && falling) {
                if (mc.player.getAttackCooldownProgress(0f) >= 1.0f
                        && hitDelay <= 0 && lockOnTicks >= LOCK_ON) {
                    float ang = angleFromRot(serverYaw, serverPitch, mc.player, target);
                    if (ang <= 0.40f) {
                        doAttack(mc);
                        hitDelay     = 5 + rng.nextInt(5);
                        critCooldown = 3 + rng.nextInt(5);
                    }
                }
                critState = 0;
            } else if (mc.player.isOnGround()) {
                critState = 0;
            }
            return;
        }

        // Normal attack gate
        if (mc.player.getAttackCooldownProgress(0f) < 1.0f) return;
        if (mc.player.isUsingItem()) return;
        if (hitDelay > 0) { hitDelay--; return; }
        if (lockOnTicks < LOCK_ON) return;

        // Must be within actual attack range to hit
        double dist = Math.sqrt(mc.player.squaredDistanceTo(target));
        if (dist > range.getValue()) return;

        float sentAngle = angleFromRot(serverYaw, serverPitch, mc.player, target);
        if (sentAngle > 0.40f) return;

        if (critCooldown > 0) critCooldown--;
        boolean doJumpCrit = critCooldown == 0
                          && mc.player.isOnGround()
                          && !mc.player.isSneaking()
                          && rng.nextFloat() < 0.55f;

        if (doJumpCrit) {
            critState = 1;
            kJump     = true;
        } else {
            doAttack(mc);
            hitDelay = 5 + rng.nextInt(5);
        }
    }

    // ── Aim ───────────────────────────────────────────────────────────────────

    private void tickAim(MinecraftClient mc, LivingEntity t) {
        if (!srvRotReady) {
            serverYaw    = savedCamYaw != 0 ? savedCamYaw : mc.player.getYaw();
            serverPitch  = savedCamPitch != 0 ? savedCamPitch : mc.player.getPitch();
            prevSrvYaw   = serverYaw;
            prevSrvPitch = serverPitch;
            srvRotReady  = true;
        }

        Vec3d vel = t.getVelocity();
        double px = t.getX() + vel.x * LAG_COMP;
        double py = t.getY() + t.getHeight() * 0.5;
        double pz = t.getZ() + vel.z * LAG_COMP;

        float[] ideal      = rotationsToPoint(mc.player, px, py, pz);
        float   cleanAngle = angleFromRot(serverYaw, serverPitch, mc.player, t);

        if (idleTicks > 0) { idleTicks--; lockOnTicks++; if (hitDelay > 0) hitDelay--; return; }
        if (cleanAngle < 10f && rng.nextFloat() < 0.10f) idleTicks = 1 + rng.nextInt(2);

        if (--jitterTimer <= 0) {
            jitterYaw   = (rng.nextFloat() - 0.5f) * 0.70f;
            jitterPitch = (rng.nextFloat() - 0.5f) * 0.15f;
            jitterTimer = 8 + rng.nextInt(9);
        }

        float osYaw = 0f, osPitch = 0f;
        if (correcting) {
            osYaw = -overshootYaw; osPitch = -overshootPitch; correcting = false;
        } else if (cleanAngle > 3f && cleanAngle < 20f && rng.nextFloat() < 0.08f) {
            overshootYaw   = (rng.nextFloat() - 0.5f) * 3.5f;
            overshootPitch = (rng.nextFloat() - 0.5f) * 2.0f;
            osYaw = overshootYaw; osPitch = overshootPitch; correcting = true;
        }

        float tYaw   = ideal[0] + jitterYaw + osYaw;
        float tPitch = MathHelper.clamp(ideal[1] + jitterPitch + osPitch, -90f, 90f);

        float factor;
        if      (cleanAngle > 15f) factor = 0.32f + rng.nextFloat() * 0.13f;
        else if (cleanAngle >  5f) factor = 0.18f + rng.nextFloat() * 0.10f;
        else                        factor = 0.08f + rng.nextFloat() * 0.07f;

        serverYaw = lerpAngle(prevSrvYaw, tYaw, factor);

        float pitchDelta = Math.abs(MathHelper.wrapDegrees(tPitch - prevSrvPitch));
        serverPitch = pitchDelta > 0.5f
            ? lerpAngle(prevSrvPitch, tPitch, factor * 0.75f)
            : prevSrvPitch;

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
        lockOnTicks++;
    }

    // ── Strafe ────────────────────────────────────────────────────────────────

    private void tickStrafe(MinecraftClient mc) {
        if (target == null || mc.player == null) return;

        double dist       = Math.sqrt(mc.player.squaredDistanceTo(target));
        double idealRange = range.getValue();

        if (--strafeSwitchTimer <= 0) {
            strafeDir         = -strafeDir;
            strafeSwitchTimer = 25 + rng.nextInt(40);
        }

        if (dist > idealRange + 0.4) {
            // Too far — stop strafing, sprint straight toward target to close gap
            kForward = true;
            kLeft    = false;
            kRight   = false;
            kSprint  = true;
        } else if (dist < idealRange - 0.4) {
            // Too close — back off, no sprint
            kBack    = true;
            kForward = false;
            kLeft    = false;
            kRight   = false;
            kSprint  = false;
        } else {
            // In range — strafe left or right while maintaining position
            kLeft   = (strafeDir > 0);
            kRight  = (strafeDir < 0);
            kSprint = true;
        }
    }

    // ── W-tap ─────────────────────────────────────────────────────────────────

    private void tickWTap() {
        if (wTapOffTicks > 0) {
            wTapOffTicks--;
            kSprint = false;
            return;
        }
        if (++wTapClock >= wTapInterval) {
            wTapClock    = 0;
            wTapInterval = 8 + rng.nextInt(13);
            wTapOffTicks = 2 + rng.nextInt(3);
        }
    }

    // ── Heal ──────────────────────────────────────────────────────────────────

    private void tickHeal(MinecraftClient mc) {
        if (healUseCd > 0) { healUseCd--; return; }
        int slot = findHealSlot(mc);
        if (slot < 0) { healing = false; return; }
        mc.player.getInventory().selectedSlot = slot;
        ItemStack item = mc.player.getInventory().getStack(slot);
        if (item.getItem() instanceof SplashPotionItem) {
            serverPitch = 85f; prevSrvPitch = 85f;
            if (splashWarmup < 2) { splashWarmup++; return; }
            splashWarmup = 0;
        }
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        healUseCd = 18 + rng.nextInt(10);
    }

    private void tickRetreat(MinecraftClient mc) {
        if (target == null || retreatTimer <= 0) { retreatTimer = 0; return; }
        retreatTimer--;
        kBack   = true;
        kSprint = true;
    }

    private int findHealSlot(MinecraftClient mc) {
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
             || e.getEffectType() == StatusEffects.REGENERATION) return true;
        return false;
    }

    // ── Attack — same legit approach as AutoClicker ───────────────────────────
    // Incrementing attackKey.timesPressed is consumed by MC's own
    // handleInputEvents() → doAttack() next tick, identical to a real mouse click.

    private void doAttack(MinecraftClient mc) {
        autoSwapWeapon(mc);
        boolean wasSprinting = mc.player.isSprinting();
        mc.player.setSprinting(false); // prevent sweep AoE
        KeyBindingAccessor atk = (KeyBindingAccessor) mc.options.attackKey;
        atk.setTimesPressed(atk.getTimesPressed() + 1);
        mc.player.setSprinting(wasSprinting);
    }

    private void autoSwapWeapon(MinecraftClient mc) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++)
            if (inv.getStack(i).isIn(ItemTags.SWORDS)) { inv.selectedSlot = i; return; }
        for (int i = 0; i < 9; i++)
            if (inv.getStack(i).isIn(ItemTags.AXES))   { inv.selectedSlot = i; return; }
    }

    // ── Math helpers ──────────────────────────────────────────────────────────

    private static float lerpAngle(float current, float target, float factor) {
        float diff = MathHelper.wrapDegrees(target - current);
        return current + diff * MathHelper.clamp(factor, 0f, 1f);
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
        double yr   = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        Vec3d look  = new Vec3d(-Math.sin(yr)*Math.cos(pr), -Math.sin(pr), Math.cos(yr)*Math.cos(pr)).normalize();
        Vec3d eye   = new Vec3d(player.getX(), player.getY()+player.getEyeHeight(player.getPose()), player.getZ());
        Vec3d to    = new Vec3d(target.getX(), target.getY()+target.getHeight()*0.5, target.getZ()).subtract(eye).normalize();
        return (float) Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(to), -1.0, 1.0)));
    }

    private static float getGcd(MinecraftClient mc) {
        double f = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        return (float) (f * f * f * 1.2);
    }

    private void resetAll() {
        srvRotReady = camOverridden = correcting = false;
        jitterTimer = idleTicks = lockOnTicks = critState = 0;
        hitDelay = critCooldown = wTapClock = wTapOffTicks = 0;
        prevSrvYaw = prevSrvPitch = 0f;
        target = null; healing = false; holdActive = false;
        strafeDir = rng.nextBoolean() ? 1 : -1;
        strafeSwitchTimer = 25 + rng.nextInt(40);
        wTapInterval = 8 + rng.nextInt(13);
        kForward = kBack = kLeft = kRight = kSprint = kJump = false;
        retreatTimer = 0; splashWarmup = 0; healUseCd = 0;
    }
}
