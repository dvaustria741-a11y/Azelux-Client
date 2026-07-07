package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class Flight extends Module {
    private final SliderSetting speed = register(new SliderSetting("Speed", 0.5, 0.1, 5.0));

    public Flight() {
        super("Flight", "Allows flying in Survival/Adventure mode.", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        client.player.getAbilities().flying = false;
        client.player.fallDistance = 0f;

        var input = client.player.input;
        double spd = speed.getValue();
        double vx = 0, vy = 0, vz = 0;

        if (input != null) {
            var move = input.getMovementInput();
            float yaw = client.player.getYaw();
            double rad = Math.toRadians(yaw);
            vx = (-Math.sin(rad) * move.y + Math.cos(rad) * move.x) * spd;
            vz = ( Math.cos(rad) * move.y + Math.sin(rad) * move.x) * spd;
        }
        if (client.options.jumpKey.isPressed())  vy =  spd;
        if (client.options.sneakKey.isPressed()) vy = -spd;

        client.player.setVelocity(vx, vy, vz);
    }

    @Override
    public void onDisable() {
        MinecraftClient client = mc();
        if (client.player != null) client.player.setVelocity(Vec3d.ZERO);
    }
}
