package com.azeluxclient.module.player;

import com.azeluxclient.module.Module;
import com.azeluxclient.mixin.KeyBindingAccessor;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;

import java.util.Random;

public class AutoClicker extends Module {
    private final SliderSetting cps = register(new SliderSetting("CPS", 10.0, 1.0, 20.0));

    private final Random rng = new Random();
    private int   ticksLeft = 0;
    private int   prevDelay = -1;

    public AutoClicker() {
        super("AutoClicker", "Automatically left-clicks at a set clicks-per-second.", Category.PLAYER);
    }

    /**
     * Gaussian-jittered delay in ticks — see AutoClicker comments for full rationale.
     * CV ≈ 22 %, anti-repeat nudge, 4 % deliberate miss rate.
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
        if (client.player.isUsingItem()) return;
        if (--ticksLeft > 0) return;
        ticksLeft = randomDelay();

        // ~4 % deliberate miss rate
        if (rng.nextInt(25) == 0) return;

        if (client.player.getAttackCooldownProgress(0f) < 1.0f) return;

        // ── Legit click via MC's own input handler ──────────────────────────
        // Incrementing attackKey.timesPressed by 1 is processed by MC's
        // handleInputEvents() → doAttack() on the NEXT tick, exactly as if
        // the player physically pressed the left mouse button.
        //
        // Why this is better than Robot.mousePress or interactionManager.attackEntity:
        //   • Robot fails silently on Android and falls back to a direct packet.
        //   • Direct interactionManager calls bypass MC's targeting/raycast pipeline
        //     — the attack packet can arrive without a matching click event, which
        //     Vulcan's BadPackets check can detect.
        //   • timesPressed goes through handleInputEvents() → the full attack
        //     pipeline (targeting, swing, cooldown, sprint-reset, all packets)
        //     fires in the same frame as a real key press — nothing to fingerprint.
        KeyBindingAccessor atk = (KeyBindingAccessor) client.options.attackKey;
        atk.setTimesPressed(atk.getTimesPressed() + 1);
    }
}
