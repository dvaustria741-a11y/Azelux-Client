package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class Jesus extends Module {
    public Jesus() {
        super("Jesus", "Walk on water and lava surfaces.", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.player.isOnGround() || client.player.isSneaking()) return;

        // Use getBlockPos() instead of getPos()
        BlockPos pos = client.player.getBlockPos();
        FluidState fluidBelow = client.world.getFluidState(pos);
        FluidState fluidAbove = client.world.getFluidState(pos.up());

        // Only activate at fluid surface (fluid below, air above)
        if (fluidBelow.isEmpty() || !fluidAbove.isEmpty()) return;

        Vec3d vel = client.player.getVelocity();
        if (vel.y < 0) {
            client.player.setVelocity(vel.x, 0.0, vel.z);
            client.player.fallDistance = 0;
        }
    }
}
