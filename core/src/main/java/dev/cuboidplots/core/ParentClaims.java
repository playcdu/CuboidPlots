package dev.cuboidplots.core;

import java.util.UUID;

/** Implementations must read authoritative server state, never a client claim map. */
public interface ParentClaims {
  String integration();

  /** Null means wilderness, unavailable dimension, or an unavailable claim manager. */
  String ownerAt(String dimension, int chunkX, int chunkZ);

  /** Management authority is distinct from ordinary block interaction permission. */
  boolean canManage(UUID player, String owner);
}
