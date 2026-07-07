package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.*;
import net.minecraft.screen.slot.SlotActionType;

public class AutoArmor extends Module {
    private int delay = 0;

    public AutoArmor() {
        super("AutoArmor", "Automatically equips the best armor from your inventory.", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        if (++delay < 20) return;
        delay = 0;

        PlayerInventory inv = client.player.getInventory();
        int syncId = client.player.currentScreenHandler.syncId;

        for (int armorIdx = 0; armorIdx < 4; armorIdx++) {
            EquipmentSlot slot = switch (armorIdx) {
                case 0 -> EquipmentSlot.FEET;
                case 1 -> EquipmentSlot.LEGS;
                case 2 -> EquipmentSlot.CHEST;
                case 3 -> EquipmentSlot.HEAD;
                default -> null;
            };
            if (slot == null) continue;

            int bestVal = armorValue(inv.armor.get(armorIdx));
            int bestInvIdx = -1;

            for (int i = 0; i < inv.main.size(); i++) {
                ItemStack stack = inv.main.get(i);
                if (stack.getItem() instanceof ArmorItem ai && ai.getSlotType() == slot) {
                    int val = armorValue(stack);
                    if (val > bestVal) { bestVal = val; bestInvIdx = i; }
                }
            }

            if (bestInvIdx >= 0) {
                int invScreen   = bestInvIdx < 9 ? bestInvIdx + 36 : bestInvIdx;
                int armorScreen = 8 - armorIdx; // HEAD=5, CHEST=6, LEGS=7, FEET=8
                client.interactionManager.clickSlot(syncId, invScreen, 0, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(syncId, armorScreen, 0, SlotActionType.PICKUP, client.player);
                if (!client.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    client.interactionManager.clickSlot(syncId, invScreen, 0, SlotActionType.PICKUP, client.player);
                }
                return;
            }
        }
    }

    private int armorValue(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Item item = stack.getItem();
        if (item == Items.NETHERITE_HELMET || item == Items.NETHERITE_CHESTPLATE ||
            item == Items.NETHERITE_LEGGINGS || item == Items.NETHERITE_BOOTS) return 6;
        if (item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE ||
            item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS) return 5;
        if (item == Items.IRON_HELMET || item == Items.IRON_CHESTPLATE ||
            item == Items.IRON_LEGGINGS || item == Items.IRON_BOOTS) return 4;
        if (item == Items.GOLDEN_HELMET || item == Items.GOLDEN_CHESTPLATE ||
            item == Items.GOLDEN_LEGGINGS || item == Items.GOLDEN_BOOTS) return 3;
        if (item == Items.CHAINMAIL_HELMET || item == Items.CHAINMAIL_CHESTPLATE ||
            item == Items.CHAINMAIL_LEGGINGS || item == Items.CHAINMAIL_BOOTS) return 2;
        if (item == Items.LEATHER_HELMET || item == Items.LEATHER_CHESTPLATE ||
            item == Items.LEATHER_LEGGINGS || item == Items.LEATHER_BOOTS) return 1;
        return 0;
    }
}
