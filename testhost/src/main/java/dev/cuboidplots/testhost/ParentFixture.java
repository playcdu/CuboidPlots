package dev.cuboidplots.testhost;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

interface ParentFixture {
  void setup(ServerPlayer... players);

  void claim(ServerPlayer owner, int x, int z);

  void unclaim(ServerPlayer owner, int x, int z);

  boolean breakDenied(ServerPlayer player, BlockPos pos);

  boolean entityDenied(
      ServerPlayer player, net.minecraft.world.entity.Entity target, boolean attack);

  void transfer(ServerPlayer oldOwner, ServerPlayer newOwner, int x, int z);

  void claimDimension(
      ServerPlayer owner,
      net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
      int x,
      int z);
}
