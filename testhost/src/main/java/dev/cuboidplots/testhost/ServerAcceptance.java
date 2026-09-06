package dev.cuboidplots.testhost;

import com.mojang.authlib.GameProfile;
import dev.cuboidplots.core.*;
import dev.cuboidplots.platform.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;

/** Included only by -PtestHost. Real parent methods and world mutations on a dedicated server. */
public final class ServerAcceptance {
  private static final UUID OWNER = new UUID(1, 1), ALICE = new UUID(1, 2), BOB = new UUID(1, 3);
  private static int checks;

  private static void check(boolean value, String label) {
    if (!value) throw new AssertionError(label);
    checks++;
    System.out.println("[CuboidPlots test] PASS " + label);
  }

  private static ServerPlayer player(MinecraftServer server, UUID id, String name) {
    ServerPlayer player = new ServerPlayer(server, server.overworld(), new GameProfile(id, name));
    player.connection =
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
    player.setPos(1, 100, 1);
    return player;
  }

  public static void run(MinecraftServer server) {
    Path report = server.getWorldPath(LevelResource.ROOT).resolve("cuboidplots-acceptance.txt");
    try {
      ServerLevel level = server.overworld();
      String dim = level.dimension().location().toString();
      ServerPlayer owner = player(server, OWNER, "CuboidOwner"),
          alice = player(server, ALICE, "CuboidAlice"),
          bob = player(server, BOB, "CuboidBob");
      check(
          !server.getPlayerList().isOp(alice.getGameProfile())
              && !server.getPlayerList().isOp(bob.getGameProfile()),
          "two ordinary non-operator identities");
      ParentFixture parent =
          AddonRuntime.parentId.equals("ftbchunks")
              ? new FtbFixture(server)
              : new OpacFixture(server);
      parent.setup(owner, alice, bob);
      RegionService service = AddonRuntime.regions;
      if (Files.exists(report)
          && new String(Files.readAllBytes(report), java.nio.charset.StandardCharsets.UTF_8)
              .startsWith("FIRST RUN PASS")) {
        check(
            service.inspect(OWNER, false, "lower").grants.get(ALICE).contains(Action.BREAK),
            "grant persisted across actual server restart");
        BlockPos inside = new BlockPos(2, 100, 2);
        level.setBlockAndUpdate(inside, Blocks.STONE.defaultBlockState());
        try (ActionScope scope = ActionPlanner.breaking(alice, inside)) {
          check(
              !parent.breakDenied(alice, inside),
              "persisted grant traverses parent protection on restarted server");
        }
        int waiting = AddonRuntime.recovery.available(ALICE);
        check(waiting >= 2, "offline owner's return boxes persisted across restart");
        int collected = AddonRuntime.recovery.collect(alice);
        check(
            collected == waiting && AddonRuntime.recovery.available(ALICE) == 0,
            "assigned owner collects all queued boxes");
        check(
            AddonRuntime.recovery.collect(alice) == 0,
            "repeated collection does not duplicate items");
        check(
            returnedCount(alice, "minecraft:gold_ingot") == 27 * 64
                && returnedCount(alice, "minecraft:diamond") == 5
                && returnedCount(alice, "minecraft:chest") == 1,
            "restarted recovery preserves exact container, dropped-item and block-loot counts");
        Files.write(
            report,
            Arrays.asList("RESTART PASS " + checks + " assertions"),
            StandardOpenOption.APPEND);
        return;
      }
      for (int x = 0; x < 4; x++) for (int z = 0; z < 2; z++) parent.claim(owner, x, z);
      // High above flat terrain, with a deliberately inert neighborhood for bounded actions.
      for (BlockPos pos : BlockPos.betweenClosed(0, 97, 0, 63, 111, 31))
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
      service.create(OWNER, false, "lower", dim, box(0, 99, 0, 5, 102, 5));
      service.create(OWNER, false, "beside", dim, box(6, 99, 0, 11, 102, 5));
      service.create(OWNER, false, "upper", dim, box(0, 103, 0, 5, 107, 5));
      service.create(OWNER, false, "four", dim, box(16, 99, 0, 47, 100, 31));
      service.permission(OWNER, false, "lower", ALICE, Action.BREAK, true);
      service.permission(OWNER, false, "beside", BOB, Action.PLACE, true);
      service.permission(OWNER, false, "upper", BOB, Action.CONTAINER, true);
      BlockPos inside = new BlockPos(2, 100, 2),
          outside = new BlockPos(12, 100, 2),
          other = new BlockPos(8, 100, 2);
      level.setBlockAndUpdate(inside, Blocks.STONE.defaultBlockState());
      level.setBlockAndUpdate(outside, Blocks.STONE.defaultBlockState());
      check(
          parent.breakDenied(alice, inside),
          "parent baseline denies untrusted player without action scope");
      try (ActionScope scope = ActionPlanner.breaking(alice, inside)) {
        check(!parent.breakDenied(alice, inside), "cuboid exception overrides real parent denial");
      }
      try (ActionScope scope = ActionPlanner.breaking(alice, outside)) {
        check(parent.breakDenied(alice, outside), "standing inside does not grant target outside");
      }
      alice.setPos(13, 100, 2);
      try (ActionScope scope = ActionPlanner.breaking(alice, inside)) {
        check(!parent.breakDenied(alice, inside), "standing outside can act on authorized target");
      }
      check(
          alice.gameMode.destroyBlock(inside) && level.getBlockState(inside).isAir(),
          "actual game mode destroys authorized block");
      check(
          !alice.gameMode.destroyBlock(outside) && level.getBlockState(outside).is(Blocks.STONE),
          "actual game mode leaves denied outside block intact");
      level.setBlockAndUpdate(inside, Blocks.STONE.defaultBlockState());
      service.permission(OWNER, false, "lower", ALICE, Action.BREAK, false);
      try (ActionScope scope = ActionPlanner.breaking(alice, inside)) {
        check(parent.breakDenied(alice, inside), "revocation restores real parent denial");
      }
      service.permission(OWNER, false, "lower", ALICE, Action.BREAK, true);
      check(
          service.usage(OWNER)[0] == 4, "two same-chunk, stacked, and four-chunk cuboids coexist");
      placementAndContainers(service, level, alice, bob, dim);
      interactions(service, parent, level, owner, alice, bob, dim);
      surveyor(owner, level);
      RegionMenu.open(owner);
      check(
          owner.containerMenu.slots.size() == 90,
          "server opens vanilla 54-slot chest menu plus player inventory");
      owner.containerMenu.clicked(0, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, owner);
      check(
          owner.containerMenu.getCarried().isEmpty(),
          "menu shift-click cannot take decorative items");
      owner.closeContainer();
      recovery(service, level, owner, alice, dim);
      dimensionsAndTransfer(service, parent, level, owner, alice, bob, dim);
      service.create(OWNER, false, "stale", dim, box(48, 99, 0, 51, 101, 3));
      service.permission(OWNER, false, "stale", ALICE, Action.BREAK, true);
      parent.unclaim(owner, 3, 0);
      check(
          service.inspect(OWNER, false, "stale").suspended,
          "real parent unclaim suspends stale cuboid grants");
      Files.write(
          report,
          Arrays.asList(
              "FIRST RUN PASS " + checks + " assertions",
              "Scope: dedicated-server parent calls and selected world mutations; not client packet"
                  + " verification."));
    } catch (Throwable ex) {
      ex.printStackTrace();
      try {
        Files.write(report, Arrays.asList("FAIL " + ex));
      } catch (Exception ignored) {
      }
    } finally {
      System.out.println("[CuboidPlots test] Test run finished, stopping server");
      server.halt(false);
    }
  }

  private static Cuboid box(int x, int y, int z, int a, int b, int c) {
    return new Cuboid(new Position(x, y, z), new Position(a, b, c));
  }

  private static InteractionResult use(ServerPlayer player, BlockPos clicked, Item item) {
    ItemStack held = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    player.setItemInHand(InteractionHand.MAIN_HAND, held);
    return player.gameMode.useItemOn(
        player,
        player.level(),
        held,
        InteractionHand.MAIN_HAND,
        new BlockHitResult(Vec3.atCenterOf(clicked).add(0, 0.5, 0), Direction.UP, clicked, false));
  }

  private static void placementAndContainers(
      RegionService service, ServerLevel level, ServerPlayer alice, ServerPlayer bob, String dim)
      throws Exception {
    BlockPos support = new BlockPos(8, 99, 2);
    level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
    use(bob, support, Items.OAK_PLANKS);
    check(
        level.getBlockState(support.above()).is(Blocks.OAK_PLANKS),
        "actual placement allowed independently of breaking");
    bob.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    check(
        !bob.gameMode.destroyBlock(support.above()), "place grant does not permit actual breaking");
    BlockPos denied = new BlockPos(2, 99, 4);
    level.setBlockAndUpdate(denied, Blocks.STONE.defaultBlockState());
    use(alice, denied, Items.OAK_PLANKS);
    check(
        level.getBlockState(denied.above()).isAir(),
        "break grant does not permit actual placement");
    BlockPos boundary = new BlockPos(11, 99, 4);
    level.setBlockAndUpdate(boundary, Blocks.STONE.defaultBlockState());
    bob.setYRot(-90);
    use(bob, boundary, Items.RED_BED);
    check(
        level.getBlockState(boundary.above()).isAir()
            && level.getBlockState(boundary.above().east()).isAir(),
        "bed second block beyond boundary refuses exception");
    BlockPos chest = new BlockPos(2, 104, 2);
    level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
    use(bob, chest, Items.AIR);
    check(
        bob.containerMenu != bob.inventoryMenu,
        "container-only grant opens actual chest in vertically separate cuboid");
    bob.closeContainer();
    use(alice, chest, Items.AIR);
    check(alice.containerMenu == alice.inventoryMenu, "other player cannot open same chest");
  }

  private static void recovery(
      RegionService service, ServerLevel level, ServerPlayer owner, ServerPlayer alice, String dim)
      throws Exception {
    service.create(OWNER, false, "recovery", dim, box(0, 149, 0, 5, 154, 5));
    service.assignOwner(OWNER, false, "recovery", ALICE);
    BlockPos support = new BlockPos(2, 149, 2);
    level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
    use(owner, support, Items.CHEST);
    BlockPos chest = support.above();
    check(
        level.getBlockEntity(chest) instanceof ChestBlockEntity,
        "owner places tracked recovery chest through game mode");
    ChestBlockEntity container = (ChestBlockEntity) level.getBlockEntity(chest);
    for (int i = 0; i < container.getContainerSize(); i++)
      container.setItem(i, new ItemStack(Items.GOLD_INGOT, 64));
    level.addFreshEntity(new ItemEntity(level, 3.5, 151, 3.5, new ItemStack(Items.DIAMOND, 5)));
    service.delete(OWNER, false, "recovery");
    check(level.getBlockState(chest).isAir(), "deletion removes tracked placed block");
    check(
        level.getBlockState(support).is(Blocks.STONE),
        "deletion preserves untracked preexisting terrain");
    check(
        AddonRuntime.recovery.available(ALICE) >= 2,
        "inventory contents, block loot and dropped items packed across multiple boxes");
    check(
        AddonRuntime.recovery.available(OWNER) == 0,
        "returns reserved for assigned owner, not parent owner");
  }

  private static void surveyor(ServerPlayer player, ServerLevel level) {
    player.getInventory().clearContent();
    SelectionTool.give(player);
    check(SelectionTool.isTool(player.getMainHandItem()), "surveyor is a marked vanilla stick");
    BlockPos first = new BlockPos(2, 100, 2), second = new BlockPos(4, 100, 4);
    level.setBlockAndUpdate(first, Blocks.STONE.defaultBlockState());
    level.setBlockAndUpdate(second, Blocks.STONE.defaultBlockState());
    player.gameMode.handleBlockBreakAction(
        first,
        net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action
            .START_DESTROY_BLOCK,
        Direction.UP,
        level.getMaxBuildHeight(),
        0);
    player.gameMode.useItemOn(
        player,
        level,
        player.getMainHandItem(),
        InteractionHand.MAIN_HAND,
        new BlockHitResult(Vec3.atCenterOf(second), Direction.UP, second, false));
    check(
        AddonCommands.selection(player).bounds().volume() == 9,
        "stick clicks set inclusive corners and invoke particle preview");
    check(
        level.getBlockState(first).is(Blocks.STONE) && level.getBlockState(second).is(Blocks.STONE),
        "selection leaves target blocks intact");
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
  }

  private static int returnedCount(ServerPlayer player, String item) {
    int count = 0;
    for (ItemStack box : player.getInventory().items) {
      if (!box.hasTag()) continue;
      var contents =
          box.getTag()
              .getCompound("BlockEntityTag")
              .getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND);
      for (int i = 0; i < contents.size(); i++)
        if (contents.getCompound(i).getString("id").equals(item))
          count += contents.getCompound(i).getByte("Count");
    }
    return count;
  }

  private static void dimensionsAndTransfer(
      RegionService service,
      ParentFixture parent,
      ServerLevel level,
      ServerPlayer owner,
      ServerPlayer alice,
      ServerPlayer bob,
      String dim)
      throws Exception {
    parent.claimDimension(owner, net.minecraft.world.level.Level.END, 0, 0);
    service.create(OWNER, false, "end_lower", "minecraft:the_end", box(0, 99, 0, 5, 102, 5));
    service.permission(OWNER, false, "end_lower", BOB, Action.BREAK, true);
    check(
        !service
            .evaluate(
                ALICE,
                "minecraft:the_end",
                Action.BREAK,
                Collections.singletonList(new Position(2, 100, 2)),
                true)
            .isGrant(),
        "actual End parent claim has independent UUID permissions at identical coordinates");
    check(
        service
            .evaluate(
                BOB,
                "minecraft:the_end",
                Action.BREAK,
                Collections.singletonList(new Position(2, 100, 2)),
                true)
            .isGrant(),
        "End cuboid grant does not depend on overworld coordinates");
    service.permission(OWNER, false, "four", ALICE, Action.BREAK, true);
    for (int x : new int[] {17, 33})
      for (int z : new int[] {1, 17}) {
        BlockPos target = new BlockPos(x, 100, z);
        level.setBlock(target, Blocks.STONE.defaultBlockState(), 2);
        check(
            alice.gameMode.destroyBlock(target),
            "actual break allowed in covered chunk " + (x >> 4) + "," + (z >> 4));
      }
    parent.claim(owner, -1, -1);
    service.create(OWNER, false, "negative", dim, box(-16, 150, -16, -1, 154, -1));
    service.permission(OWNER, false, "negative", ALICE, Action.BREAK, true);
    BlockPos negative = new BlockPos(-16, 152, -16);
    level.setBlock(negative, Blocks.STONE.defaultBlockState(), 2);
    check(
        alice.gameMode.destroyBlock(negative),
        "actual parent exception works at a negative chunk boundary");
    parent.claim(owner, 4, 0);
    service.create(OWNER, false, "transfer", dim, box(64, 149, 0, 69, 154, 5));
    service.assignOwner(OWNER, false, "transfer", ALICE);
    level.addFreshEntity(new ItemEntity(level, 66.5, 151, 2.5, new ItemStack(Items.EMERALD, 7)));
    int before = AddonRuntime.recovery.available(ALICE);
    parent.transfer(owner, bob, 4, 0);
    check(
        service.inspect(OWNER, false, "transfer").suspended,
        "direct parent ownership transfer suspends region");
    check(
        AddonRuntime.recovery.available(ALICE) > before,
        "parent ownership transfer reserves old assigned owner's items");
  }

  private static void interactions(
      RegionService service,
      ParentFixture parent,
      ServerLevel level,
      ServerPlayer owner,
      ServerPlayer alice,
      ServerPlayer bob,
      String dim)
      throws Exception {
    service.create(OWNER, false, "actions", dim, box(0, 119, 0, 47, 145, 31));
    for (BlockPos p : BlockPos.betweenClosed(0, 118, 0, 47, 146, 31))
      level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
    BlockPos door = new BlockPos(3, 125, 8),
        lever = new BlockPos(11, 125, 8),
        note = new BlockPos(19, 125, 8),
        pumpkin = new BlockPos(27, 125, 8);
    for (BlockPos p : Arrays.asList(door, lever, note, pumpkin))
      level.setBlockAndUpdate(p.below(), Blocks.STONE.defaultBlockState());
    level.setBlock(door, Blocks.OAK_DOOR.defaultBlockState(), 2);
    level.setBlock(
        door.above(),
        Blocks.OAK_DOOR
            .defaultBlockState()
            .setValue(
                DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),
        2);
    use(bob, door, Items.AIR);
    check(!level.getBlockState(door).getValue(DoorBlock.OPEN), "parent denies ungranted door use");
    service.permission(OWNER, false, "actions", ALICE, Action.DOOR, true);
    use(alice, door, Items.AIR);
    check(
        level.getBlockState(door).getValue(DoorBlock.OPEN),
        "door-only grant opens actual two-block door");
    level.setBlock(
        lever,
        Blocks.LEVER
            .defaultBlockState()
            .setValue(
                LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR),
        2);
    use(alice, lever, Items.AIR);
    check(
        !level.getBlockState(lever).getValue(LeverBlock.POWERED),
        "door grant does not permit switch use");
    service.permission(OWNER, false, "actions", ALICE, Action.SWITCH, true);
    use(alice, lever, Items.AIR);
    check(
        level.getBlockState(lever).getValue(LeverBlock.POWERED),
        "switch-only exception powers actual isolated lever");
    level.setBlock(note, Blocks.NOTE_BLOCK.defaultBlockState(), 2);
    use(alice, note, Items.AIR);
    check(
        level.getBlockState(note).getValue(NoteBlock.NOTE) == 0,
        "switch grant does not permit other block interactions");
    service.permission(OWNER, false, "actions", ALICE, Action.OTHER_BLOCK, true);
    use(alice, note, Items.AIR);
    check(
        level.getBlockState(note).getValue(NoteBlock.NOTE) == 1,
        "other-block exception changes actual note block");
    level.setBlock(pumpkin, Blocks.PUMPKIN.defaultBlockState(), 2);
    use(alice, pumpkin, Items.SHEARS);
    check(
        level.getBlockState(pumpkin).is(Blocks.PUMPKIN),
        "other-block grant does not permit item-on-block use");
    service.permission(OWNER, false, "actions", ALICE, Action.ITEM_ON_BLOCK, true);
    use(alice, pumpkin, Items.SHEARS);
    check(
        level.getBlockState(pumpkin).is(Blocks.CARVED_PUMPKIN),
        "item-on-block exception carves actual pumpkin");
    alice.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    bob.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    var frame =
        new net.minecraft.world.entity.decoration.ItemFrame(
            level, new BlockPos(36, 125, 8), Direction.SOUTH);
    level.addFreshEntity(frame);
    check(parent.entityDenied(alice, frame, false), "parent denies ungranted entity interaction");
    service.permission(OWNER, false, "actions", ALICE, Action.ENTITY_INTERACT, true);
    try (ActionScope scope = ActionPlanner.entity(alice, frame, false)) {
      check(
          !parent.entityDenied(alice, frame, false),
          "entity interaction traverses parent protection with bounded target");
    }
    try (ActionScope scope = ActionPlanner.entity(alice, frame, true)) {
      check(
          parent.entityDenied(alice, frame, true),
          "entity interaction grant does not permit damage");
    }
    service.permission(OWNER, false, "actions", ALICE, Action.ENTITY_DAMAGE, true);
    try (ActionScope scope = ActionPlanner.entity(alice, frame, true)) {
      check(
          !parent.entityDenied(alice, frame, true),
          "entity damage traverses parent protection independently");
    }
    frame.discard();
    alice.setPos(40, 125, 8);
    ItemStack food = new ItemStack(Items.APPLE);
    alice.setItemInHand(InteractionHand.MAIN_HAND, food);
    try (ActionScope scope = ActionPlanner.useAir(alice, food)) {
      check(
          !ActionScope.grants(alice, BlockPos.containing(alice.getEyePosition()), null),
          "item-in-air scope has no implicit region grant");
    }
    service.permission(OWNER, false, "actions", ALICE, Action.ITEM_IN_AIR, true);
    try (ActionScope scope = ActionPlanner.useAir(alice, food)) {
      check(
          ActionScope.grants(alice, BlockPos.containing(alice.getEyePosition()), null),
          "explicit item-in-air grant uses eye position for bounded food use");
    }
    // Both parents normally allow ordinary food. Preserve that policy rather than inventing a
    // denial.
    alice.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    BlockPos outside = new BlockPos(48, 125, 8);
    level.setBlock(outside, Blocks.STONE.defaultBlockState(), 2);
    use(alice, outside, Items.WATER_BUCKET);
    check(
        level.getBlockState(outside.above()).getFluidState().isEmpty(),
        "unbounded bucket effects receive no region exception");
    alice.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
  }
}
