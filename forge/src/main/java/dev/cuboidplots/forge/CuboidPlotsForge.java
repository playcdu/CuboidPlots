package dev.cuboidplots.forge;

import dev.cuboidplots.platform.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod("cuboidplots")
public final class CuboidPlotsForge {
  public CuboidPlotsForge() {
    MinecraftForge.EVENT_BUS.addListener(this::commands);
    MinecraftForge.EVENT_BUS.addListener(this::started);
    MinecraftForge.EVENT_BUS.addListener(this::stopped);
    MinecraftForge.EVENT_BUS.addListener(this::tick);
  }

  private void commands(RegisterCommandsEvent event) {
    AddonCommands.register(event.getDispatcher());
  }

  private void started(ServerStartedEvent event) {
    AddonRuntime.start(event.getServer(), id -> ModList.get().isLoaded(id));
  }

  private void stopped(ServerStoppedEvent event) {
    AddonRuntime.stop();
  }

  // Forge 40's tick event has no server accessor. Its lifecycle hook supplies the active server.
  private void tick(TickEvent.ServerTickEvent event) {
    if (event.phase == TickEvent.Phase.END)
      SelectionTool.tick(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
  }
}
