package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import com.azeluxclient.module.ModuleManager;
import com.azeluxclient.setting.BooleanSetting;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.List;

public class KillAura extends Module {
    private final SliderSetting range     = register(new SliderSetting("Range", 4.0, 2.0, 6.0));
    private final BooleanSetting players  = register(new BooleanSetting("Players", true));
    private final BooleanSetting mobs     = register(new BooleanSetting("Mobs", true));
    private final BooleanSetting crits    = register(new BooleanSetting("Criticals", true));

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities.", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.player.getAttackCooldownProgress(0f) < 0.9f) return;

        Box box = client.player.getBoundingBox().expand(range.getValue());
        List<LivingEntity> targets = client.world.getEntitiesByClass(
            LivingEntity.class, box,
            e -> e != client.player && !e.isDead()
                && (e instanceof PlayerEntity ? players.getValue() : mobs.getValue())
                && !(e instanceof PlayerEntity p && p.isCreative())
        );

        targets.stream()
            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(client.player)))
            .ifPresent(t -> {
                Criticals crit = ModuleManager.get(Criticals.class);
                if (crit != null && crit.isEnabled() && crits.getValue()) {
                    Criticals.sendCritPackets(client);
                }
                client.interactionManager.attackEntity(client.player, t);
                client.player.swingHand(Hand.MAIN_HAND);
            });
    }
}
