package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class Spider extends Module {
    private final SliderSetting speed = register(new SliderSetting("Speed", 0.2, 0.1, 0.5));

    public Spider() {
        super("Spider", "Allows you to climb any wall like a spider.", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (!client.player.horizontalCollision) return;
        Vec3d vel = client.player.getVelocity();
        if (vel.y < 0) {
            client.player.setVelocity(vel.x, speed.getValue(), vel.z);
        }
    }
}
