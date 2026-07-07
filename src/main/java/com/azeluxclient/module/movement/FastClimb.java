package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class FastClimb extends Module {
    private final SliderSetting climbSpeed = register(new SliderSetting("Speed", 0.3, 0.1, 1.0));

    public FastClimb() {
        super("FastClimb", "Climb ladders and vines at full movement speed.", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || !client.player.isClimbing()) return;
        Vec3d vel = client.player.getVelocity();
        double spd = climbSpeed.getValue();
        if (client.options.jumpKey.isPressed()) {
            client.player.setVelocity(vel.x, spd, vel.z);
        } else if (client.options.sneakKey.isPressed()) {
            client.player.setVelocity(vel.x, -spd, vel.z);
        } else if (vel.y < 0.1) {
            client.player.setVelocity(vel.x, spd * 0.5, vel.z);
        }
    }
}
