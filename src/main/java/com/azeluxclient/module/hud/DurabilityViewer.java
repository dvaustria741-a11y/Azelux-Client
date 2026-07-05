package com.azeluxclient.module.hud;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.BooleanSetting;

public class DurabilityViewer extends Module {
    public final BooleanSetting showAll = new BooleanSetting("Show All Items", true);

    public DurabilityViewer() {
        super("DurabilityViewer", "Shows durability of held items and armor", Category.HUD);
        settings.add(showAll);
    }
}
