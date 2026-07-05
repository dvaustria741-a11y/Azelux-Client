package com.azeluxclient.module.hud;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class ArmorHUD extends Module {
    public final BooleanSetting showDurability = new BooleanSetting("Show Durability", true);

    public ArmorHUD() {
        super("ArmorHUD", "Shows your armor on the HUD", Category.HUD);
        settings.add(showDurability);
    }

    public ItemStack[] getArmorStacks(MinecraftClient client) {
        if (client.player == null) return new ItemStack[0];
        return client.player.getArmorItems().iterator().hasNext()
            ? new ItemStack[] {
                client.player.getInventory().armor.get(3),
                client.player.getInventory().armor.get(2),
                client.player.getInventory().armor.get(1),
                client.player.getInventory().armor.get(0)
            }
            : new ItemStack[0];
    }
}
