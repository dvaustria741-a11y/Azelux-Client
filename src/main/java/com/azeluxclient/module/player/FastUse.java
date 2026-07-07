package com.azeluxclient.module.player;

import com.azeluxclient.mixin.MinecraftClientAccessor;
import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;

public class FastUse extends Module {

    public FastUse() {
        super("FastUse", "Removes the delay between consecutive item uses.", Category.PLAYER);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        ((MinecraftClientAccessor) client).azeluxclient$setItemUseCooldown(0);
    }
}
