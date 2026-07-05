package com.azeluxclient;

import com.azeluxclient.gui.AzeluxClickGui;
import com.azeluxclient.gui.HudRenderer;
import com.azeluxclient.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzeluxClient implements ClientModInitializer {
    public static final String MOD_ID  = "azeluxclient";
    public static final String NAME    = "Azelux Client";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER  = LoggerFactory.getLogger(MOD_ID);

    private static AzeluxClient instance;
    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        instance = this;

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.azeluxclient.opengui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                new KeyBinding.Category(Identifier.of("azeluxclient", "category"))
        ));

        ModuleManager.init();
        HudRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openGuiKey.wasPressed() && client.currentScreen == null) {
                client.setScreen(new AzeluxClickGui());
            }
            if (client.player != null) {
                ModuleManager.onTick(client);
            }
        });

        LOGGER.info("[Azelux Client] v{} loaded — {} modules ready.", VERSION, ModuleManager.getModules().size());
    }

    public static AzeluxClient getInstance() { return instance; }
    public static KeyBinding getOpenGuiKey() { return openGuiKey; }
}
