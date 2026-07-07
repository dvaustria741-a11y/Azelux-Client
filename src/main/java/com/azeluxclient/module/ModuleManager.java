package com.azeluxclient.module;

import com.azeluxclient.module.combat.*;
import com.azeluxclient.module.hud.*;
import com.azeluxclient.module.misc.*;
import com.azeluxclient.module.movement.*;
import com.azeluxclient.module.player.*;
import com.azeluxclient.module.render.*;
import com.azeluxclient.module.world.*;
import net.minecraft.client.MinecraftClient;
import java.util.*;
import java.util.stream.Collectors;

public class ModuleManager {
    private static final List<Module> modules = new ArrayList<>();

    public static void init() {
        // ── Combat ──────────────────────────────────────────────────────────
        modules.add(new KillAura());
        modules.add(new AimAssist());
        modules.add(new Reach());
        modules.add(new Velocity());
        modules.add(new AutoTotem());
        modules.add(new Criticals());
        modules.add(new AutoArmor());
        modules.add(new AutoWeapon());

        // ── Movement ────────────────────────────────────────────────────────
        modules.add(new Speed());
        modules.add(new Sprint());
        modules.add(new NoFall());
        modules.add(new Flight());
        modules.add(new Step());
        modules.add(new SafeWalk());
        modules.add(new NoSlow());
        modules.add(new Jesus());
        modules.add(new FastClimb());
        modules.add(new Spider());
        modules.add(new HighJump());
        modules.add(new Scaffold());

        // ── Player ──────────────────────────────────────────────────────────
        modules.add(new AutoEat());
        modules.add(new AutoClicker());
        modules.add(new FastUse());
        modules.add(new AutoTool());
        modules.add(new AirPlace());

        // ── Render ──────────────────────────────────────────────────────────
        modules.add(new ESP());
        modules.add(new Fullbright());
        modules.add(new Zoom());
        modules.add(new Tracers());
        modules.add(new StorageESP());
        modules.add(new HoleESP());
        modules.add(new Breadcrumbs());

        // ── HUD ─────────────────────────────────────────────────────────────
        modules.add(new ArmorHUD());
        modules.add(new DirectionHUD());
        modules.add(new DurabilityViewer());
        modules.add(new TotemCounter());
        modules.add(new PotionCounter());
        modules.add(new ServerAddressHUD());
        modules.add(new MovingStatus());
        modules.add(new CustomCrosshair());

        // ── Misc ────────────────────────────────────────────────────────────
        modules.add(new AntiAFK());
        modules.add(new FastPlace());
        modules.add(new ChunkBorders());
        modules.add(new AutoReconnect());

        // ── World ────────────────────────────────────────────────────────────
        modules.add(new Nuker());
    }

    public static void onTick(MinecraftClient client) {
        modules.stream().filter(Module::isEnabled).forEach(m -> m.onTick(client));
    }

    public static List<Module> getModules() { return modules; }

    public static List<Module> getByCategory(Module.Category cat) {
        return modules.stream().filter(m -> m.getCategory() == cat).collect(Collectors.toList());
    }
}
