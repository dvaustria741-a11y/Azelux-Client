package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.screen.slot.SlotActionType;

public class Offhand extends Module {
    // Mode: what to prefer in offhand (simple enum via booleans)
    private final BooleanSetting crystal = register(new BooleanSetting("Crystal", true));
    private final BooleanSetting shield  = register(new BooleanSetting("Shield", false));
    private final BooleanSetting gap     = register(new BooleanSetting("Gapple", false));
    private int cooldown = 0;

    public Offhand() {
        super("Offhand", "Keeps your chosen item in your offhand slot.", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (cooldown > 0) { cooldown--; return; }

        Item target = getTargetItem();
        if (target == null) return;
        if (client.player.getOffHandStack().getItem() == target) return;

        // Find in inventory
        for (int i = 0; i < 36; i++) {
            if (client.player.getInventory().getStack(i).getItem() != target) continue;
            int syncId = client.player.playerScreenHandler.syncId;
            int screen = i < 9 ? 36 + i : i;
            client.interactionManager.clickSlot(syncId, screen, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(syncId, 45, 0, SlotActionType.PICKUP, client.player);
            cooldown = 5;
            break;
        }
    }

    @Override public void onDisable() { cooldown = 0; }

    private Item getTargetItem() {
        if (crystal.getValue()) return Items.END_CRYSTAL;
        if (shield.getValue())  return Items.SHIELD;
        if (gap.getValue())     return Items.ENCHANTED_GOLDEN_APPLE;
        return null;
    }
}
