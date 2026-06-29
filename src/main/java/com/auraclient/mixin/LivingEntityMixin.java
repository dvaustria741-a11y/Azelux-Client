package com.auraclient.mixin;

import com.auraclient.module.combat.Velocity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
    private void onTakeKnockback(double strength, double x, double z, CallbackInfo ci) {
        if (Velocity.shouldCancel((LivingEntity)(Object)this)) {
            ci.cancel();
        }
    }
}
