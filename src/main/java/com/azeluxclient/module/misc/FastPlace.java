package com.azeluxclient.module.misc;

import com.azeluxclient.mixin.MinecraftClientAccessor;
import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;

public class FastPlace extends Module {
    public FastPlace() {
        super("FastPlace", "Removes block/item placement delay.", Category.MISC);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        MinecraftClientAccessor accessor = (MinecraftClientAccessor) client;
        if (accessor.azeluxclient$getItemUseCooldown() > 0) {
            accessor.azeluxclient$setItemUseCooldown(0);
        }
    }
}
