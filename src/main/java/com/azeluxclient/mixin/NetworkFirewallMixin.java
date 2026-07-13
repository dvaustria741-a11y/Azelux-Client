package com.azeluxclient.mixin;

import com.azeluxclient.util.NetworkFirewall;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Always-on network firewall.
 *
 * Intercepts every incoming custom payload packet (server → client) at
 * ClientCommonNetworkHandler level — the base handler for both play and
 * config phases in 1.20.5+.
 *
 * If the channel is not in NetworkFirewall.ALLOWED_CHANNELS the packet
 * is silently cancelled here before any Fabric listener or vanilla handler
 * can process it. This prevents anticheat and plugin probes from detecting
 * which mod channels are registered on this client.
 */
@Mixin(ClientCommonNetworkHandler.class)
public class NetworkFirewallMixin {

    @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
    private void firewall_onCustomPayload(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        String channel = packet.payload().id().id().toString();
        if (!NetworkFirewall.isChannelAllowed(channel)) {
            ci.cancel(); // silently drop
        }
    }
}
