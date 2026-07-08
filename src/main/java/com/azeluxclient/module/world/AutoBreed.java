package com.azeluxclient.module.world;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;

import java.util.List;

public class AutoBreed extends Module {
    private final SliderSetting range = register(new SliderSetting("Range", 4.0, 1.0, 8.0));
    private int cooldown = 0;

    public AutoBreed() {
        super("AutoBreed", "Automatically uses held food to breed nearby animals.", Category.WORLD);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (cooldown > 0) { cooldown--; return; }

        // Check if held item is food that animals eat
        ItemStack held = client.player.getMainHandStack();
        if (held.isEmpty() || held.get(DataComponentTypes.FOOD) == null) return;

        Box box = client.player.getBoundingBox().expand(range.getValue());
        List<AnimalEntity> animals = client.world.getEntitiesByClass(AnimalEntity.class, box,
            a -> a.isAlive() && !a.isBaby() && !a.isInLove());

        for (AnimalEntity animal : animals) {
            if (client.player.squaredDistanceTo(animal) > range.getValue() * range.getValue()) continue;
            client.interactionManager.interactEntityAtLocation(client.player, animal,
                new net.minecraft.util.hit.EntityHitResult(animal), Hand.MAIN_HAND);
            cooldown = 10;
            return;
        }
    }

    @Override public void onDisable() { cooldown = 0; }
}
