package com.azeluxclient.module.player;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;

import java.util.Random;

public class AutoClicker extends Module {
    private final SliderSetting cps = register(new SliderSetting("CPS", 10.0, 1.0, 20.0));

    private final Random rng = new Random();
    private int ticksLeft  = 0;
    private int prevDelay  = -1;

    public AutoClicker() {
        super("AutoClicker", "Automatically left-clicks at a set clicks-per-second.", Category.PLAYER);
    }

    /**
     * Generates a Gaussian-jittered tick delay centered on (20 / CPS).
     *
     * Why this bypasses Vulcan's AutoClicker B–M checks:
     *  - Coefficient of variation ≈ 22 %, which matches measured human
     *    clicking distributions (CV ≈ 0.18–0.30).
     *  - The anti-repeat nudge ensures no two consecutive delays are
     *    identical, breaking Vulcan's sequence-pattern detectors (G, I, J).
     *  - The result is a non-trivial interval distribution with realistic
     *    entropy, defeating Vulcan's statistical consistency checks (B–F).
     *
     * The old code: (int)(20.0 / cps) → exactly 5 ticks every time at 3.5 CPS
     * → CV = 0, entropy ≈ 0, instant detection by every buffer-based check.
     */
    private int randomDelay() {
        double base = 20.0 / cps.getValue();
        // Gaussian noise centered on `base` with 22 % standard deviation.
        // Clamped to ±45 % so extreme outliers can't make CPS go negative
        // or spike absurdly high.
        double noise  = rng.nextGaussian() * base * 0.22;
        double clamped = Math.max(-base * 0.45, Math.min(base * 0.45, noise));
        int delay = Math.max(1, (int) Math.round(base + clamped));

        // Avoid back-to-back equal delays: nudge ±1 tick at random.
        // This breaks Vulcan checks that look for repeating interval runs.
        if (delay == prevDelay) {
            delay = Math.max(1, delay + (rng.nextBoolean() ? 1 : -1));
        }
        prevDelay = delay;
        return delay;
    }

    @Override
    public void onEnable() {
        prevDelay  = -1;
        ticksLeft  = randomDelay();
    }

    @Override
    public void onDisable() {
        ticksLeft = 0;
        prevDelay = -1;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        if (--ticksLeft > 0) return;
        ticksLeft = randomDelay();

        // ~4 % deliberate miss rate — real humans occasionally fumble a click.
        // This adds entropy to the interval sequence and prevents Vulcan's
        // long-window checks (G, I, K) from building a consistent click model.
        if (rng.nextInt(25) == 0) return;

        // Respect attack cooldown — don't click while the weapon is still
        // cooling down (also avoids the "impossibly fast swing" flag).
        if (client.player.getAttackCooldownProgress(0f) < 1.0f) return;

        if (client.targetedEntity != null) {
            client.interactionManager.attackEntity(client.player, client.targetedEntity);
            client.player.swingHand(Hand.MAIN_HAND);
        }
    }
}
