package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class Jesus extends Module {

    public Jesus() {
        super("Jesus", "Walk on water and lava like solid ground.", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.player.isOnGround()) return;

        Vec3d pos = client.player.getPos();
        BlockPos blockPos = BlockPos.ofFloored(pos);
        FluidState fluid = client.world.getFluidState(blockPos);

        if (!fluid.isEmpty() && (fluid.isOf(Fluids.WATER) || fluid.isOf(Fluids.FLOWING_WATER)
                || fluid.isOf(Fluids.LAVA) || fluid.isOf(Fluids.FLOWING_LAVA))) {
            Vec3d vel = client.player.getVelocity();
            if (vel.y < 0) {
                client.player.setVelocity(vel.x, 0.04, vel.z);
            }
        }
    }
}
