package com.azeluxclient.mixin;

import com.azeluxclient.util.NetworkFirewall;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Silently drops incoming custom payload packets from known anticheat channels.
 * All minecraft: namespace packets pass through so vanilla game init works.
 */
@Mixin(ClientCommonNetworkHandler.class)
public class NetworkFirewallMixin {

    @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
    private void firewall_onCustomPayload(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        try {
            CustomPayload payload = packet.payload();
            // getId() is the Yarn 1.21.x accessor for CustomPayload.Id<T>
            String channel = payload.getId().id().toString();
            if (NetworkFirewall.shouldBlock(channel)) {
                ci.cancel();
            }
        } catch (Exception ignored) {
            // If we can't read the channel, allow through — never break vanilla
        }
    }
}
