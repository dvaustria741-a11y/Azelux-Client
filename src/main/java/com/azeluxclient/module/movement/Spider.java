package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Spider extends Module {

    public Spider() {
        super("Spider", "Climb up walls by holding jump.", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.player.isOnGround() || client.player.isClimbing()) return;

        Vec3d pos = client.player.getPos();
        Vec3d vel = client.player.getVelocity();

        // Check if any horizontal neighbour is a solid block
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos adj = BlockPos.ofFloored(
                pos.x + dir.getOffsetX() * 0.7,
                pos.y + 0.5,
                pos.z + dir.getOffsetZ() * 0.7
            );
            if (!client.world.getBlockState(adj).isAir()) {
                if (client.options.jumpKey.isPressed()) {
                    client.player.setVelocity(vel.x, 0.25, vel.z);
                } else if (vel.y < 0) {
                    client.player.setVelocity(vel.x, 0, vel.z);
                }
                return;
            }
        }
    }
}
