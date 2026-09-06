package dev.cuboidplots.integration.opac;

import dev.cuboidplots.core.ParentClaims;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import xaero.pac.common.server.api.OpenPACServerAPI;

public final class OpacClaimsAdapter implements ParentClaims {
  private final MinecraftServer server;

  public OpacClaimsAdapter(MinecraftServer server) {
    this.server = server;
  }

  public String integration() {
    return "openpartiesandclaims";
  }

  public String ownerAt(String dimension, int x, int z) {
    var claim =
        OpenPACServerAPI.get(server)
            .getServerClaimsManager()
            .get(new ResourceLocation(dimension), x, z);
    return claim == null ? null : claim.getPlayerId().toString();
  }

  public boolean canManage(UUID player, String owner) {
    // OPAC claims belong to a player, not their current party. Party access does not imply
    // ownership.
    return player.toString().equals(owner);
  }
}
