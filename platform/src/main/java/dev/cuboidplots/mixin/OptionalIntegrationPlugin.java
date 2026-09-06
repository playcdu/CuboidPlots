package dev.cuboidplots.mixin;

import java.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

/** Optional parent classes are never linked when their mod is absent. No reflective API calls. */
public final class OptionalIntegrationPlugin implements IMixinConfigPlugin {
  public void onLoad(String mixinPackage) {}

  public String getRefMapperConfig() {
    return null;
  }

  public boolean shouldApplyMixin(String target, String mixin) {
    if (mixin.contains(".integration."))
      return getClass().getClassLoader().getResource(target.replace('.', '/') + ".class") != null;
    return true;
  }

  public void acceptTargets(Set<String> mine, Set<String> others) {}

  public List<String> getMixins() {
    return null;
  }

  public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

  public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
