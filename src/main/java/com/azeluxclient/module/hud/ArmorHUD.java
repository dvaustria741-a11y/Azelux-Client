package com.azeluxclient.module.hud;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

public class ArmorHUD extends Module {
    public final BooleanSetting showDurability = new BooleanSetting("Show Durability", true);

    public ArmorHUD() {
        super("ArmorHUD", "Shows your armor slots and durability on the HUD.", Category.HUD);
        settings.add(showDurability);
    }

    // Returns [helmet, chestplate, leggings, boots] — top to bottom display order
    public ItemStack[] getArmorStacks(MinecraftClient client) {
        if (client.player == null) return new ItemStack[0];
        return new ItemStack[] {
            client.player.getEquippedStack(EquipmentSlot.HEAD),
            client.player.getEquippedStack(EquipmentSlot.CHEST),
            client.player.getEquippedStack(EquipmentSlot.LEGS),
            client.player.getEquippedStack(EquipmentSlot.FEET)
        };
    }
}
