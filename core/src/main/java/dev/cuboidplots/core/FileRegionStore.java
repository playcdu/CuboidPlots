package dev.cuboidplots.core;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;

/** Versioned, checksummed snapshot. A damaged file fails closed, never becomes an empty world. */
public final class FileRegionStore implements RegionStore {
  private static final int MAGIC = 0x43504C54, VERSION = 3, MAX_FILE = 32 * 1024 * 1024;
  private final Path file;

  public FileRegionStore(Path file) {
    this.file = file;
  }

  public List<Region> load() throws IOException {
    if (!Files.exists(file)) return new ArrayList<>();
    if (Files.size(file) > MAX_FILE) throw new IOException("Region snapshot exceeds 32 MiB");
    byte[] bytes = Files.readAllBytes(file);
    if (bytes.length < 20) throw new IOException("Truncated region snapshot");
    CRC32 crc = new CRC32();
    crc.update(bytes, 0, bytes.length - 8);
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      if (in.readInt() != MAGIC || in.readInt() != VERSION)
        throw new IOException("Unknown region snapshot format");
      List<Region> regions = new ArrayList<>();
      int count = count(in, 10000);
      for (int i = 0; i < count; i++) {
        UUID id = uuid(in);
        String name = in.readUTF(),
            integration = in.readUTF(),
            owner = in.readUTF(),
            dimension = in.readUTF();
        UUID creator = uuid(in), assignedOwner = uuid(in);
        Cuboid bounds = new Cuboid(position(in), position(in));
        boolean suspended = in.readBoolean();
        Map<UUID, Set<Action>> grants = new LinkedHashMap<>();
        int players = count(in, 10000);
        for (int p = 0; p < players; p++) {
          UUID player = uuid(in);
          int actions = count(in, Action.values().length);
          Set<Action> permissions = EnumSet.noneOf(Action.class);
          for (int a = 0; a < actions; a++)
            if (!permissions.add(Action.valueOf(in.readUTF())))
              throw new IOException("Duplicate action");
          if (grants.put(player, permissions) != null)
            throw new IOException("Duplicate player entry");
        }
        regions.add(
            new Region(
                id,
                name,
                integration,
                owner,
                dimension,
                creator,
                assignedOwner,
                bounds,
                suspended,
                grants));
      }
      if (in.readLong() != crc.getValue() || in.available() != 0)
        throw new IOException("Region snapshot checksum or length mismatch");
      return regions;
    } catch (RuntimeException ex) {
      throw new IOException("Invalid region snapshot", ex);
    }
  }

  public void save(List<Region> regions) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeInt(MAGIC);
      out.writeInt(VERSION);
      out.writeInt(regions.size());
      for (Region region : regions) {
        uuid(out, region.id);
        out.writeUTF(region.name);
        out.writeUTF(region.integration);
        out.writeUTF(region.parentOwner);
        out.writeUTF(region.dimension);
        uuid(out, region.creator);
        uuid(out, region.assignedOwner);
        position(out, region.bounds.min);
        position(out, region.bounds.max);
        out.writeBoolean(region.suspended);
        out.writeInt(region.grants.size());
        for (Map.Entry<UUID, Set<Action>> entry : region.grants.entrySet()) {
          uuid(out, entry.getKey());
          out.writeInt(entry.getValue().size());
          for (Action action : entry.getValue()) out.writeUTF(action.name());
        }
      }
      CRC32 crc = new CRC32();
      crc.update(bytes.toByteArray());
      out.writeLong(crc.getValue());
    }
    if (bytes.size() > MAX_FILE) throw new IOException("Region snapshot exceeds 32 MiB");
    Files.createDirectories(file.toAbsolutePath().getParent());
    Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
    Files.write(temporary, bytes.toByteArray());
    try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
      channel.force(true);
    }
    // Do not fall back to a non-atomic replace: an interrupted write must keep the old grants.
    Files.move(
        temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
  }

  private static int count(DataInputStream in, int maximum) throws IOException {
    int value = in.readInt();
    if (value < 0 || value > maximum) throw new IOException("Invalid record count " + value);
    return value;
  }

  private static UUID uuid(DataInputStream in) throws IOException {
    return new UUID(in.readLong(), in.readLong());
  }

  private static void uuid(DataOutputStream out, UUID value) throws IOException {
    out.writeLong(value.getMostSignificantBits());
    out.writeLong(value.getLeastSignificantBits());
  }

  private static Position position(DataInputStream in) throws IOException {
    return new Position(in.readInt(), in.readInt(), in.readInt());
  }

  private static void position(DataOutputStream out, Position value) throws IOException {
    out.writeInt(value.x);
    out.writeInt(value.y);
    out.writeInt(value.z);
  }
}
