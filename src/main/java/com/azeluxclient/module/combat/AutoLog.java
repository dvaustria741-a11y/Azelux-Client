package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.BooleanSetting;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;

public class AutoLog extends Module {
    private final SliderSetting health      = register(new SliderSetting("Health", 6.0, 1.0, 19.0));
    private final BooleanSetting toggleOff  = register(new BooleanSetting("Toggle Off", true));

    public AutoLog() {
        super("AutoLog", "Automatically disconnects when your health drops below a threshold.", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        float hp = client.player.getHealth();
        if (hp <= 0 || hp > (float) health.getValue()) return;

        client.player.networkHandler.onDisconnect(
            new DisconnectS2CPacket(Text.literal("[AutoLog] Health below " + (int) health.getValue()))
        );
        if (toggleOff.getValue()) toggle();
    }
}
