package com.azeluxclient.mixin;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes KeyBinding internals so modules can simulate real key input
 * without Robot (which fails on Android) or packet injection.
 *
 * Usage:
 *   pressed      — held-down state; read by KeyboardInput.tick() each frame
 *   timesPressed — one-shot press counter; decremented by wasPressed();
 *                  used by handleInputEvents() for attack/interact actions
 *
 * Incrementing timesPressed by 1 = exactly one "key press" event processed
 * by MC's own input handler — indistinguishable from a real physical click.
 */
@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {
    @Accessor("pressed")      void setPressed(boolean pressed);
    @Accessor("timesPressed") int  getTimesPressed();
    @Accessor("timesPressed") void setTimesPressed(int n);
}
