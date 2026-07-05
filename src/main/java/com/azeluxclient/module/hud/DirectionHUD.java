package com.azeluxclient.module.hud;

import com.azeluxclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Direction;

public class DirectionHUD extends Module {
    public DirectionHUD() {
        super("DirectionHUD", "Shows compass and facing direction on the HUD", Category.HUD);
    }

    public String getFacingLabel(MinecraftClient client) {
        if (client.player == null) return "?";
        Direction dir = client.player.getHorizontalFacing();
        return switch (dir) {
            case NORTH -> "N";
            case SOUTH -> "S";
            case EAST  -> "E";
            case WEST  -> "W";
            default    -> "?";
        };
    }

    public String getFacingFull(MinecraftClient client) {
        if (client.player == null) return "Unknown";
        Direction dir = client.player.getHorizontalFacing();
        return switch (dir) {
            case NORTH -> "North (-Z)";
            case SOUTH -> "South (+Z)";
            case EAST  -> "East (+X)";
            case WEST  -> "West (-X)";
            default    -> "Unknown";
        };
    }
}
