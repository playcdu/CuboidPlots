package dev.cuboidplots.mixin.integration;

import dev.cuboidplots.platform.AddonRuntime;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.pac.common.server.claims.ServerClaimsManager;

@Mixin(value = ServerClaimsManager.class, remap = false)
public abstract class OpacOwnershipMixin {
  @Inject(method = "claim", at = @At("HEAD"), remap = false)
  private void cuboidplots$transfer(
      ResourceLocation dimension,
      java.util.UUID owner,
      int subConfig,
      int x,
      int z,
      boolean force,
      org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<?> info) {
    var existing = ((ServerClaimsManager) (Object) this).get(dimension, x, z);
    if (existing != null && !existing.getPlayerId().equals(owner))
      AddonRuntime.ownershipChanged(dimension.toString(), x, z);
  }

  @Inject(method = "unclaim", at = @At("HEAD"), remap = false)
  private void cuboidplots$unclaim(ResourceLocation dimension, int x, int z, CallbackInfo info) {
    AddonRuntime.ownershipChanged(dimension.toString(), x, z);
  }
}
