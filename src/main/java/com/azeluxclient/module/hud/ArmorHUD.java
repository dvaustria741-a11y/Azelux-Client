package com.azeluxclient.module.hud;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.BooleanSetting;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

public class ArmorHUD extends Module {
    public final BooleanSetting showDurability = register(new BooleanSetting("Show Durability", true));
    public final SliderSetting  xPos           = register(new SliderSetting("X Position", 4.0, 0.0, 400.0));
    public final SliderSetting  yPos           = register(new SliderSetting("Y Position", 28.0, 0.0, 300.0));

    public ArmorHUD() {
        super("ArmorHUD", "Shows your armor slots and durability on the HUD.", Category.HUD);
    }

    /** Returns [helmet, chestplate, leggings, boots] */
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
