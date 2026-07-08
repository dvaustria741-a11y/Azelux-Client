package com.azeluxclient.module.player;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public class AutoReplenish extends Module {
    private final SliderSetting minCount = register(new SliderSetting("Min Count", 8.0, 1.0, 32.0));
    private int cooldown = 0;

    public AutoReplenish() {
        super("AutoReplenish", "Refills hotbar stacks from your inventory when they run low.", Category.PLAYER);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (cooldown > 0) { cooldown--; return; }

        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            ItemStack hotStack = client.player.getInventory().getStack(hotbarSlot);
            if (hotStack.isEmpty() || !hotStack.isStackable()) continue;
            if (hotStack.getCount() > (int) minCount.getValue()) continue;

            Item target = hotStack.getItem();
            // Find same item in main inventory (slots 9-35)
            for (int invSlot = 9; invSlot < 36; invSlot++) {
                ItemStack inv = client.player.getInventory().getStack(invSlot);
                if (inv.getItem() != target) continue;
                // Shift-click to move
                int syncId = client.player.playerScreenHandler.syncId;
                client.interactionManager.clickSlot(syncId, invSlot, 0, SlotActionType.QUICK_MOVE, client.player);
                cooldown = 3;
                return;
            }
        }
    }

    @Override public void onDisable() { cooldown = 0; }
}
