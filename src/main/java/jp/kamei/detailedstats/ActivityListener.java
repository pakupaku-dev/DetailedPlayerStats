package jp.kamei.detailedstats;

import com.google.gson.JsonObject;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.inventory.ItemStack;

public final class ActivityListener implements Listener {
    private final DetailedPlayerStatsPlugin plugin;
    ActivityListener(DetailedPlayerStatsPlugin plugin){this.plugin=plugin;}
    private JsonObject context(){return new JsonObject();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent e){
        JsonObject c=context();c.addProperty("block_data",e.getBlock().getBlockData().getAsString());c.addProperty("experience",e.getExpToDrop());c.addProperty("drop_items",e.isDropItems());
        plugin.record(e.getPlayer(),e.getBlock().getLocation(),"block_break",e.getBlock().getType().getKey().toString(),1,c);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void place(BlockPlaceEvent e){
        if(!e.canBuild())return;
        if(e instanceof BlockMultiPlaceEvent multi)for(var state:multi.getReplacedBlockStates())placed(e,state.getBlock());
        else placed(e,e.getBlockPlaced());
    }
    private void placed(BlockPlaceEvent e,org.bukkit.block.Block block){
        JsonObject c=context();c.addProperty("hand",e.getHand().name());c.addProperty("block_data",block.getBlockData().getAsString());
        plugin.record(e.getPlayer(),block.getLocation(),"block_place",block.getType().getKey().toString(),1,c);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void drops(BlockDropItemEvent e){for(Item item:e.getItems())plugin.record(e.getPlayer(),e.getBlock().getLocation(),"block_drop",item.getItemStack().getType().getKey().toString(),item.getItemStack().getAmount(),context());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void mob(EntityDeathEvent e){
        Player killer=e.getEntity().getKiller();if(killer==null||e.getEntity() instanceof Player)return;
        JsonObject c=context();c.addProperty("entity_uuid",e.getEntity().getUniqueId().toString());c.addProperty("experience",e.getDroppedExp());
        if(e.getEntity().getLastDamageCause()!=null)c.addProperty("cause",e.getEntity().getLastDamageCause().getCause().name());
        plugin.record(killer,e.getEntity().getLocation(),"mob_kill",e.getEntityType().getKey().toString(),1,c);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void death(PlayerDeathEvent e){
        Player p=e.getEntity();JsonObject c=context();c.addProperty("keep_inventory",e.getKeepInventory());c.addProperty("lost_experience",e.getDroppedExp());
        String cause=p.getLastDamageCause()==null?"UNKNOWN":p.getLastDamageCause().getCause().name();
        if(p.getKiller()!=null)c.addProperty("killer_uuid",p.getKiller().getUniqueId().toString());
        plugin.record(p,p.getLocation(),"death",cause,1,c);plugin.snapshot(p);
        if(p.getKiller()!=null)plugin.record(p.getKiller(),p.getLocation(),"player_kill","minecraft:player",1,c);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void damage(EntityDamageEvent e){
        if(e.getFinalDamage()<=0)return;
        JsonObject c=context();c.addProperty("cause",e.getCause().name());c.addProperty("raw_damage",e.getDamage());c.addProperty("target_uuid",e.getEntity().getUniqueId().toString());
        Player attacker=null;
        if(e instanceof EntityDamageByEntityEvent hit){
            Entity source=hit.getDamager();c.addProperty("source",source.getType().getKey().toString());
            if(source instanceof Player p)attacker=p;
            else if(source instanceof Projectile projectile&&projectile.getShooter() instanceof Player p)attacker=p;
        }
        if(attacker!=null)plugin.record(attacker,e.getEntity().getLocation(),"damage_given",e.getEntityType().getKey().toString(),e.getFinalDamage(),c);
        if(e.getEntity() instanceof Player p)plugin.record(p,p.getLocation(),"damage_taken",e.getCause().name(),e.getFinalDamage(),c);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void consume(PlayerItemConsumeEvent e){plugin.record(e.getPlayer(),e.getPlayer().getLocation(),"item_consume",e.getItem().getType().getKey().toString(),1,context());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void fish(PlayerFishEvent e){
        if(e.getState()!=PlayerFishEvent.State.CAUGHT_FISH)return;
        if(e.getCaught() instanceof Item item)plugin.record(e.getPlayer(),e.getHook().getLocation(),"fish_catch",item.getItemStack().getType().getKey().toString(),item.getItemStack().getAmount(),context());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void harvest(PlayerHarvestBlockEvent e){for(ItemStack item:e.getItemsHarvested())plugin.record(e.getPlayer(),e.getHarvestedBlock().getLocation(),"harvest",item.getType().getKey().toString(),item.getAmount(),context());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void bucket(PlayerBucketEmptyEvent e){plugin.record(e.getPlayer(),e.getBlock().getLocation(),"bucket_empty",e.getBucket().getKey().toString(),1,context());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void fill(PlayerBucketFillEvent e){plugin.record(e.getPlayer(),e.getBlock().getLocation(),"bucket_fill",e.getBucket().getKey().toString(),1,context());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void smelt(FurnaceExtractEvent e){plugin.record(e.getPlayer(),e.getBlock().getLocation(),"smelt_collect",e.getItemType().getKey().toString(),e.getItemAmount(),context());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void tame(EntityTameEvent e){if(e.getOwner() instanceof Player p)plugin.record(p,e.getEntity().getLocation(),"tame",e.getEntityType().getKey().toString(),1,context());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void breed(EntityBreedEvent e){if(e.getBreeder() instanceof Player p)plugin.record(p,e.getEntity().getLocation(),"breed",e.getEntityType().getKey().toString(),1,context());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void join(PlayerJoinEvent e){plugin.record(e.getPlayer(),e.getPlayer().getLocation(),"join","session",1,context());plugin.snapshot(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void quit(PlayerQuitEvent e){plugin.record(e.getPlayer(),e.getPlayer().getLocation(),"quit","session",1,context());plugin.snapshot(e.getPlayer());plugin.flushJournal();}
    @EventHandler(priority=EventPriority.MONITOR)
    public void world(PlayerChangedWorldEvent e){JsonObject c=context();c.addProperty("from_world",e.getFrom().getName());plugin.record(e.getPlayer(),e.getPlayer().getLocation(),"world_change",e.getPlayer().getWorld().getName(),1,c);plugin.snapshot(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void statistic(PlayerStatisticIncrementEvent e){
        String stat=e.getStatistic().name();
        // High-frequency movement/time statistics remain in full lifetime snapshots.
        if(stat.endsWith("_ONE_CM")||stat.contains("TIME")||stat.equals("PLAY_ONE_MINUTE"))return;
        long delta=(long)e.getNewValue()-e.getPreviousValue();if(delta<=0)return;
        JsonObject c=context();c.addProperty("statistic",stat);c.addProperty("previous",e.getPreviousValue());c.addProperty("current",e.getNewValue());
        String subject=stat.toLowerCase(java.util.Locale.ROOT);
        if(e.getMaterial()!=null)subject+=":"+e.getMaterial().getKey();
        if(e.getEntityType()!=null)subject+=":"+e.getEntityType().getKey();
        plugin.record(e.getPlayer(),e.getPlayer().getLocation(),"statistic_change",subject,delta,c);
    }
}
