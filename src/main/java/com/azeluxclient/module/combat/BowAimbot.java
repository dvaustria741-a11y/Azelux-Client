package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

public class BowAimbot extends Module {
    private final SliderSetting range = register(new SliderSetting("Range", 20.0, 5.0, 60.0));

    public BowAimbot() {
        super("BowAimbot", "Automatically aims your bow at the nearest enemy.", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        // Only when holding bow and charging
        boolean holdingBow = client.player.getMainHandStack().getItem() == Items.BOW;
        if (!holdingBow || !client.player.isUsingItem()) return;

        Box box = client.player.getBoundingBox().expand(range.getValue());
        List<LivingEntity> targets = client.world.getEntitiesByClass(LivingEntity.class, box,
            e -> e != client.player && !e.isDead()
                && !(e instanceof PlayerEntity p && p.isCreative()));

        targets.stream()
            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(client.player)))
            .ifPresent(target -> {
                float charge = BowItem.getPullProgress(client.player.getItemUseTime());
                Vec3d pos = target.getPos().add(0, target.getHeight() / 2.0, 0);
                double dx = pos.x - client.player.getX();
                double dy = pos.y - client.player.getEyeY();
                double dz = pos.z - client.player.getZ();
                double hDist = Math.sqrt(dx * dx + dz * dz);

                // Trajectory compensation
                float g = 0.006f;
                float v = charge;
                double pitchRad = -Math.atan2(dy, hDist) + Math.asin(g * hDist / (v * v + 0.0001)) * 0.5;
                float yaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) -Math.toDegrees(pitchRad);

                client.player.setYaw(yaw);
                client.player.setPitch(MathHelper.clamp(pitch, -90f, 90f));
            });
    }
}
