package com.azeluxclient.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("itemUseCooldown")
    int azeluxclient$getItemUseCooldown();

    @Accessor("itemUseCooldown")
    void azeluxclient$setItemUseCooldown(int value);
}
