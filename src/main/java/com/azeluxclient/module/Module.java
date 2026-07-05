package com.azeluxclient.module;

import com.azeluxclient.setting.Setting;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private boolean enabled = false;
    protected final List<Setting<?>> settings = new ArrayList<>();

    public Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public void toggle() {
        enabled = !enabled;
        if (enabled) onEnable(); else onDisable();
    }

    public void onEnable()  {}
    public void onDisable() {}
    public void onTick(MinecraftClient client) {}

    protected <T extends Setting<?>> T register(T s) { settings.add(s); return s; }

    public String getName()           { return name; }
    public String getDescription()    { return description; }
    public Category getCategory()     { return category; }
    public boolean isEnabled()        { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public List<Setting<?>> getSettings() { return settings; }

    public enum Category {
        COMBAT("Combat"),
        MOVEMENT("Movement"),
        RENDER("Render"),
        MISC("Misc");

        public final String display;
        Category(String display) { this.display = display; }
    }
}
