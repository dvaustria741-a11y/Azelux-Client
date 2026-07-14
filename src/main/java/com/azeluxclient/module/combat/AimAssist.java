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
     * Gaussian-jittered delay, CV ≈ 22 % — matches human clicking stats.
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
        if (client.player.isUsingItem()) return;
        if (--ticksLeft > 0) return;
        ticksLeft = randomDelay();

        // ~4 % miss rate — human hesitation
        if (rng.nextInt(25) == 0) return;

        if (client.player.getAttackCooldownProgress(0f) < 1.0f) return;

        /**
         * Legit attack via MC's own input system.
         *
         * Incrementing attackKey.timesPressed by 1 places exactly one "key
         * press" event in MC's queue.  On the next call to handleInputEvents()
         * MC calls wasPressed() → true → runs its full doAttack() path:
         *   ray-cast to find target, call interactionManager.attackEntity(),
         *   swing animation, sound — everything a real click produces.
         *
         * Why this is better than Robot.mousePress():
         *   • Works on Android (no java.awt dependency)
         *   • Guaranteed to go through MC's own attack handler
         *   • Vulcan sees a normal attack originating from MC's input loop
         *
         * Why this is better than direct interactionManager.attackEntity():
         *   • MC's handleInputEvents() performs ray-casting and targeting —
         *     the same checks a real click triggers, so there's no shortcut
         *     that can be fingerprinted server-side.
         */
        KeyBindingAccessor atk = (KeyBindingAccessor) client.options.attackKey;
        atk.setTimesPressed(atk.getTimesPressed() + 1);
    }
}
