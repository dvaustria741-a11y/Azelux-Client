package com.auraclient.module.misc;

import com.auraclient.mixin.MinecraftClientAccessor;
import com.auraclient.module.Module;
import net.minecraft.client.MinecraftClient;

public class FastPlace extends Module {
    public FastPlace() {
        super("FastPlace", "Removes block/item placement delay.", Category.MISC);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        MinecraftClientAccessor accessor = (MinecraftClientAccessor) client;
        if (accessor.auraclient$getItemUseCooldown() > 0) {
            accessor.auraclient$setItemUseCooldown(0);
        }
    }
}
