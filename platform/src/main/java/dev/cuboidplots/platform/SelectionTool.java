package dev.cuboidplots.platform;

import dev.cuboidplots.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;

/** Vanilla stick and vanilla particle packets. No client-side classes or custom networking. */
public final class SelectionTool {
  private static final String MARKER = "CuboidPlotsSurveyor";
  private static final Map<UUID, Preview> previews = new HashMap<>();

  private static final class Preview {
    final String dimension;
    final Cuboid bounds;
    final long expires;

    Preview(String dimension, Cuboid bounds, long expires) {
      this.dimension = dimension;
      this.bounds = bounds;
      this.expires = expires;
    }
  }

  public static boolean isTool(ItemStack stack) {
    return stack.getItem() == Items.STICK && stack.hasTag() && stack.getTag().getBoolean(MARKER);
  }

  public static void give(ServerPlayer player) {
    for (ItemStack item : player.getInventory().items)
      if (isTool(item)) {
        AddonCommands.tell(player, "Your surveyor's stick is already in your inventory");
        return;
      }
    if (player.getInventory().getFreeSlot() < 0)
      throw new IllegalArgumentException("Free an inventory slot for the surveyor's stick");
    ItemStack tool = new ItemStack(Items.STICK);
    tool.getOrCreateTag().putBoolean(MARKER, true);
    tool.setHoverName(
        new net.minecraft.network.chat.TextComponent("Cuboid Surveyor | Left: 1 / Right: 2"));
    player.getInventory().add(tool);
    AddonCommands.tell(
        player,
        "Surveyor's stick: left-click a block for corner 1; right-click for corner 2. /cuboid"
            + " preview redraws the selection.");
  }

  public static void select(ServerPlayer player, BlockPos pos, int corner) {
    AddonCommands.corner(player, corner, pos);
    // Correct any vanilla client block prediction. Selection never breaks or uses the target.
    player.connection.send(new ClientboundBlockUpdatePacket(player.level, pos));
    AddonCommands.Selection selection = AddonCommands.selection(player);
    if (selection.first != null && selection.second != null)
      preview(player, selection.dimension, selection.bounds());
    else
      player
          .getLevel()
          .sendParticles(
              player,
              ParticleTypes.HAPPY_VILLAGER,
              true,
              pos.getX() + 0.5,
              pos.getY() + 1.1,
              pos.getZ() + 0.5,
              8,
              0.2,
              0.2,
              0.2,
              0);
  }

  public static void preview(ServerPlayer player, String dimension, Cuboid bounds) {
    RegionService.validateBounds(bounds);
    if (!player.level.dimension().location().toString().equals(dimension))
      throw new IllegalArgumentException("Travel to " + dimension + " to preview these bounds");
    previews.put(
        player.getUUID(), new Preview(dimension, bounds, player.server.getTickCount() + 20 * 30L));
    draw(player, bounds);
    AddonCommands.tell(player, "Previewing inclusive bounds for 30 seconds: " + bounds);
  }

  public static void tick(MinecraftServer server) {
    if (server.getTickCount() % 10 != 0) return;
    Iterator<Map.Entry<UUID, Preview>> iterator = previews.entrySet().iterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
      Preview preview = entry.getValue();
      if (player == null || server.getTickCount() > preview.expires) {
        iterator.remove();
        continue;
      }
      if (player.level.dimension().location().toString().equals(preview.dimension))
        draw(player, preview.bounds);
    }
  }

  private static void draw(ServerPlayer player, Cuboid box) {
    double[] xs = {box.min.x, (double) box.max.x + 1},
        ys = {box.min.y, (double) box.max.y + 1},
        zs = {box.min.z, (double) box.max.z + 1};
    for (int i = 0; i < 2; i++)
      for (int j = 0; j < 2; j++) {
        line(player, xs[0], ys[i], zs[j], xs[1], ys[i], zs[j]);
        line(player, xs[i], ys[0], zs[j], xs[i], ys[1], zs[j]);
        line(player, xs[i], ys[j], zs[0], xs[i], ys[j], zs[1]);
      }
  }

  private static void line(
      ServerPlayer player, double x, double y, double z, double endX, double endY, double endZ) {
    int steps =
        Math.max(
            1,
            Math.min(
                40, (int) Math.ceil(Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z))));
    for (int step = 0; step <= steps; step++) {
      double fraction = (double) step / steps,
          px = x + (endX - x) * fraction,
          py = y + (endY - y) * fraction,
          pz = z + (endZ - z) * fraction;
      if (player.distanceToSqr(px, py, pz) > 96 * 96) continue;
      player
          .getLevel()
          .sendParticles(player, ParticleTypes.END_ROD, true, px, py, pz, 1, 0, 0, 0, 0);
    }
  }

  public static void clear() {
    previews.clear();
  }
}
