package com.azeluxclient.module;

import com.azeluxclient.setting.Setting;
import net.minecraft.client.MinecraftClient;
import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public enum Category {
        COMBAT("Combat"),
        MOVEMENT("Movement"),
        RENDER("Render"),
        HUD("HUD"),
        MISC("Misc"),
        PLAYER("Player"),
        WORLD("World");

        public final String display;
        Category(String d) { this.display = d; }
    }

    private final String name;
    private final String description;
    private final Category category;
    private boolean enabled = false;
    protected final List<Setting<?>> settings = new ArrayList<>();

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected <T extends Setting<?>> T register(T setting) {
        settings.add(setting);
        return setting;
    }

    protected MinecraftClient mc() { return MinecraftClient.getInstance(); }

    public String getName()        { return name; }
    public String getDescription() { return description; }
    public Category getCategory()  { return category; }
    public boolean isEnabled()     { return enabled; }
    public List<Setting<?>> getSettings() { return settings; }

    public void toggle() {
        enabled = !enabled;
        if (enabled) onEnable(); else onDisable();
    }

    public void onEnable()  {}
    public void onDisable() {}
    public void onTick(MinecraftClient client) {}
}
