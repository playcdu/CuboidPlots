package dev.cuboidplots.integration.ranks;

import dev.cuboidplots.core.*;
import dev.cuboidplots.platform.AddonConfig;
import dev.ftb.mods.ftbranks.api.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.math.BigDecimal;
import java.util.UUID;

public final class FtbRanksAdapter implements LimitProvider {
    private final MinecraftServer server;
    private final AddonConfig config;
    public FtbRanksAdapter(MinecraftServer server, AddonConfig config) { this.server = server; this.config = config; }
    public Limits effectiveLimits(UUID creator) {
        ServerPlayer player = server.getPlayerList().getPlayer(creator);
        if (player == null) throw new IllegalStateException("Creator must be online to evaluate FTB Ranks conditions for an increase");
        // Query every increase. Time, team and rank membership conditions may change at runtime.
        return new Limits(value(player, config.countNode, config.fallback.regions), value(player, config.volumeNode, config.fallback.volume));
    }
    private long value(ServerPlayer player, String node, long fallback) {
        PermissionValue permission = FTBRanksAPI.getPermissionValue(player, node);
        if (permission.isEmpty()) return fallback;
        // asLong() can truncate numeric values. Reject fractional, overflowing or nonnumeric limits.
        Number number = permission.asNumber().orElseThrow(() -> new IllegalArgumentException("Numeric permission required: " + node));
        return new BigDecimal(number.toString()).longValueExact();
    }
}
