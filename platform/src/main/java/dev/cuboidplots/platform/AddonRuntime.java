package dev.cuboidplots.platform;

import dev.cuboidplots.core.*;
import dev.cuboidplots.integration.ftb.FtbClaimsAdapter;
import dev.cuboidplots.integration.opac.OpacClaimsAdapter;
import dev.cuboidplots.integration.ranks.FtbRanksAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.util.*;
import java.util.function.Predicate;

public final class AddonRuntime {
    public static volatile RegionService regions;
    public static AddonConfig config;
    public static String parentId;
    public static ItemRecovery recovery;
    private AddonRuntime() {}
    public static void start(MinecraftServer server, Predicate<String> loaded) {
        boolean ftb = loaded.test("ftbchunks"), opac = loaded.test("openpartiesandclaims");
        if (ftb == opac) throw new IllegalStateException("CuboidPlots requires exactly one parent: FTB Chunks or Open Parties and Claims. Both or neither is unsupported.");
        try {
            var directory = server.getWorldPath(LevelResource.ROOT).resolve("cuboidplots");
            config = new AddonConfig(directory.resolve("config.properties"));
            ParentClaims parent = ftb ? new FtbClaimsAdapter() : new OpacClaimsAdapter(server);
            parentId = parent.integration();
            boolean ranks = loaded.test("ftbranks") && !config.provider.equals("config");
            if (config.provider.equals("ftbranks") && !ranks) throw new IllegalStateException("Configured FTB Ranks provider is missing");
            LimitProvider provider = ranks ? new FtbRanksAdapter(server, config) : id -> config.fallback;
            recovery = new ItemRecovery(server,directory.resolve("returns"));
            RegionService service = new RegionService(parent, provider, new FileRegionStore(directory.resolve("regions.cplt")),recovery);
            recovery.resumePrepared();
            service.revalidateAll();
            regions = service;
            System.out.println("[CuboidPlots] Ready: parent=" + parentId + ", limits=" + (ranks ? "ftbranks" : "config"));
        } catch (Exception ex) { throw new IllegalStateException("CuboidPlots initialization failed closed", ex); }
    }
    public static void stop() { regions = null; recovery = null; ActionScope.clear(); }
    public static Position position(BlockPos pos) { return new Position(pos.getX(), pos.getY(), pos.getZ()); }
    public static void ownershipChanged(String dimension, int x, int z) {
        RegionService service = regions;
        if (service != null) try { service.ownershipChanged(dimension,x,z); }
        catch (Exception ex) { System.err.println("[CuboidPlots] Failed to persist suspension; exceptions disabled: " + ex); }
    }
}
