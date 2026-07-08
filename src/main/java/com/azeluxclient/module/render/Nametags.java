package com.azeluxclient.module.render;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.BooleanSetting;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class Nametags extends Module {
    private final BooleanSetting health = register(new BooleanSetting("Health", true));
    private final BooleanSetting ping   = register(new BooleanSetting("Ping", false));

    public Nametags() {
        super("Nametags", "Draws enhanced nametags above players showing health and more.", Category.RENDER);
    }

    /** Called from WorldRenderEvents to render 3D nametags. */
    public void render3D(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || ctx.consumers() == null) return;

        Camera cam = mc.gameRenderer.getCamera();
        Vec3d camPos = cam.getPos();

        MatrixStack matrices = ctx.matrices();
        if (matrices == null) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isInvisibleTo(mc.player)) continue;
            if (mc.player.squaredDistanceTo(player) > 2048) continue;

            String name = player.getName().getString();
            String tag  = health.getValue()
                ? name + " §c" + Math.round(player.getHealth()) + "§7/§c" + Math.round(player.getMaxHealth())
                : name;

            double x = player.getX() - camPos.x;
            double y = player.getEyeY() + 0.5  - camPos.y;
            double z = player.getZ() - camPos.z;

            matrices.push();
            matrices.translate(x, y, z);
            matrices.multiply(cam.getRotation());
            matrices.scale(-0.025f, -0.025f, 0.025f);

            Matrix4f mat = matrices.peek().getPositionMatrix();
            TextRenderer tr = mc.textRenderer;
            int w = tr.getWidth(tag);
            VertexConsumerProvider.Immediate vcp = mc.getBufferBuilders().getEntityVertexConsumers();

            // Background
            tr.draw(tag, -w / 2f, 0, 0xFFFFFF, false, mat, vcp, TextRenderer.TextLayerType.SEE_THROUGH, 0x66000000, 0xF000F0);
            vcp.draw();
            matrices.pop();
        }
    }
}
