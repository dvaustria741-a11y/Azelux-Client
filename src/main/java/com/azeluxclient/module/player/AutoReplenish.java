package com.azeluxclient.module.player;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
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

        PlayerInventory inv = client.player.getInventory();
        int min = minCount.getValue().intValue();  // Fix: intValue()

        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            ItemStack hotStack = inv.getStack(hotbarSlot);
            if (hotStack.isEmpty() || !hotStack.isStackable()) continue;
            if (hotStack.getCount() > min) continue;

            Item target = hotStack.getItem();
            for (int invSlot = 9; invSlot < 36; invSlot++) {
                if (inv.getStack(invSlot).getItem() != target) continue;
                int syncId = client.player.currentScreenHandler.syncId;
                client.interactionManager.clickSlot(syncId, invSlot, 0, SlotActionType.QUICK_MOVE, client.player);
                cooldown = 3;
                return;
            }
        }
    }

    @Override public void onDisable() { cooldown = 0; }
}
