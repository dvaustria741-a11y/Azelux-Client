package com.azeluxclient.module.misc;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class BetterChat extends Module {
    private final BooleanSetting antiSpam  = register(new BooleanSetting("Anti-Spam", true));
    private final BooleanSetting timestamps = register(new BooleanSetting("Timestamps", false));

    private final List<String> recentMessages = new ArrayList<>();
    private static BetterChat instance;

    public BetterChat() {
        super("BetterChat", "Filters duplicate messages and adds optional timestamps.", Category.MISC);
        instance = this;
    }

    /** Returns true if this message should be blocked (duplicate). */
    public static boolean shouldBlock(String msg) {
        if (instance == null || !instance.isEnabled()) return false;
        if (!instance.antiSpam.getValue()) return false;

        if (instance.recentMessages.contains(msg)) return true;
        instance.recentMessages.add(msg);
        if (instance.recentMessages.size() > 20) instance.recentMessages.remove(0);
        return false;
    }

    public static String addTimestamp(String msg) {
        if (instance == null || !instance.isEnabled()) return msg;
        if (!instance.timestamps.getValue()) return msg;
        java.time.LocalTime t = java.time.LocalTime.now();
        return String.format("[%02d:%02d] %s", t.getHour(), t.getMinute(), msg);
    }

    @Override public void onDisable() { recentMessages.clear(); }
    @Override public void onTick(MinecraftClient client) {}
}
