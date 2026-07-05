package com.azeluxclient.module;

import com.azeluxclient.module.combat.KillAura;
import com.azeluxclient.module.combat.Reach;
import com.azeluxclient.module.combat.Velocity;
import com.azeluxclient.module.misc.AntiAFK;
import com.azeluxclient.module.misc.FastPlace;
import com.azeluxclient.module.movement.NoFall;
import com.azeluxclient.module.movement.Speed;
import com.azeluxclient.module.movement.Sprint;
import com.azeluxclient.module.render.ESP;
import com.azeluxclient.module.render.Fullbright;
import com.azeluxclient.module.render.Zoom;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {
    private static final List<Module> modules = new ArrayList<>();

    public static void init() {
        reg(new KillAura());
        reg(new Velocity());
        reg(new Reach());
        reg(new Sprint());
        reg(new Speed());
        reg(new NoFall());
        reg(new ESP());
        reg(new Fullbright());
        reg(new Zoom());
        reg(new AntiAFK());
        reg(new FastPlace());
    }

    private static void reg(Module m) { modules.add(m); }

    public static void onTick(MinecraftClient client) {
        for (Module m : modules) {
            if (m.isEnabled()) m.onTick(client);
        }
    }

    public static List<Module> getModules() { return modules; }

    public static List<Module> getByCategory(Module.Category cat) {
        return modules.stream().filter(m -> m.getCategory() == cat).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public static <T extends Module> T get(Class<T> clazz) {
        return (T) modules.stream().filter(m -> m.getClass() == clazz).findFirst().orElse(null);
    }
}
