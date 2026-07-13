package com.azeluxclient.util;

import com.azeluxclient.AzeluxClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import java.util.Set;

/**
 * NetworkFirewall — always-on anti-detection packet layer.
 *
 * What it does:
 *   1. Spoof client brand as "vanilla" so the server cannot detect Fabric/mods
 *      via the minecraft:brand plugin channel.
 *   2. Whitelist only vanilla channels (minecraft:brand, minecraft:register).
 *      All other incoming custom payload packets (anticheat probes, Fabric
 *      channel advertisements, etc.) are silently dropped before reaching
 *      any registered listener.
 *   3. Silent consume — rejected packets are cancelled cleanly, leaving no
 *      "no response" anomaly logged server-side.
 *
 * Always active — no toggle. Call NetworkFirewall.init() once from
 * AzeluxClient.onInitializeClient() and it stays on for the lifetime
 * of the game session.
 */
public class NetworkFirewall {

    /** Brand sent to the server — appears as a vanilla client. */
    public static final String BRAND = "vanilla";

    /**
     * Channels allowed through in both directions.
     * Everything else is silently dropped.
     */
    public static final Set<String> ALLOWED_CHANNELS = Set.of(
        "minecraft:brand",
        "minecraft:register"
    );

    /**
     * Returns true if an incoming custom payload on this channel
     * should be allowed through. All others are silently dropped.
     */
    public static boolean isChannelAllowed(String channel) {
        return ALLOWED_CHANNELS.contains(channel);
    }

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
            AzeluxClient.LOGGER.info(
                "[NetworkFirewall] Active — brand=\"{}\", allowed channels: {}",
                BRAND, ALLOWED_CHANNELS)
        );
    }
}
