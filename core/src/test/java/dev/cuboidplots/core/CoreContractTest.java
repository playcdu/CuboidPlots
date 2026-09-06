package dev.cuboidplots.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Dependency-free contract suite, reused verbatim on every Minecraft branch. */
public final class CoreContractTest {
  private static final UUID OWNER = new UUID(0, 1), ALICE = new UUID(0, 2), BOB = new UUID(0, 3);
  private static final String DIM = "minecraft:overworld";
  private static int checks;

  private static final class Claims implements ParentClaims {
    String owner = "team-A";
    final Set<String> missing = new HashSet<>();

    public String integration() {
      return "fixture";
    }

    public String ownerAt(String dim, int x, int z) {
      return missing.contains(dim + ":" + x + ":" + z) ? null : owner;
    }

    public boolean canManage(UUID player, String team) {
      return OWNER.equals(player) && "team-A".equals(team);
    }
  }

  private static final class Provider implements LimitProvider {
    Limits current = new Limits(100, 1000000);
    boolean fail;

    public Limits effectiveLimits(UUID player) throws Exception {
      if (fail) throw new IOException("fixture unavailable");
      return current;
    }
  }

  private static final class Memory implements RegionStore {
    List<Region> saved = new ArrayList<>();
    boolean fail;

    public List<Region> load() {
      return new ArrayList<>(saved);
    }

    public void save(List<Region> regions) throws IOException {
      if (fail) throw new IOException("fixture disk failure");
      saved = new ArrayList<>(regions);
    }
  }

  private static final class Fixture {
    final Claims claims = new Claims();
    final Provider provider = new Provider();
    final Memory store = new Memory();
    final RegionService service;

    Fixture() throws IOException {
      service = new RegionService(claims, provider, store);
    }

    Region create(String name, Cuboid bounds) throws IOException {
      return service.create(OWNER, false, name, DIM, bounds);
    }

    void grant(String region, UUID player, Action action) throws IOException {
      service.permission(OWNER, false, region, player, action, true);
    }

    boolean allowed(UUID player, Action action, Position... positions) {
      return service.evaluate(player, DIM, action, Arrays.asList(positions), true).isGrant();
    }
  }

  private interface Checked {
    void run() throws Exception;
  }

  private static void ok(boolean condition, String name) {
    checks++;
    if (!condition) throw new AssertionError(name);
  }

  private static void rejected(Checked operation, String name) throws Exception {
    try {
      operation.run();
    } catch (IllegalArgumentException
        | IllegalStateException
        | SecurityException
        | ArithmeticException expected) {
      checks++;
      return;
    }
    throw new AssertionError("Expected rejection: " + name);
  }

  private static Position p(int x, int y, int z) {
    return new Position(x, y, z);
  }

  private static Cuboid box(int x, int y, int z, int a, int b, int c) {
    return new Cuboid(p(x, y, z), p(a, b, c));
  }

  public static void main(String[] args) throws Exception {
    geometry();
    permissions();
    delegation();
    ownership();
    quotas();
    persistence();
    concurrency();
    System.out.println(
        "PASS: " + checks + " portable core contract assertions (not Minecraft integration tests)");
  }

  private static void geometry() throws Exception {
    Cuboid single = box(-1, 64, -1, -1, 64, -1);
    ok(single.volume() == 1 && single.chunkCount() == 1, "inclusive single block");
    ok(single.coversChunk(-1, -1) && !single.coversChunk(0, 0), "negative floor division");
    Cuboid four = box(-1, 63, -1, 16, 65, 16);
    ok(four.chunkCount() == 9 && four.volume() == 972, "negative multiple chunk boundaries");
    ok(box(0, 0, 0, 31, 0, 31).chunkCount() == 4, "exact four chunks");
    ok(!single.intersects(box(-1, 65, -1, -1, 66, -1)), "vertical adjacency");
    rejected(
        () ->
            box(
                Integer.MIN_VALUE,
                Integer.MIN_VALUE,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE),
        "overflow");
    rejected(() -> RegionService.validateBounds(box(0, 0, 0, 1000000, 0, 0)), "chunk safety cap");
  }

  private static void permissions() throws Exception {
    Fixture f = new Fixture();
    f.create("west", box(0, 64, 0, 3, 66, 3));
    f.create("east", box(4, 64, 0, 7, 66, 3));
    f.create("upstairs", box(0, 67, 0, 3, 70, 3));
    f.grant("west", ALICE, Action.BREAK);
    f.grant("east", BOB, Action.PLACE);
    f.grant("upstairs", BOB, Action.CONTAINER);
    ok(f.allowed(ALICE, Action.BREAK, p(0, 64, 0)), "inside exact target");
    ok(!f.allowed(ALICE, Action.BREAK, p(4, 64, 0)), "standing inside never grants outside");
    ok(!f.allowed(ALICE, Action.PLACE, p(0, 64, 0)), "break does not grant place");
    ok(
        f.allowed(BOB, Action.PLACE, p(4, 64, 0)) && !f.allowed(BOB, Action.BREAK, p(4, 64, 0)),
        "place does not grant break");
    ok(
        f.allowed(BOB, Action.CONTAINER, p(0, 67, 0))
            && !f.allowed(BOB, Action.CONTAINER, p(0, 66, 0)),
        "stacked vertical bounds");
    ok(
        !f.allowed(ALICE, Action.BREAK, p(3, 64, 0), p(4, 64, 0)),
        "area action crossing adjacent grants");
    ok(
        !f.allowed(BOB, Action.PLACE, p(7, 64, 0), p(8, 64, 0)),
        "bed or door second position outside");
    f.grant("west", ALICE, Action.OTHER_BLOCK);
    ok(
        !f.allowed(ALICE, Action.CONTAINER, p(0, 64, 0)),
        "generic does not override container category");
    for (Action action : Action.values()) {
      f.grant("west", BOB, action);
      ok(f.allowed(BOB, action, p(1, 65, 1)), "independent category " + action);
      f.service.permission(OWNER, false, "west", BOB, action, false);
      ok(!f.allowed(BOB, action, p(1, 65, 1)), "revocation " + action);
    }
    ok(
        !f.service
            .evaluate(ALICE, DIM, Action.BREAK, Collections.singletonList(p(0, 64, 0)), false)
            .isGrant(),
        "unknown indirect effects");
    f.service.create(OWNER, false, "nether", "minecraft:the_nether", box(0, 64, 0, 3, 66, 3));
    ok(
        !f.service
            .evaluate(
                ALICE,
                "minecraft:the_nether",
                Action.BREAK,
                Collections.singletonList(p(0, 64, 0)),
                true)
            .isGrant(),
        "dimension independent");
    rejected(() -> f.create("overlap", box(3, 66, 3, 4, 67, 4)), "inclusive overlap");
    rejected(
        () -> f.service.permission(ALICE, false, "west", ALICE, Action.PLACE, true),
        "recipient cannot manage");
    rejected(
        () -> f.service.inspect(ALICE, false, "west"),
        "recipient cannot inspect sensitive entries");
    ok(f.service.list(ALICE, false).isEmpty(), "recipient list privacy");
  }

  private static void ownership() throws Exception {
    Fixture f = new Fixture();
    f.create("four", box(0, 64, 0, 31, 64, 31));
    f.grant("four", ALICE, Action.BREAK);
    f.claims.missing.add(DIM + ":1:1");
    ok(
        !f.allowed(ALICE, Action.BREAK, p(0, 64, 0)),
        "missing remote covered chunk disables whole region");
    f.claims.missing.clear();
    ok(!f.allowed(ALICE, Action.BREAK, p(0, 64, 0)), "suspension persists after reclaim");
    ok(f.service.usage(OWNER)[0] == 1, "suspended region still charged");
    f.service.delete(OWNER, false, "four");
    f.create("new", box(0, 64, 0, 1, 64, 1));
    f.grant("new", ALICE, Action.BREAK);
    f.service.ownershipChanged(DIM, 0, 0);
    ok(!f.allowed(ALICE, Action.BREAK, p(0, 64, 0)), "same-owner unclaim reclaim event");
    Fixture transfer = new Fixture();
    transfer.create("land", box(0, 64, 0, 1, 64, 1));
    transfer.grant("land", ALICE, Action.BREAK);
    transfer.claims.owner = "team-B";
    ok(!transfer.allowed(ALICE, Action.BREAK, p(0, 64, 0)), "ownership transfer");
    Fixture wilderness = new Fixture();
    wilderness.claims.missing.add(DIM + ":1:0");
    rejected(() -> wilderness.create("bad", box(0, 64, 0, 31, 64, 15)), "crossing wilderness");
  }

  private static void delegation() throws Exception {
    Claims parent = new Claims();
    Memory memory = new Memory();
    boolean[] fail = {true};
    RegionService recovering =
        new RegionService(
            parent,
            new Provider(),
            memory,
            (region, reason, current) -> {
              if (fail[0]) throw new IOException("Recovery fixture unavailable");
            });
    recovering.create(OWNER, false, "retry", DIM, box(0, 0, 0, 1, 1, 1));
    try {
      recovering.ownershipChanged(DIM, 0, 0);
      throw new AssertionError("Recovery failure was ignored");
    } catch (IOException expected) {
      checks++;
    }
    ok(
        !recovering.inspect(OWNER, false, "retry").suspended,
        "failed recovery leaves ownership removal retryable");
    fail[0] = false;
    recovering.ownershipChanged(DIM, 0, 0);
    ok(
        recovering.inspect(OWNER, false, "retry").suspended,
        "successful retry suspends recovered region");
    Fixture f = new Fixture();
    f.create("leased", box(0, 0, 0, 2, 2, 2));
    f.service.assignOwner(OWNER, false, "leased", ALICE);
    f.service.permission(ALICE, false, "leased", BOB, Action.CONTAINER, true);
    ok(f.allowed(BOB, Action.CONTAINER, p(0, 0, 0)), "assigned owner delegates cuboid permissions");
    ok(
        !f.allowed(ALICE, Action.BREAK, p(0, 0, 0)),
        "assignment adds no implicit action permissions");
    ok(
        f.service.inspect(ALICE, false, "leased").assignedOwner.equals(ALICE),
        "assigned owner sees region details");
    ok(
        f.service.usage(OWNER)[0] == 1 && f.service.usage(ALICE)[0] == 0,
        "assignment preserves creator quota");
    rejected(
        () -> f.service.resize(ALICE, false, "leased", box(0, 0, 0, 1, 1, 1)),
        "assigned owner cannot resize");
    rejected(
        () -> f.service.assignOwner(ALICE, false, "leased", BOB), "assigned owner cannot reassign");
    rejected(() -> f.service.delete(ALICE, false, "leased"), "assigned owner cannot delete");
    f.service.assignOwner(OWNER, false, "leased", BOB);
    rejected(
        () -> f.service.permission(ALICE, false, "leased", ALICE, Action.BREAK, true),
        "reassignment revokes old management authority");
  }

  private static void quotas() throws Exception {
    Fixture f = new Fixture();
    f.provider.current = new Limits(1, 8);
    f.create("exact", box(0, 0, 0, 1, 1, 1));
    ok(f.service.usage(OWNER)[1] == 8, "inclusive volume including air exact limit");
    rejected(() -> f.create("count", box(5, 0, 0, 5, 0, 0)), "region count independently");
    rejected(
        () -> f.service.resize(OWNER, false, "exact", box(0, 0, 0, 2, 1, 1)),
        "volume independently");
    ok(f.service.usage(OWNER)[1] == 8, "failed resize is atomic");
    f.grant("exact", ALICE, Action.BREAK);
    ok(f.service.usage(ALICE)[0] == 0, "recipient not charged");
    f.provider.current = new Limits(0, 1);
    f.service.resize(OWNER, false, "exact", box(0, 0, 0, 1, 0, 1));
    ok(f.service.usage(OWNER)[1] == 4, "downgrade permits reduction while still over");
    rejected(
        () -> f.service.resize(OWNER, false, "exact", box(0, 0, 0, 1, 1, 1)),
        "downgrade rejects increase");
    f.provider.fail = true;
    rejected(
        () -> f.service.resize(OWNER, false, "exact", box(0, 0, 0, 1, 1, 1)),
        "provider failure fails closed");
    f.service.resize(OWNER, false, "exact", box(0, 0, 0, 0, 0, 0));
    f.service.delete(OWNER, false, "exact");
    ok(
        f.service.usage(OWNER)[0] == 0 && f.service.usage(OWNER)[1] == 0,
        "deletion releases allowance despite failure");
    f.provider.fail = false;
    f.provider.current = new Limits(-1, -1);
    f.create("unlimited", box(0, 0, 0, 0, 0, 0));
    ok(f.service.usage(OWNER)[0] == 1, "explicit unlimited");
    rejected(() -> new Limits(-2, 10), "invalid negative limit");
    Fixture zero = new Fixture();
    zero.provider.current = new Limits(0, 100);
    rejected(() -> zero.create("none", box(0, 0, 0, 0, 0, 0)), "zero region limit");
    zero.provider.current = new Limits(100, 0);
    rejected(() -> zero.create("none", box(0, 0, 0, 0, 0, 0)), "zero volume limit");
  }

  private static void persistence() throws Exception {
    Path dir = Files.createTempDirectory("cuboidplots-contract-");
    Path path = dir.resolve("regions.cplt");
    Claims claims = new Claims();
    Provider provider = new Provider();
    RegionService service = new RegionService(claims, provider, new FileRegionStore(path));
    service.create(OWNER, false, "saved", DIM, box(-16, -64, -16, 16, 64, 16));
    service.permission(OWNER, false, "saved", ALICE, Action.BREAK, true);
    RegionService restarted = new RegionService(claims, provider, new FileRegionStore(path));
    restarted.revalidateAll();
    ok(
        restarted
            .evaluate(ALICE, DIM, Action.BREAK, Collections.singletonList(p(0, 0, 0)), true)
            .isGrant(),
        "real filesystem restart grant");
    ok(restarted.usage(OWNER)[1] == 140481, "restart quota usage");
    byte[] bytes = Files.readAllBytes(path);
    bytes[20] ^= 1;
    Files.write(path, bytes);
    try {
      new FileRegionStore(path).load();
      throw new AssertionError("corruption accepted");
    } catch (IOException expected) {
      checks++;
    }
    Files.delete(path);
    Files.delete(dir);
    Fixture failure = new Fixture();
    failure.create("before", box(0, 0, 0, 0, 0, 0));
    failure.grant("before", ALICE, Action.BREAK);
    failure.store.fail = true;
    try {
      failure.create("failed", box(2, 0, 0, 2, 0, 0));
      throw new AssertionError("disk error swallowed");
    } catch (IOException expected) {
      checks++;
    }
    ok(failure.service.usage(OWNER)[0] == 1, "failed save did not change quotas");
    ok(!failure.allowed(ALICE, Action.BREAK, p(0, 0, 0)), "storage fault disables exceptions");
  }

  private static void concurrency() throws Exception {
    Fixture f = new Fixture();
    f.provider.current = new Limits(1, 100);
    AtomicInteger accepted = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(8);
    List<Future<?>> tasks = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      final int n = i;
      tasks.add(
          pool.submit(
              () -> {
                try {
                  f.create("r" + n, box(n, 0, 0, n, 0, 0));
                  accepted.incrementAndGet();
                } catch (IllegalArgumentException expected) {
                } catch (IOException ex) {
                  throw new UncheckedIOException(ex);
                }
              }));
    }
    for (Future<?> task : tasks) task.get();
    pool.shutdown();
    ok(
        accepted.get() == 1 && f.service.usage(OWNER)[0] == 1,
        "concurrent operations cannot overspend");
  }
}
