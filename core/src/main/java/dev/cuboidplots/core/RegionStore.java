package dev.cuboidplots.core;

import java.io.IOException;
import java.util.List;

public interface RegionStore {
    List<Region> load() throws IOException;
    /** Write the complete new snapshot atomically or throw without replacing the old snapshot. */
    void save(List<Region> regions) throws IOException;
}
