package com.azeluxclient.module.hud;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;

public class TotemCounter extends Module {
    public TotemCounter() {
        super("TotemCounter", "Counts totems of undying in your inventory", Category.HUD);
    }

    public int countTotems(MinecraftClient client) {
        if (client.player == null) return 0;
        int count = 0;
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            if (client.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING)
                count++;
        }
        return count;
    }
}
