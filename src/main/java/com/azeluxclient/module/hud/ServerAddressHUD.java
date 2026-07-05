package com.azeluxclient.module.hud;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;

public class ServerAddressHUD extends Module {
    public ServerAddressHUD() {
        super("ServerAddress", "Shows current server address on the HUD", Category.HUD);
    }

    public String getAddress(MinecraftClient client) {
        if (client.getCurrentServerEntry() != null)
            return client.getCurrentServerEntry().address;
        return "Singleplayer";
    }
}
