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
 * Always-on network firewall.
 *
 * Intercepts every incoming custom payload packet and silently drops
 * any channel not on the whitelist in NetworkFirewall.ALLOWED_CHANNELS.
 *
 * Uses CustomPayload.Id to extract the channel identifier — the Id record
 * exposes the Identifier via its id() accessor in 1.21.x Yarn.
 */
@Mixin(ClientCommonNetworkHandler.class)
public class NetworkFirewallMixin {

    @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
    private void firewall_onCustomPayload(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        CustomPayload payload = packet.payload();
        // CustomPayload.Id<T> is a record whose accessor is id() -> Identifier
        // We call getId() which is the Yarn name in 1.21.11 for the id method
        CustomPayload.Id<?> payloadId = payload.getId();
        String channel = payloadId.id().toString();

        if (!NetworkFirewall.isChannelAllowed(channel)) {
            ci.cancel();
        }
    }
}
