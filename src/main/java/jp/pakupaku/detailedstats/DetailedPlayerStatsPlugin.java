package jp.pakupaku.detailedstats;

import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

public final class DetailedPlayerStatsPlugin extends JavaPlugin {
    private Journal journal;
    private Store store;
    private ScheduledExecutorService worker;
    private ExecutorService importer;
    private String server;
    private volatile String lastError="none";
    private volatile long committedAt;
    private volatile int imported;
    private final java.util.Map<UUID,java.time.LocalDate> presenceDays=new java.util.HashMap<>();
    @Override public void onEnable(){
        saveDefaultConfig();
        try{
            server=getConfig().getString("server-id","").trim();
            if(server.isEmpty())server=getServer().getName()+":"+getServer().getPort();
            if(server.length()>96)throw new IllegalArgumentException("server-id too long");
            var mysql=getConfig().getConfigurationSection("mysql");
            if(getConfig().getBoolean("inherit-worldstatesync-mysql",true)){
                Path path=getDataFolder().toPath().getParent().resolve("WorldStateSync/config.yml");
                if(!Files.exists(path))throw new IllegalArgumentException("WorldStateSync config missing; configure own MySQL settings");
                mysql=YamlConfiguration.loadConfiguration(path.toFile()).getConfigurationSection("mysql");
            }
            Path install=getDataFolder().toPath().resolve("installation-ms.txt");
            if(!Files.exists(install))Files.writeString(install,Long.toString(System.currentTimeMillis()),StandardOpenOption.CREATE_NEW);
            store=new Store(mysql,server,Long.parseLong(Files.readString(install).trim()));
            journal=new Journal(getDataFolder().toPath().resolve("outbox"));
            worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"DetailedPlayerStats-MySQL");t.setDaemon(true);return t;});
            worker.scheduleWithFixedDelay(this::drain,0,Math.max(1,getConfig().getInt("flush-seconds",2)),TimeUnit.SECONDS);
            getServer().getPluginManager().registerEvents(new ActivityListener(this),this);
            Objects.requireNonNull(getCommand("detailedstats")).setExecutor((sender,command,label,args)->{
                String action=args.length==0?"status":args[0];
                if(action.equals("flush")){flushJournal();worker.submit(this::drain);sender.sendMessage("Statistics flush queued.");}
                else if(action.equals("import")){importHistory();sender.sendMessage("Idempotent historical import queued.");}
                else {try{sender.sendMessage("DetailedPlayerStats 1.0.1 server="+server+" pending="+journal.pending().size()+" imported="+imported+" lastCommit="+committedAt+" error="+lastError);}catch(Exception e){sender.sendMessage("Journal unavailable");}}
                return true;
            });
            long period=Math.max(10,getConfig().getInt("snapshot-seconds",30))*20L;
            getServer().getScheduler().runTaskTimer(this,()->{
                java.time.LocalDate today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo"));
                for(Player player:getServer().getOnlinePlayers()){
                    snapshot(player);
                    if(!today.equals(presenceDays.put(player.getUniqueId(),today)))record(player,player.getLocation(),"reward_presence","daily",1,new JsonObject());
                }
            },20,period);
            if(getConfig().getBoolean("import-on-start",true))importHistory();
            Path core=getDataFolder().toPath().getParent().resolve("CoreProtect/database.db");
            if(getConfig().getBoolean("import-coreprotect",true)&&Files.exists(core)){
                CoreProtectImport migration=new CoreProtectImport(store,core,getConfig().getString("coreprotect-prefix","co_"));
                importer=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"DetailedPlayerStats-Import");t.setDaemon(true);return t;});
                importer.submit(()->{
                    while(!Thread.currentThread().isInterrupted()){
                        try{store.initialize();if(migration.page()){getLogger().info("CoreProtect historical import complete");return;}}
                        catch(Exception e){getLogger().log(Level.WARNING,"CoreProtect import paused; checkpoint retained",e);try{Thread.sleep(5000);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}
                    }
                });
            }
            getLogger().info("Recording detailed statistics for "+server+"; local journal enabled");
        }catch(Exception e){getLogger().log(Level.SEVERE,"Statistics startup failed",e);getServer().getPluginManager().disablePlugin(this);}
    }
    public void record(Player player,Location location,String kind,String subject,double amount,JsonObject context){
        if(!Double.isFinite(amount)||amount<=0)return;
        JsonObject r=base(player.getUniqueId().toString(),player.getName(),System.currentTimeMillis());
        r.addProperty("record","event");r.addProperty("id",UUID.randomUUID().toString());r.addProperty("kind",kind);r.addProperty("subject",subject);r.addProperty("amount",amount);
        r.addProperty("world_uuid",location.getWorld().getUID().toString());r.addProperty("world",location.getWorld().getName());
        r.addProperty("x",location.getBlockX());r.addProperty("y",location.getBlockY());r.addProperty("z",location.getBlockZ());
        r.addProperty("biome",location.getBlock().getBiome().getKey().toString());r.addProperty("mode",player.getGameMode().name());
        JsonObject tool=new JsonObject();ItemStack item=player.getInventory().getItemInMainHand();
        tool.addProperty("type",item.getType().getKey().toString());tool.addProperty("amount",item.getAmount());
        JsonObject enchants=new JsonObject();item.getEnchantments().forEach((e,n)->enchants.addProperty(e.getKey().toString(),n));tool.add("enchantments",enchants);
        if(item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damage)tool.addProperty("damage",damage.getDamage());
        r.add("tool",tool);r.add("context",context);append(r);
    }
    public void snapshot(Player p){
        try{JsonObject r=base(p.getUniqueId().toString(),p.getName(),System.currentTimeMillis());r.addProperty("record","snapshot");r.addProperty("source","bukkit");r.add("statistics",Statistics.capture(p));append(r);}
        catch(Exception e){getLogger().log(Level.WARNING,"Could not capture statistics",e);}
    }
    private JsonObject base(String uuid,String name,long time){JsonObject r=new JsonObject();r.addProperty("player",uuid);r.addProperty("name",name);r.addProperty("server",server);r.addProperty("time",time);return r;}
    private void append(JsonObject r){try{journal.append(r);}catch(Exception e){lastError="local journal write failed";getLogger().log(Level.SEVERE,lastError,e);}}
    public void flushJournal(){try{journal.rotate();}catch(Exception e){getLogger().log(Level.SEVERE,"Journal flush failed",e);}}
    private void drain(){
        try{journal.rotate();store.initialize();for(Path file:journal.pending())store.replay(file);store.heartbeat();committedAt=System.currentTimeMillis();lastError="none";}
        catch(Exception e){if(lastError.equals("none"))getLogger().log(Level.SEVERE,"MySQL unavailable; retaining local journal for retry",e);lastError=e.getClass().getSimpleName();}
    }
    private void importHistory(){
        // Paper stores global statistics in the primary world's players/stats (26.2), or stats (older versions).
        Path serverRoot=getDataFolder().toPath().toAbsolutePath().getParent().getParent().normalize();
        Set<Path> directories=new LinkedHashSet<>();
        for(String configured:getConfig().getStringList("statistics-directories")){
            Path candidate=serverRoot.resolve(configured).normalize();
            if(!candidate.startsWith(serverRoot))throw new IllegalArgumentException("Statistics directory must be inside server directory");
            directories.add(candidate);
        }
        for(var world:getServer().getWorlds()){
            Path root=world.getWorldFolder().toPath().toAbsolutePath().normalize();
            for(int depth=0;root!=null&&root.startsWith(serverRoot)&&depth<6;depth++,root=root.getParent()){
                directories.add(root.resolve("players/stats"));directories.add(root.resolve("stats"));
            }
        }
        Map<UUID,String> names=new HashMap<>();for(var p:getServer().getOfflinePlayers())if(p.getName()!=null)names.put(p.getUniqueId(),p.getName());
        worker.submit(()->{
            try{
                Map<String,Path> newest=new HashMap<>();
                for(Path dir:directories){
                    if(!Files.isDirectory(dir))continue;
                    try(var files=Files.list(dir)){for(Path file:files.filter(p->p.toString().endsWith(".json")).toList()){
                        String key=file.getFileName().toString();Path prior=newest.get(key);
                        if(prior==null||Files.getLastModifiedTime(file).compareTo(Files.getLastModifiedTime(prior))>0)newest.put(key,file);
                    }}
                }
                for(Path file:newest.values())try{
                    UUID player=UUID.fromString(file.getFileName().toString().replace(".json",""));
                    JsonObject r=base(player.toString(),names.getOrDefault(player,""),Files.getLastModifiedTime(file).toMillis());
                    r.addProperty("record","snapshot");r.addProperty("source","vanilla_import");r.add("statistics",Statistics.vanilla(JsonParser.parseString(Files.readString(file)).getAsJsonObject()));
                    journal.append(r);imported++;
                }catch(Exception e){getLogger().log(Level.WARNING,"Could not import statistics file "+file.getFileName(),e);}
                drain();getLogger().info("Historical import complete: "+newest.size()+" files; absolute snapshots, no additive reimport");
            }catch(Exception e){getLogger().log(Level.SEVERE,"Historical import failed",e);}
        });
    }
    @Override public void onDisable(){
        getServer().getScheduler().cancelTasks(this);
        if(journal!=null){getServer().getOnlinePlayers().forEach(this::snapshot);flushJournal();}
        if(worker!=null){worker.submit(this::drain);worker.shutdown();try{if(!worker.awaitTermination(25,TimeUnit.SECONDS))worker.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        if(importer!=null){importer.shutdownNow();try{importer.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        if(store!=null)store.close();
    }
}
