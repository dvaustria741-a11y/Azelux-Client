package com.azeluxclient.mixin;

import com.azeluxclient.AzeluxClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    private static final Identifier AZ_LOGO = Identifier.of("azeluxclient", "textures/gui/logo.png");

    protected TitleScreenMixin() {
        super(Text.empty());
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void azeluxInit(CallbackInfo ci) {
        ci.cancel();
        clearChildren();

        int cx   = width / 2;
        int btnW = 220, btnH = 24;
        int startY = height / 2 + 20;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("SINGLEPLAYER"),
                btn -> client.setScreen(new SelectWorldScreen(this)))
                .dimensions(cx - btnW / 2, startY,      btnW, btnH).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("MULTIPLAYER"),
                btn -> client.setScreen(new MultiplayerScreen(this)))
                .dimensions(cx - btnW / 2, startY + 28, btnW, btnH).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("QUIT"),
                btn -> client.scheduleStop())
                .dimensions(cx - btnW / 2, startY + 56, btnW, btnH).build());
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void azeluxRender(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
        ci.cancel();
        int w = width, h = height;

        // Deep blue-black gradient background
        ctx.fillGradient(0, 0, w, h, 0xFF0D0D22, 0xFF060612);

        // Subtle centre glow
        ctx.fillGradient(w / 2 - 220, h / 5, w / 2 + 220, 4 * h / 5, 0x1A304090, 0x00000000);

        // Logo
        int logoSz = Math.min(90, h / 5);
        int logoX  = w / 2 - logoSz / 2;
        int logoY  = h / 4 - logoSz / 2 - 8;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED,
                AZ_LOGO, logoX, logoY, 0f, 0f, logoSz, logoSz, 281, 282, 281, 282);

        // "Azelux" white + "Client" purple
        int titleY = logoY + logoSz + 10;
        String t1 = "Azelux", t2 = "Client";
        int t1w    = textRenderer.getWidth(t1);
        int totalW = t1w + textRenderer.getWidth(t2) + 3;
        ctx.drawText(textRenderer, t1, w / 2 - totalW / 2,           titleY, 0xFFFFFFFF, false);
        ctx.drawText(textRenderer, t2, w / 2 - totalW / 2 + t1w + 3, titleY, 0xFFC084FC, false);

        // Render our buttons
        for (Element e : children()) {
            if (e instanceof net.minecraft.client.gui.Drawable d) d.render(ctx, mx, my, delta);
        }

        // Bottom-left version
        String ver = "Azelux Client (" + AzeluxClient.VERSION + "/release)";
        ctx.drawText(textRenderer, ver, 4, h - 12, 0xFF44446A, false);

        // Bottom-right disclaimer
        String disc = "Not affiliated with Mojang or Microsoft. Do not distribute!";
        ctx.drawText(textRenderer, disc,
                w - textRenderer.getWidth(disc) - 4, h - 12, 0xFF44446A, false);

        // Bottom-right badge
        int bw = 132, bh = 38;
        int bx = w - bw - 12, by = h - bh - 22;
        ctx.fill(bx, by, bx + bw, by + bh, 0xBB12122A);
        ctx.fill(bx, by, bx + bw, by + 1,  0x55FFFFFF);
        ctx.fill(bx, by, bx + 1,  by + bh, 0x33FFFFFF);
        int bls = 22;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED,
                AZ_LOGO, bx + 7, by + bh / 2 - bls / 2, 0f, 0f, bls, bls, 281, 282, 281, 282);
        ctx.drawText(textRenderer, "Azelux", bx + 7 + bls + 6, by + bh / 2 - 8, 0xFFFFFFFF, false);
        ctx.drawText(textRenderer, "Client", bx + 7 + bls + 6, by + bh / 2 + 2, 0xFFC084FC, false);
    }
}
