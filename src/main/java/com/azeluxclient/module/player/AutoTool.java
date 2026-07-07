package com.azeluxclient.module.player;

import com.azeluxclient.module.Module;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;

public class AutoTool extends Module {

    public AutoTool() {
        super("AutoTool", "Automatically switches to the best tool for the block you are mining.", Category.PLAYER);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (!(client.crosshairTarget instanceof BlockHitResult bhr)) return;

        BlockState state = client.world.getBlockState(bhr.getBlockPos());
        if (state.isAir()) return;

        PlayerInventory inv = client.player.getInventory();
        int bestSlot = -1;
        float bestSpeed = 1.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) { bestSpeed = speed; bestSlot = i; }
        }

        if (bestSlot >= 0 && bestSlot != inv.selectedSlot) {
            inv.selectedSlot = bestSlot;
        }
    }
}
