package com.azeluxclient.util;

import com.azeluxclient.AzeluxClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import java.util.Set;

/**
 * NetworkFirewall — always-on anti-detection packet layer.
 *
 * ── What it does ─────────────────────────────────────────────────────────────
 *  1. Spoof client brand as "vanilla" (via BrandMixin).
 *  2. Block incoming custom payload packets from known anticheat / mod-probe
 *     channels. All vanilla minecraft: channels are ALLOWED so normal game
 *     init (custom_report_details, server_links, etc.) continues to work.
 *
 * ── Why not whitelist-only ────────────────────────────────────────────────────
 *  The original firewall-config.json5 used a whitelist of only minecraft:brand
 *  and minecraft:register. In 1.21.x Minecraft sends many more minecraft:
 *  channels during session init (minecraft:custom_report_details,
 *  minecraft:server_links, etc.). Blocking them caused the client to freeze —
 *  movement keys stopped working even with no modules active.
 *
 *  Fix: ALLOW everything in the minecraft: namespace. Only block channels from
 *  namespaces known to be used by anticheats / mod detectors.
 */
public class NetworkFirewall {

    public static final String BRAND = "vanilla";

    /**
     * Namespace prefixes that are always blocked.
     * These are never used by vanilla Minecraft.
     */
    private static final Set<String> BLOCKED_NAMESPACES = Set.of(
        "v_c",        // Vulcan anticheat
        "v_f",        // Vulcan anticheat (flag channel)
        "grim",       // GrimAC
        "intave",     // Intave anticheat
        "wyvern",     // Wyvern anticheat
        "karhu",      // Karhu anticheat
        "spartan",    // Spartan anticheat
        "matrix",     // Matrix anticheat
        "negativity", // Negativity anticheat
        "nocheatplus" // NoCheatPlus
    );

    /**
     * Returns true if the incoming custom payload packet on this channel
     * should be DROPPED. All minecraft: channels pass through.
     * All known anticheat namespaces are blocked.
     */
    public static boolean shouldBlock(String channel) {
        // Always allow everything vanilla
        if (channel.startsWith("minecraft:")) return false;
        // Allow fml/forge channels (some servers use these for handshake)
        if (channel.startsWith("fml:") || channel.startsWith("forge:")) return false;
        // Block known anticheat namespaces
        String ns = channel.contains(":") ? channel.substring(0, channel.indexOf(':')) : channel;
        return BLOCKED_NAMESPACES.contains(ns.toLowerCase());
    }

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
            AzeluxClient.LOGGER.info(
                "[NetworkFirewall] Active — brand=\"{}\", blocking anticheat namespaces: {}",
                BRAND, BLOCKED_NAMESPACES)
        );
    }
}
