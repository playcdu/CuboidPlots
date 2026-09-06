package dev.cuboidplots.platform;

import dev.cuboidplots.core.Limits;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** World-local settings. Invalid numbers stop startup rather than silently widening access. */
public final class AddonConfig {
    public final Limits fallback;
    public final String provider, countNode, volumeNode;
    public final Set<UUID> administrators = new HashSet<>();
    public AddonConfig(Path file) throws IOException {
        Properties values = new Properties();
        values.setProperty("provider", "auto");
        values.setProperty("default.max_regions", "16");
        values.setProperty("default.max_volume", "65536");
        values.setProperty("permission.max_regions", "cuboidplots.max_regions");
        values.setProperty("permission.max_volume", "cuboidplots.max_volume");
        values.setProperty("admin_uuids", "");
        if (Files.exists(file)) { try (Reader reader = Files.newBufferedReader(file)) { values.load(reader); } }
        else { Files.createDirectories(file.getParent()); try (Writer writer = Files.newBufferedWriter(file)) { values.store(writer, "CuboidPlots: -1 is explicitly unlimited; 0 allows none. Restart after editing."); } }
        fallback = new Limits(Long.parseLong(values.getProperty("default.max_regions")), Long.parseLong(values.getProperty("default.max_volume")));
        provider = values.getProperty("provider");
        if (!Arrays.asList("auto", "config", "ftbranks").contains(provider)) throw new IllegalArgumentException("Unknown limit provider " + provider);
        countNode = values.getProperty("permission.max_regions"); volumeNode = values.getProperty("permission.max_volume");
        for (String id : values.getProperty("admin_uuids").split(",")) if (!id.trim().isEmpty()) administrators.add(UUID.fromString(id.trim()));
    }
}
