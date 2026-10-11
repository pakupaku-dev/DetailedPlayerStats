package jp.pakupaku.detailedstats;
import com.google.gson.*;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StoreTest {
 @TempDir Path directory;
 @Test void transactionalReplayAndImportAreIdempotent() throws Exception {
  Assumptions.assumeTrue(System.getenv("DPS_TEST_DB")!=null,"Isolated MySQL test database not configured");
  MemoryConfiguration cfg=new MemoryConfiguration();
  cfg.set("host",System.getenv("DPS_TEST_HOST"));cfg.set("port",Integer.parseInt(System.getenv("DPS_TEST_PORT")));cfg.set("database",System.getenv("DPS_TEST_DB"));cfg.set("username",System.getenv("DPS_TEST_USER"));cfg.set("password",System.getenv("DPS_TEST_PASSWORD"));
  String url="jdbc:mysql://"+cfg.getString("host")+":"+cfg.getInt("port")+"/"+cfg.getString("database")+"?allowPublicKeyRetrieval=true&useSSL=false";
  try(Store store=new Store(cfg,"test:1",1);Connection c=DriverManager.getConnection(url,cfg.getString("username"),cfg.getString("password"))){
   store.initialize();
   JsonObject event=new JsonObject();
   for(String key:new String[]{"id","player","world_uuid"})event.addProperty(key,UUID.randomUUID().toString());
   for(String key:new String[]{"name","world","biome","mode"})event.addProperty(key,"test");
   event.addProperty("server","test:1");event.addProperty("record","event");event.addProperty("kind","block_break");event.addProperty("subject","minecraft:stone");event.addProperty("amount",1);event.addProperty("time",1000000);
   for(String key:new String[]{"x","y","z"})event.addProperty(key,1);
   event.add("tool",new JsonObject());event.add("context",new JsonObject());
   String batch=UUID.randomUUID().toString();Path file=directory.resolve(batch+".jsonl");
   Files.writeString(file,event+"\n");store.replay(file);
   Files.writeString(file,event+"\n");store.replay(file);
   file=directory.resolve(UUID.randomUUID()+".jsonl");Files.writeString(file,event+"\n");store.replay(file);
   try(var s=c.createStatement();var r=s.executeQuery("SELECT SUM(amount),SUM(event_count) FROM detailed_stats_daily")){assertTrue(r.next());assertEquals(1,r.getInt(1));assertEquals(1,r.getInt(2));}
   try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM reward_quest_queue")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
   JsonObject snapshot=new JsonObject();snapshot.addProperty("record","snapshot");snapshot.addProperty("player",event.get("player").getAsString());snapshot.addProperty("name","test");snapshot.addProperty("server","test:1");snapshot.addProperty("source","vanilla_import");snapshot.addProperty("time",2000);snapshot.add("statistics",JsonParser.parseString("{\"mob_kills\":50}"));
   file=directory.resolve(UUID.randomUUID()+".jsonl");Files.writeString(file,snapshot+"\n");store.replay(file);
   snapshot.addProperty("time",1000);snapshot.add("statistics",JsonParser.parseString("{\"mob_kills\":20}"));
   file=directory.resolve(UUID.randomUUID()+".jsonl");Files.writeString(file,snapshot+"\n");store.replay(file);
   try(var s=c.createStatement();var r=s.executeQuery("SELECT JSON_EXTRACT(statistics,'$.mob_kills') FROM detailed_stats_snapshots")){assertTrue(r.next());assertEquals(50,r.getInt(1));}
   event.addProperty("id",UUID.randomUUID().toString());file=directory.resolve(UUID.randomUUID()+".jsonl");Files.writeString(file,event+"\n{broken}\n");
   assertThrows(Exception.class,()->store.replay(directory.resolve(fileName(directory))));
   assertTrue(Files.exists(file));
   try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM detailed_stats_events")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
   Path legacy=directory.resolve("legacy.db");Class.forName("org.sqlite.JDBC");
   try(Connection old=DriverManager.getConnection("jdbc:sqlite:"+legacy);Statement s=old.createStatement()){
    s.execute("CREATE TABLE co_user(id INTEGER,user TEXT,uuid TEXT)");s.execute("CREATE TABLE co_world(id INTEGER,world TEXT)");s.execute("CREATE TABLE co_material_map(id INTEGER,material TEXT)");
    s.execute("CREATE TABLE co_block(time INTEGER,user INTEGER,wid INTEGER,x INTEGER,y INTEGER,z INTEGER,type INTEGER,data INTEGER,action INTEGER,rolled_back INTEGER)");
    s.execute("INSERT INTO co_user VALUES(1,'test','11111111-1111-1111-1111-111111111111'),(2,'#water',NULL)");s.execute("INSERT INTO co_world VALUES(1,'world')");s.execute("INSERT INTO co_material_map VALUES(1,'minecraft:stone')");
    s.execute("INSERT INTO co_block VALUES(0,1,1,10,20,30,1,0,0,0),(0,2,1,1,2,3,1,0,1,0)");
   }
   CoreProtectImport migration=new CoreProtectImport(store,legacy,"co_");assertTrue(migration.page());assertTrue(migration.page());
   try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*),MIN(x),MIN(material) FROM detailed_stats_coreprotect_blocks")){assertTrue(r.next());assertEquals(1,r.getInt(1));assertEquals(10,r.getInt(2));assertEquals("minecraft:stone",r.getString(3));}
  }
 }
 private String fileName(Path path) throws Exception {try(var files=Files.list(path)){return files.findFirst().orElseThrow().getFileName().toString();}}
}
