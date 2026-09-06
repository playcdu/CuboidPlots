package dev.cuboidplots.core;

/** Inclusive block geometry. No Minecraft or loader types cross this boundary. */
public final class Cuboid {
  public final Position min, max;

  public Cuboid(Position first, Position second) {
    min =
        new Position(
            Math.min(first.x, second.x), Math.min(first.y, second.y), Math.min(first.z, second.z));
    max =
        new Position(
            Math.max(first.x, second.x), Math.max(first.y, second.y), Math.max(first.z, second.z));
    volume(); // Reject overflow before iteration, quotas or persistence.
  }

  public long volume() {
    return Math.multiplyExact(
        Math.multiplyExact((long) max.x - min.x + 1, (long) max.y - min.y + 1),
        (long) max.z - min.z + 1);
  }

  public long chunkCount() {
    return Math.multiplyExact(
        (long) (max.x >> 4) - (min.x >> 4) + 1, (long) (max.z >> 4) - (min.z >> 4) + 1);
  }

  public boolean contains(Position p) {
    return p.x >= min.x
        && p.x <= max.x
        && p.y >= min.y
        && p.y <= max.y
        && p.z >= min.z
        && p.z <= max.z;
  }

  public boolean intersects(Cuboid other) {
    return min.x <= other.max.x
        && max.x >= other.min.x
        && min.y <= other.max.y
        && max.y >= other.min.y
        && min.z <= other.max.z
        && max.z >= other.min.z;
  }

  public boolean coversChunk(int x, int z) {
    // Arithmetic shift floors negative coordinates, unlike Java integer division.
    return x >= (min.x >> 4) && x <= (max.x >> 4) && z >= (min.z >> 4) && z <= (max.z >> 4);
  }

  @Override
  public String toString() {
    return "[" + min + "] to [" + max + "] (" + volume() + " blocks)";
  }
}
