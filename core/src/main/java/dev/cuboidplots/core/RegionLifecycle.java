package dev.cuboidplots.core;

import java.io.IOException;

/** Narrow boundary for platform item recovery. Called under the service's mutation lock. */
public interface RegionLifecycle {
  enum Removal {
    DELETE,
    REASSIGN,
    PARENT_CHANGE
  }

  /** Must durably reserve returns before clearing any inventory or placed blocks. */
  void beforeOwnershipRemoved(Region region, Removal reason, boolean bindingStillCurrent)
      throws IOException;
}
