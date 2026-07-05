package com.azeluxclient.module.combat;

import com.azeluxclient.module.Module;
import com.azeluxclient.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.Identifier;

public class Reach extends Module {
    private static final Identifier REACH_ID = Identifier.of("azeluxclient", "reach_bonus");
    private final SliderSetting extra = register(new SliderSetting("Extra", 1.0, 0.0, 3.0));

    public Reach() {
        super("Reach", "Increases attack and interaction range.", Category.COMBAT);
    }

    @Override
    public void onEnable() { apply(extra.getValue()); }

    @Override
    public void onDisable() { remove(); }

    private void apply(double amount) {
        if (mc.player == null) return;
        remove();
        EntityAttributeModifier mod = new EntityAttributeModifier(REACH_ID, amount, EntityAttributeModifier.Operation.ADD_VALUE);
        var blockAttr  = mc.player.getAttributeInstance(EntityAttributes.BLOCK_INTERACTION_RANGE);
        var entityAttr = mc.player.getAttributeInstance(EntityAttributes.ENTITY_INTERACTION_RANGE);
        if (blockAttr  != null) blockAttr.addTemporaryModifier(mod);
        if (entityAttr != null) entityAttr.addTemporaryModifier(mod);
    }

    private void remove() {
        if (mc.player == null) return;
        var blockAttr  = mc.player.getAttributeInstance(EntityAttributes.BLOCK_INTERACTION_RANGE);
        var entityAttr = mc.player.getAttributeInstance(EntityAttributes.ENTITY_INTERACTION_RANGE);
        if (blockAttr  != null) blockAttr.removeModifier(REACH_ID);
        if (entityAttr != null) entityAttr.removeModifier(REACH_ID);
    }
}
