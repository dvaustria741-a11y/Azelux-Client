package com.azeluxclient.module.misc;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;

public class Spam extends Module {
    private final SliderSetting delay   = register(new SliderSetting("Delay", 60.0, 10.0, 200.0));
    private String message = "Azelux Client | github.com/dvaustria741-a11y/Azelux-Client";
    private int ticker = 0;

    public Spam() {
        super("Spam", "Sends a chat message at regular intervals.", Category.MISC);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        if (++ticker >= (int)(double) delay.getValue()) {
            ticker = 0;
            client.player.networkHandler.sendChatMessage(message);
        }
    }

    @Override public void onDisable() { ticker = 0; }

    public void setMessage(String msg) { this.message = msg; }
}

