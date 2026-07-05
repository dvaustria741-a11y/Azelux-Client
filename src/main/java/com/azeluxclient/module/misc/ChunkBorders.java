package com.azeluxclient.module.misc;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;

public class ChunkBorders extends Module {
    public ChunkBorders() {
        super("ChunkBorders", "Displays chunk boundary lines in the world", Category.MISC);
    }

    public int[] getCurrentChunk(MinecraftClient client) {
        if (client.player == null) return new int[]{0, 0};
        int cx = (int) Math.floor(client.player.getX()) >> 4;
        int cz = (int) Math.floor(client.player.getZ()) >> 4;
        return new int[]{cx, cz};
    }
}
