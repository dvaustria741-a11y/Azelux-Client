package com.azeluxclient.module.player;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.Random;

public class AutoClicker extends Module {
    private final SliderSetting cps = register(new SliderSetting("CPS", 10.0, 1.0, 20.0));

    private final Random rng = new Random();
    private int   ticksLeft = 0;
    private int   prevDelay = -1;

    /**
     * java.awt.Robot instance — sends a real OS-level left-mouse-button event
     * that travels through LWJGL → GLFW → MC's handleMouse() → doAttack(),
     * exactly like a physical click.  This is fundamentally different from
     * calling interactionManager.attackEntity() or sending a raw packet:
     *
     *   • The full input pipeline runs (attack-cooldown, raycast targeting,
     *     swingHand, etc.) — no shortcuts that anticheats can fingerprint.
     *   • The click is indistinguishable from hardware at every layer the
     *     server can observe.
     *
     * If Robot can't be initialised (e.g. headless environment) we fall back
     * to the direct interactionManager call so the module still works.
     */
    private static final Robot robot;
    static {
        Robot r = null;
        try { r = new Robot(); } catch (Exception ignored) {}
        robot = r;
    }

    public AutoClicker() {
        super("AutoClicker", "Automatically left-clicks at a set clicks-per-second.", Category.PLAYER);
    }

    /**
     * Gaussian-jittered delay in ticks, centred on 20/CPS.
     * CV ≈ 22 % matches measured human clicking distributions (0.18–0.30).
     * Anti-repeat nudge breaks Vulcan's sequence-pattern checks (G, I, J).
     */
    private int randomDelay() {
        double base  = 20.0 / cps.getValue();
        double noise = rng.nextGaussian() * base * 0.22;
        noise = Math.max(-base * 0.45, Math.min(base * 0.45, noise));
        int delay = Math.max(1, (int) Math.round(base + noise));
        if (delay == prevDelay)
            delay = Math.max(1, delay + (rng.nextBoolean() ? 1 : -1));
        prevDelay = delay;
        return delay;
    }

    @Override public void onEnable()  { prevDelay = -1; ticksLeft = randomDelay(); }
    @Override public void onDisable() { ticksLeft = 0;  prevDelay = -1; }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        // Don't click while eating / blocking / drawing bow / etc.
        if (client.player.isUsingItem()) return;
        if (--ticksLeft > 0) return;
        ticksLeft = randomDelay();

        // ~4 % deliberate miss rate — mimics human hesitation
        if (rng.nextInt(25) == 0) return;

        // Respect attack cooldown
        if (client.player.getAttackCooldownProgress(0f) < 1.0f) return;

        if (robot != null) {
            // ── Preferred path: real OS-level mouse click ─────────────────
            // Goes through the full LWJGL → GLFW → MC input stack.
            // MC's own doAttack() handles targeting, swing, packets — nothing
            // looks different from a player actually pressing the mouse button.
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        } else {
            // ── Fallback (e.g. headless / Android AWT stub) ───────────────
            if (client.targetedEntity != null) {
                client.interactionManager.attackEntity(client.player, client.targetedEntity);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }
}
