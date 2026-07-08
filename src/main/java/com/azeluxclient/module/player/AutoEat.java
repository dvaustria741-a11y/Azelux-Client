package com.azeluxclient.module.player;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public class AutoEat extends Module {
    private final SliderSetting threshold = register(new SliderSetting("Hunger Threshold", 14.0, 1.0, 20.0));

    public AutoEat() {
        super("AutoEat", "Automatically eats food when hunger drops below the threshold.", Category.PLAYER);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        // Fix: intValue() for Double -> int comparison
        if (client.player.getHungerManager().getFoodLevel() > threshold.getValue().intValue()) return;

        PlayerInventory inv = client.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.contains(DataComponentTypes.FOOD)) {
                inv.selectedSlot = i;  // Now accessible via AW
                client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                return;
            }
        }
    }
}
