package com.azeluxclient.module.movement;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class Speed extends Module {
    private final SliderSetting multiplier = register(new SliderSetting("Multiplier", 1.5, 1.1, 3.0));

    public Speed() {
        super("Speed", "Increases horizontal movement speed.", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || !client.player.isOnGround()) return;
        Vec3d vel = client.player.getVelocity();
        double hSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (hSpeed > 0.01) {
            double m = multiplier.getValue();
            client.player.setVelocity(vel.x * m, vel.y, vel.z * m);
        }
    }
}
