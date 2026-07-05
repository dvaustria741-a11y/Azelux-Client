package com.azeluxclient.module;

import com.azeluxclient.module.combat.*;
import com.azeluxclient.module.hud.*;
import com.azeluxclient.module.misc.*;
import com.azeluxclient.module.movement.*;
import com.azeluxclient.module.render.*;
import net.minecraft.client.MinecraftClient;
import java.util.*;
import java.util.stream.Collectors;

public class ModuleManager {
    private static final List<Module> modules = new ArrayList<>();

    public static void init() {
        // Combat
        modules.add(new KillAura());
        modules.add(new AimAssist());
        modules.add(new Reach());
        modules.add(new Velocity());

        // Movement
        modules.add(new Speed());
        modules.add(new Sprint());
        modules.add(new NoFall());

        // Render
        modules.add(new ESP());
        modules.add(new Fullbright());
        modules.add(new Zoom());

        // HUD — based on Azelux Bedrock modules
        modules.add(new ArmorHUD());
        modules.add(new DirectionHUD());
        modules.add(new DurabilityViewer());
        modules.add(new TotemCounter());
        modules.add(new PotionCounter());
        modules.add(new ServerAddressHUD());
        modules.add(new MovingStatus());
        modules.add(new CustomCrosshair());

        // Misc
        modules.add(new AntiAFK());
        modules.add(new FastPlace());
        modules.add(new ChunkBorders());
    }

    public static void onTick(MinecraftClient client) {
        modules.stream().filter(Module::isEnabled).forEach(m -> m.onTick(client));
    }

    public static List<Module> getModules() { return modules; }

    public static List<Module> getByCategory(Module.Category cat) {
        return modules.stream().filter(m -> m.getCategory() == cat).collect(Collectors.toList());
    }
}
