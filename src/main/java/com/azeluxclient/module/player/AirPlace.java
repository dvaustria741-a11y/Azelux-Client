package com.azeluxclient.module.player;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class AirPlace extends Module {
    private final SliderSetting range = register(new SliderSetting("Range", 5.5, 4.0, 6.0));

    public AirPlace() {
        super("AirPlace", "Extends block placement range beyond normal.", Category.PLAYER);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) return;
        if (!(client.player.getMainHandStack().getItem() instanceof BlockItem)) return;
        if (!client.options.useKey.isPressed()) return;

        // Cast a ray with extended reach and place on the hit face
        HitResult hit = client.player.raycast(range.getValue(), 0f, false);
        if (hit instanceof BlockHitResult bhr && hit.getType() != HitResult.Type.MISS) {
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, bhr);
        }
    }
}
