package com.auraclient.module.movement;

import com.auraclient.module.Module;
import net.minecraft.client.MinecraftClient;

public class Sprint extends Module {
    public Sprint() {
        super("Sprint", "Automatically sprints while moving forward.", Category.MOVEMENT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        var input = client.player.input;
        if (input == null) return;
        if (input.getMovementInput().y > 0f && !client.player.isSprinting()
                && !client.player.isSubmergedInWater()
                && !client.player.isTouchingWater()) {
            client.player.setSprinting(true);
        }
    }
}
