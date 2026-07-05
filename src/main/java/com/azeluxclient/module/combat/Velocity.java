package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;

public class Velocity extends Module {
    private static Velocity instance;

    public Velocity() {
        super("Velocity", "Reduces or cancels knockback.", Category.COMBAT);
        instance = this;
    }

    public static boolean shouldCancel(LivingEntity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return instance != null && instance.isEnabled()
                && mc.player != null && entity == mc.player;
    }
}
