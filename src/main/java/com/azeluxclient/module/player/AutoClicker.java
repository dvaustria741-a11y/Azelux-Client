package com.azeluxclient.module.player;

import com.azeluxclient.module.Module;
import com.azeluxclient.mixin.KeyBindingAccessor;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;

import java.util.Random;

public class AutoClicker extends Module {
    private final SliderSetting cps = register(new SliderSetting("CPS", 10.0, 1.0, 20.0));

    private final Random rng = new Random();
    private int   ticksLeft = 0;
    private int   prevDelay = -1;

    public AutoClicker() {
        super("AutoClicker", "Automatically left-clicks at a set CPS — only when a mob or player is in range.", Category.PLAYER);
    }

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

        // ── Range check — only click when a mob or player is in the crosshair ──
        // client.targetedEntity is computed by MC every tick via its own
        // raycast pipeline and is non-null only when an entity is within
        // the player's attack reach. This means we never send clicks into
        // the air, which looks unnatural and wastes the attack cooldown.
        if (!(client.targetedEntity instanceof LivingEntity target)) return;
        if (target == client.player) return;

        // ── Legit click via MC's own input handler ───────────────────────────
        // Incrementing attackKey.timesPressed is processed by MC's own
        // handleInputEvents() → doAttack() on the next tick, identical to
        // a real left mouse button press. Nothing to fingerprint.
        KeyBindingAccessor atk = (KeyBindingAccessor) client.options.attackKey;
        atk.setTimesPressed(atk.getTimesPressed() + 1);
    }
}
