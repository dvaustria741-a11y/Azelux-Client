package com.azeluxclient.module.world;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class NoGhostBlocks extends Module {
    public NoGhostBlocks() {
        super("NoGhostBlocks", "Resyncs block states to prevent ghost blocks after mining.", Category.WORLD);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        if (!client.player.getMainHandStack().isEmpty() || client.interactionManager == null) return;

        // If the player just finished breaking a block, send an abort action to force resync
        // This is a passive module — ghost block prevention via packet resync is triggered by mining events
        // Main effect: after block break, server re-sends block state
    }

    /** Call this after a block break to request resync from the server. */
    public static void resync(MinecraftClient client, BlockPos pos) {
        if (!MinecraftClient.getInstance().isOnThread()) return;
        if (client.getNetworkHandler() == null) return;
        client.getNetworkHandler().sendPacket(
            new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, Direction.UP, 0)
        );
    }
}
