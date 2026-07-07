package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.*;

public class AutoWeapon extends Module {

    public AutoWeapon() {
        super("AutoWeapon", "Automatically selects the best weapon in your hotbar.", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        PlayerInventory inv = client.player.getInventory();
        int bestSlot = -1, bestVal = 0;

        for (int i = 0; i < 9; i++) {
            int val = weaponValue(inv.getStack(i));
            if (val > bestVal) { bestVal = val; bestSlot = i; }
        }

        if (bestSlot >= 0 && bestSlot != inv.selectedSlot) {
            inv.selectedSlot = bestSlot;
        }
    }

    private int weaponValue(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Item item = stack.getItem();
        if (item == Items.NETHERITE_SWORD) return 60;
        if (item == Items.DIAMOND_SWORD)   return 55;
        if (item == Items.IRON_SWORD)      return 45;
        if (item == Items.STONE_SWORD)     return 35;
        if (item == Items.GOLDEN_SWORD)    return 30;
        if (item == Items.WOODEN_SWORD)    return 25;
        if (item == Items.NETHERITE_AXE)   return 58;
        if (item == Items.DIAMOND_AXE)     return 52;
        if (item == Items.IRON_AXE)        return 42;
        return 0;
    }
}
