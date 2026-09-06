package dev.cuboidplots.platform;

import dev.cuboidplots.core.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/**
 * World cleanup and shulker recovery, isolated from region geometry and quota logic.
 * A prepared NBT journal precedes destructive work. It records source inventories,
 * item entity UUIDs, tracked blocks and the complete return payload. Sources are
 * reconciled before a forced world save; only then is the batch made withdrawable.
 */
public final class ItemRecovery implements RegionLifecycle {
    private final MinecraftServer server;
    private final Path directory,placementsFile;
    private CompoundTag placements;
    private boolean recovering;
    public ItemRecovery(MinecraftServer server,Path directory) throws IOException {
        this.server=server;this.directory=directory;this.placementsFile=directory.resolve("placements.nbt");
        Files.createDirectories(directory);
        placements=Files.exists(placementsFile)?NbtIo.readCompressed(placementsFile.toFile()):new CompoundTag();
    }
    public void resumePrepared() throws IOException {
        try(var files=Files.list(directory)){
            for(Path path:(Iterable<Path>)files.filter(p->p.getFileName().toString().startsWith("batch-"))::iterator){
                CompoundTag batch=NbtIo.readCompressed(path.toFile());
                if(batch.getString("State").equals("PREPARED"))complete(path,batch);
            }
        }
    }
    /** Called after a block item operation completes, including ordinary parent-owner placement. */
    public void recordPlacement(ServerPlayer player,Collection<BlockPos> positions){
        if(recovering || AddonRuntime.regions==null)return;
        boolean dirty=false;
        for(BlockPos pos:positions){
            Region region=AddonRuntime.regions.regionAt(player.level().dimension().location().toString(),AddonRuntime.position(pos));
            if(region==null||region.suspended)continue;
            BlockState state=player.level().getBlockState(pos);if(state.isAir())continue;
            CompoundTag entries=placements.getCompound(region.id.toString());
            entries.putString(Long.toString(pos.asLong()),BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            placements.put(region.id.toString(),entries);dirty=true;
        }
        if(dirty)try{write(placementsFile,placements);}catch(IOException ex){fatal("Placement history could not be persisted",ex);}
    }
    public void forgetBroken(ServerPlayer player,Collection<BlockPos> positions){
        if(recovering)return;
        boolean dirty=false;
        for(BlockPos pos:positions){
            Region region=AddonRuntime.regions==null?null:AddonRuntime.regions.regionAt(player.level().dimension().location().toString(),AddonRuntime.position(pos));
            if(region==null||!player.level().getBlockState(pos).isAir())continue;
            CompoundTag entries=placements.getCompound(region.id.toString());
            if(entries.contains(Long.toString(pos.asLong()))){entries.remove(Long.toString(pos.asLong()));dirty=true;}
        }
        if(dirty)try{write(placementsFile,placements);}catch(IOException ex){fatal("Placement history could not be persisted",ex);}
    }
    public void beforeOwnershipRemoved(Region region,Removal reason,boolean current) throws IOException {
        if(region.suspended)return; // Its original removal already reserved the returns.
        if(!current) {
            // Never harvest a new owner's land to satisfy an old owner's recovery claim.
            throw new IOException("Recovery needs review: parent binding already changed for "+region.name+". No new-owner blocks were touched.");
        }
        ServerLevel level=level(region.dimension);
        CompoundTag batch=new CompoundTag();UUID id=UUID.randomUUID();
        batch.putUUID("Id",id);batch.putUUID("Recipient",region.assignedOwner);batch.putUUID("RegionId",region.id);
        batch.putString("Region",region.name);batch.putString("Dimension",region.dimension);batch.putString("Reason",reason.name());batch.putString("State","PREPARED");batch.putInt("Delivered",0);
        ListTag sources=new ListTag(),blocks=new ListTag();List<ItemStack> returns=new ArrayList<>();
        CompoundTag tracked=placements.getCompound(region.id.toString());
        Set<BlockPos> remove=new HashSet<>();
        for(String key:tracked.getAllKeys()){
            BlockPos pos=BlockPos.of(Long.parseLong(key));if(!region.bounds.contains(AddonRuntime.position(pos)))continue;
            BlockState state=level.getBlockState(pos);
            if(!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(tracked.getString(key)))continue;
            // Unknown modded loot functions may have side effects. Require vanilla tracked blocks here.
            if(!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft"))throw new IOException("Recovery cannot safely evaluate modded block loot at "+pos);
            remove.add(pos);
            CompoundTag block=new CompoundTag();block.putLong("Pos",pos.asLong());block.putString("Block",tracked.getString(key));blocks.add(block);
            returns.addAll(Block.getDrops(state,level,pos,level.getBlockEntity(pos),null,new ItemStack(Items.DIAMOND_PICKAXE)));
        }
        // Visit loaded chunk block-entity indexes, not every air voxel in the cuboid.
        for(int x=region.bounds.min.x>>4;x<=(region.bounds.max.x>>4);x++)for(int z=region.bounds.min.z>>4;z<=(region.bounds.max.z>>4);z++){
            var chunk=level.getChunk(x,z);
            for(BlockPos pos:new HashSet<>(chunk.getBlockEntitiesPos())){
                BlockEntity entity=chunk.getBlockEntity(pos);
                if(!region.bounds.contains(AddonRuntime.position(pos))||!(entity instanceof Container container))continue;
                if(!BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals("minecraft"))throw new IOException("Modded inventory recovery requires an explicit adapter at "+pos);
                if(entity instanceof ChestBlockEntity){
                    BlockState state=level.getBlockState(pos);
                    if(state.getValue(ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE
                        && !region.bounds.contains(AddonRuntime.position(pos.relative(ChestBlock.getConnectedDirection(state)))))throw new IOException("Recovery refused: double chest crosses cuboid boundary at "+pos);
                }
                CompoundTag source=new CompoundTag();source.putString("Kind","container");source.putLong("Pos",pos.asLong());source.putString("Block",BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
                ListTag items=inventory(container);source.put("Items",items);sources.add(source);
                // A removed shulker's block loot already carries its stored Items NBT. Do not duplicate it.
                if(!(remove.contains(pos)&&entity instanceof ShulkerBoxBlockEntity))for(int i=0;i<container.getContainerSize();i++)if(!container.getItem(i).isEmpty())returns.add(container.getItem(i).copy());
            }
        }
        AABB box=new AABB(region.bounds.min.x,region.bounds.min.y,region.bounds.min.z,(double)region.bounds.max.x+1,(double)region.bounds.max.y+1,(double)region.bounds.max.z+1);
        for(ItemEntity entity:level.getEntitiesOfClass(ItemEntity.class,box,e->region.bounds.contains(AddonRuntime.position(e.blockPosition())))){
            CompoundTag source=new CompoundTag();source.putString("Kind","item");source.putUUID("Entity",entity.getUUID());source.putLong("Pos",entity.blockPosition().asLong());source.put("Item",entity.getItem().save(new CompoundTag()));sources.add(source);returns.add(entity.getItem().copy());
        }
        if(sources.size()>10000 || returns.size()>100000)throw new IOException("Recovery safety cap exceeded; split or empty this cuboid first");
        batch.put("Sources",sources);batch.put("Blocks",blocks);batch.put("Boxes",pack(returns,region.name,id));
        Path path=directory.resolve("batch-"+id+".nbt");write(path,batch);complete(path,batch);
    }
    private void complete(Path path,CompoundTag batch)throws IOException{
        recovering=true;
        try{
            ServerLevel level=level(batch.getString("Dimension"));ListTag sources=batch.getList("Sources",Tag.TAG_COMPOUND),blocks=batch.getList("Blocks",Tag.TAG_COMPOUND);
            // Validate every source before clearing any source. Interrupted empty sources are idempotent.
            for(int i=0;i<sources.size();i++){
                CompoundTag source=sources.getCompound(i);BlockPos pos=BlockPos.of(source.getLong("Pos"));level.getChunkAt(pos);
                if(source.getString("Kind").equals("container")){
                    BlockEntity entity=level.getBlockEntity(pos);
                    if(entity instanceof Container container){if(!container.isEmpty()&&!inventory(container).equals(source.getList("Items",Tag.TAG_COMPOUND)))throw new IOException("Recovery journal conflict at "+pos);}
                    else if(!level.getBlockState(pos).isAir())throw new IOException("Recovery source block changed at "+pos);
                }else{Entity entity=level.getEntity(source.getUUID("Entity"));if(entity instanceof ItemEntity item&&!item.getItem().save(new CompoundTag()).equals(source.getCompound("Item")))throw new IOException("Recovery item entity changed");}
            }
            for(int i=0;i<blocks.size();i++){
                CompoundTag block=blocks.getCompound(i);BlockState state=level.getBlockState(BlockPos.of(block.getLong("Pos")));
                if(!state.isAir()&&!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(block.getString("Block")))throw new IOException("Tracked block changed during recovery");
            }
            for(int i=0;i<sources.size();i++){
                CompoundTag source=sources.getCompound(i);
                if(source.getString("Kind").equals("container")){BlockEntity entity=level.getBlockEntity(BlockPos.of(source.getLong("Pos")));if(entity instanceof Container container){container.clearContent();entity.setChanged();}}
                else{Entity entity=level.getEntity(source.getUUID("Entity"));if(entity!=null)entity.discard();}
            }
            for(int i=0;i<blocks.size();i++)level.setBlock(BlockPos.of(blocks.getCompound(i).getLong("Pos")),Blocks.AIR.defaultBlockState(),2|16);
            // Freeze the recovery transaction until world state is durable. No inventory delivery yet.
            server.saveEverything(false,true,true);
            placements.remove(batch.getUUID("RegionId").toString());write(placementsFile,placements);
            batch.putString("State","READY");write(path,batch);
            System.out.println("[CuboidPlots] Reserved "+batch.getList("Boxes",Tag.TAG_COMPOUND).size()+" return boxes for "+batch.getUUID("Recipient"));
        }finally{recovering=false;}
    }
    public int available(UUID owner)throws IOException{
        int count=0;for(Path path:batches()){CompoundTag batch=NbtIo.readCompressed(path.toFile());if(batch.getUUID("Recipient").equals(owner)&&batch.getString("State").equals("READY"))count+=batch.getList("Boxes",Tag.TAG_COMPOUND).size()-batch.getInt("Delivered");}return count;
    }
    public int collect(ServerPlayer player)throws IOException{
        int delivered=0;
        for(Path path:batches()){
            CompoundTag batch=NbtIo.readCompressed(path.toFile());if(!batch.getUUID("Recipient").equals(player.getUUID())||!batch.getString("State").equals("READY"))continue;
            ListTag boxes=batch.getList("Boxes",Tag.TAG_COMPOUND);
            for(int i=batch.getInt("Delivered");i<boxes.size();i++){
                ItemStack box=ItemStack.of(boxes.getCompound(i));String receipt=box.getOrCreateTag().getString("CuboidReturnReceipt");
                boolean exists=false;for(ItemStack held:player.getInventory().items)if(held.hasTag()&&receipt.equals(held.getTag().getString("CuboidReturnReceipt")))exists=true;
                if(!exists){int slot=player.getInventory().getFreeSlot();if(slot<0)return delivered;
                    batch.putInt("Delivering",i);write(path,batch);
                    player.getInventory().setItem(slot,box);player.getInventory().setChanged();
                    ((dev.cuboidplots.mixin.PlayerSaveAccess)server.getPlayerList()).cuboidplots$savePlayer(player);delivered++;
                }
                batch.putInt("Delivered",i+1);batch.remove("Delivering");
                try{write(path,batch);}catch(IOException ex){fatal("Delivery receipt could not be committed",ex);throw ex;}
            }
        }
        player.containerMenu.broadcastChanges();return delivered;
    }
    private List<Path>batches()throws IOException{try(var files=Files.list(directory)){return files.filter(p->p.getFileName().toString().startsWith("batch-")).sorted().collect(java.util.stream.Collectors.toList());}}
    private ServerLevel level(String dimension)throws IOException{ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(dimension)));if(level==null)throw new IOException("Recovery dimension unavailable: "+dimension);return level;}
    private static ListTag inventory(Container container){ListTag items=new ListTag();for(int slot=0;slot<container.getContainerSize();slot++){ItemStack item=container.getItem(slot);if(item.isEmpty())continue;CompoundTag value=item.save(new CompoundTag());value.putInt("SourceSlot",slot);items.add(value);}return items;}
    static ListTag pack(List<ItemStack> items,String region,UUID batch){
        ListTag result=new ListTag(),contents=new ListTag();int slot=0;
        for(ItemStack original:items){if(original.isEmpty())continue;
            // Vanilla forbids nesting shulkers. Existing shulkers are returned intact as their own box.
            if(original.getItem() instanceof BlockItem blockItem&&blockItem.getBlock() instanceof ShulkerBoxBlock){ItemStack box=original.copy();box.getOrCreateTag().putString("CuboidReturnReceipt",batch+":"+result.size());result.add(box.save(new CompoundTag()));continue;}
            ItemStack remaining=original.copy();while(!remaining.isEmpty()){
                ItemStack part=remaining.split(Math.min(remaining.getMaxStackSize(),remaining.getCount()));CompoundTag value=part.save(new CompoundTag());value.putByte("Slot",(byte)slot++);contents.add(value);
                if(slot==27){result.add(shulker(contents,region,batch,result.size()));contents=new ListTag();slot=0;}
            }
        }
        if(!contents.isEmpty())result.add(shulker(contents,region,batch,result.size()));return result;
    }
    private static CompoundTag shulker(ListTag contents,String region,UUID batch,int index){ItemStack box=new ItemStack(Items.PURPLE_SHULKER_BOX);CompoundTag data=new CompoundTag();data.put("Items",contents);box.getOrCreateTag().put("BlockEntityTag",data);box.getOrCreateTag().putString("CuboidReturnReceipt",batch+":"+index);box.setHoverName(net.minecraft.network.chat.Component.literal("Returned: "+region+" / "+(index+1)));return box.save(new CompoundTag());}
    private static void write(Path path,CompoundTag data)throws IOException{
        Path temp=path.resolveSibling(path.getFileName()+".tmp");NbtIo.writeCompressed(data,temp.toFile());
        try(FileChannel channel=FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}
        Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    private void fatal(String message,Exception error){System.err.println("[CuboidPlots] "+message+"; stopping to preserve recovery consistency: "+error);server.halt(false);}
}
