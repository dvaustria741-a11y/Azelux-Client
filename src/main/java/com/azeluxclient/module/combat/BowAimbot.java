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
        if (client.player.getMainHandStack().getItem() != Items.BOW || !client.player.isUsingItem()) return;

        double r = range.getValue();
        Box box = client.player.getBoundingBox().expand(r);
        List<LivingEntity> targets = client.world.getEntitiesByClass(LivingEntity.class, box,
            e -> e != client.player && !e.isDead()
                && !(e instanceof PlayerEntity p && p.isCreative()));

        targets.stream()
            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(client.player)))
            .ifPresent(target -> {
                float charge = BowItem.getPullProgress(client.player.getItemUseTime());

                // Use getX/Y/Z instead of getPos()
                double tx = target.getX();
                double ty = target.getY() + target.getHeight() / 2.0;
                double tz = target.getZ();

                double dx = tx - client.player.getX();
                double dy = ty - client.player.getEyeY();
                double dz = tz - client.player.getZ();
                double hDist = Math.sqrt(dx * dx + dz * dz);

                double g = 0.006;
                double v = Math.max(charge, 0.01);
                double pitchRad = -Math.atan2(dy, hDist) + Math.asin(g * hDist / (v * v)) * 0.5;

                float yaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) -Math.toDegrees(pitchRad);

                client.player.setYaw(yaw);
                client.player.setPitch(MathHelper.clamp(pitch, -90f, 90f));
            });
    }
}
