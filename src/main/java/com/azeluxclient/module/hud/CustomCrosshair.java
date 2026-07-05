package com.azeluxclient.module.hud;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;

public class CustomCrosshair extends Module {
    public final SliderSetting style = new SliderSetting("Style", 1, 1, 8);
    public final SliderSetting size  = new SliderSetting("Size", 8, 2, 20);

    public CustomCrosshair() {
        super("CustomCrosshair", "Replace the default crosshair with a custom one", Category.HUD);
        settings.add(style);
        settings.add(size);
    }
}
