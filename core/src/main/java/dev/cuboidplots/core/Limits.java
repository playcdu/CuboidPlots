package dev.cuboidplots.core;

public final class Limits {
  public final long regions, volume;

  public Limits(long regions, long volume) {
    if (regions < -1 || volume < -1)
      throw new IllegalArgumentException("Limits must be nonnegative or -1 (unlimited)");
    this.regions = regions;
    this.volume = volume;
  }

  public static long remaining(long used, long limit) {
    return limit == -1 ? -1 : Math.max(0, limit - used);
  }

  public static void check(long before, long after, long limit, String label) {
    // Rank downgrades preserve existing regions and permit non-increasing changes.
    if (limit != -1 && after > limit && after > before)
      throw new IllegalArgumentException(
          label
              + " limit "
              + limit
              + " exceeded by "
              + (after - limit)
              + " (requested usage "
              + after
              + ")");
  }
}
