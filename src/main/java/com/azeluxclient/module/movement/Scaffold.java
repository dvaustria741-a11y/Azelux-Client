package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Scaffold extends Module {

    public Scaffold() {
        super("Scaffold", "Automatically places blocks under your feet as you walk.", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) return;

        PlayerInventory inv = client.player.getInventory();
        int blockSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() instanceof BlockItem) { blockSlot = i; break; }
        }
        if (blockSlot == -1) return;

        // Use getBlockPos() instead of getPos()
        BlockPos feet    = client.player.getBlockPos();
        BlockPos placeAt = feet.down();

        if (!client.world.getBlockState(placeAt).isAir()) return;

        int prevSlot = inv.selectedSlot;
        inv.selectedSlot = blockSlot;

        BlockPos support = placeAt.down();
        if (!client.world.getBlockState(support).isAir()) {
            Vec3d hit = Vec3d.ofCenter(support).add(0, 0.5, 0);
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, support, false));
            inv.selectedSlot = prevSlot;
            return;
        }

        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos adj = placeAt.offset(dir);
            if (!client.world.getBlockState(adj).isAir()) {
                Direction opp = dir.getOpposite();
                Vec3d hit = Vec3d.ofCenter(adj).add(opp.getOffsetX() * 0.5, 0, opp.getOffsetZ() * 0.5);
                client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
                    new BlockHitResult(hit, opp, adj, false));
                break;
            }
        }
        inv.selectedSlot = prevSlot;
    }
}
