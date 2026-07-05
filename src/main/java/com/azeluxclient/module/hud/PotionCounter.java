package com.azeluxclient.module.hud;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;

public class PotionCounter extends Module {
    public PotionCounter() {
        super("PotionCounter", "Counts potions in your inventory", Category.HUD);
    }

    public int countPotions(MinecraftClient client) {
        if (client.player == null) return 0;
        int count = 0;
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            var item = client.player.getInventory().getStack(i).getItem();
            if (item == Items.SPLASH_POTION || item == Items.POTION || item == Items.LINGERING_POTION)
                count++;
        }
        return count;
    }
}
