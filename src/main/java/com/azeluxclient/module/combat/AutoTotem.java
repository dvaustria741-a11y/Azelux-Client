package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotem extends Module {

    public AutoTotem() {
        super("AutoTotem", "Automatically moves a totem of undying into the offhand.", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        PlayerInventory inv = client.player.getInventory();

        // Already has totem in offhand
        if (inv.offHand.get(0).isOf(Items.TOTEM_OF_UNDYING)) return;

        int syncId = client.player.currentScreenHandler.syncId;

        for (int i = 0; i < inv.main.size(); i++) {
            if (!inv.main.get(i).isOf(Items.TOTEM_OF_UNDYING)) continue;

            // Hotbar (0-8) → screen slots 36-44; main rows (9-35) → screen slots 9-35
            int screenSlot = i < 9 ? i + 36 : i;
            int offhandSlot = 45;

            client.interactionManager.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(syncId, offhandSlot, 0, SlotActionType.PICKUP, client.player);
            // If offhand was occupied the displaced item is on cursor — put it back
            if (!client.player.currentScreenHandler.getCursorStack().isEmpty()) {
                client.interactionManager.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, client.player);
            }
            return;
        }
    }
}
