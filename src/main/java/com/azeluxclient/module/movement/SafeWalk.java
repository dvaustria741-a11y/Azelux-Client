package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class SafeWalk extends Module {

    public SafeWalk() {
        super("SafeWalk", "Prevents you from accidentally walking off edges.", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (!client.player.isOnGround()) return;

        Vec3d vel = client.player.getVelocity();
        if (Math.abs(vel.x) < 0.003 && Math.abs(vel.z) < 0.003) return;

        Vec3d pos = client.player.getPos();
        // Predict where the player is heading and check if ground exists there
        BlockPos ahead = BlockPos.ofFloored(pos.x + vel.x * 5, pos.y - 0.5, pos.z + vel.z * 5);
        BlockState state = client.world.getBlockState(ahead);

        if (state.isAir()) {
            client.player.setVelocity(0, vel.y, 0);
        }
    }
}
