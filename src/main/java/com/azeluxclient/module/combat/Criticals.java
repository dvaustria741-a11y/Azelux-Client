package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public class Criticals extends Module {

    public Criticals() {
        super("Criticals", "Makes every melee attack a critical hit.", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || !client.player.isOnGround()) return;
        Vec3d vel = client.player.getVelocity();
        if (Math.abs(vel.x) > 0.01 || Math.abs(vel.z) > 0.01) {
            client.player.setVelocity(vel.x, 0.11, vel.z);
        }
    }

    /** Called by KillAura to guarantee a crit via micro-jump packets. */
    public static void sendCritPackets(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        if (!client.player.isOnGround()) return;
        double x = client.player.getX(), y = client.player.getY(), z = client.player.getZ();
        boolean hc = client.player.horizontalCollision;
        client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false, hc));
        client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y,          z, true,  hc));
    }
}
