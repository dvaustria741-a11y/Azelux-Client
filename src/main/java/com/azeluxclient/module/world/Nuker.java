package com.azeluxclient.module.world;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class Nuker extends Module {
    private final SliderSetting range = register(new SliderSetting("Range", 3.0, 1.0, 6.0));

    public Nuker() {
        super("Nuker", "Rapidly destroys blocks in a radius around you.", Category.WORLD);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) return;

        BlockPos origin = client.player.getBlockPos();
        int r = (int) range.getValue();
        BlockPos target = null;
        double minDist = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++)
          for (int y = -r; y <= r; y++)
            for (int z = -r; z <= r; z++) {
                BlockPos pos = origin.add(x, y, z);
                BlockState state = client.world.getBlockState(pos);
                if (state.isAir()) continue;
                if (state.getHardness(client.world, pos) < 0) continue; // bedrock / unbreakable
                double dist = origin.getSquaredDistance(pos);
                if (dist < minDist) { minDist = dist; target = pos; }
            }

        if (target != null) {
            client.interactionManager.attackBlock(target, Direction.UP);
            client.interactionManager.updateBlockBreakingProgress(target, Direction.UP);
        }
    }
}
