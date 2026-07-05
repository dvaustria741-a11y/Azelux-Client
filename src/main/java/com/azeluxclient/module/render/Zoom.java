package com.azeluxclient.module.render;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;

public class Zoom extends Module {
    private static Zoom instance;
    public final SliderSetting fov = register(new SliderSetting("FOV", 30.0, 5.0, 50.0));

    public Zoom() {
        super("Zoom", "Zooms the camera in for a closer view.", Category.RENDER);
        instance = this;
    }

    public static Zoom getInstance() { return instance; }
}
