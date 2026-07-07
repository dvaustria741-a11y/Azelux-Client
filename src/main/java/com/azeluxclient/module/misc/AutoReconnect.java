package com.azeluxclient.module.misc;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

public class AutoReconnect extends Module {
    private final SliderSetting delay = register(new SliderSetting("Delay (s)", 5.0, 1.0, 30.0));
    private int ticksWaited = 0;
    private static AutoReconnect instance;

    public AutoReconnect() {
        super("AutoReconnect", "Automatically reconnects after being disconnected from a server.", Category.MISC);
        instance = this;
    }

    /** Called from AzeluxClient global tick (runs even without a player). */
    public static void globalTick(MinecraftClient client) {
        if (instance == null || !instance.isEnabled()) return;

        if (client.currentScreen instanceof DisconnectedScreen && client.player == null) {
            instance.ticksWaited++;
            int needed = (int)(instance.delay.getValue() * 20);
            if (instance.ticksWaited >= needed) {
                instance.ticksWaited = 0;
                ServerInfo server = client.getCurrentServerEntry();
                if (server != null) {
                    ServerAddress addr = ServerAddress.parse(server.address);
                    ConnectScreen.connect(new TitleScreen(), client, addr, server, false, null);
                }
            }
        } else if (client.player != null) {
            instance.ticksWaited = 0;
        }
    }

    @Override public void onTick(MinecraftClient client) { /* handled via globalTick */ }
    @Override public void onDisable() { ticksWaited = 0; }
}
