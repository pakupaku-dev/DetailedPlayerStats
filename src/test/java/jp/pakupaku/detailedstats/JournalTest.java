package jp.pakupaku.detailedstats;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class JournalTest {
 @TempDir Path directory;
 @Test void persistsAndRecoversOnlyCompleteRecords() throws Exception {
  JsonObject value=new JsonObject();value.addProperty("record","event");
  try(Journal j=new Journal(directory)){j.append(value);j.rotate();assertEquals(1,j.pending().size());}
  Files.writeString(directory.resolve("00000000-0000-0000-0000-000000000001.open"),value+"\n{partial");
  try(Journal j=new Journal(directory)){
   assertEquals(2,j.pending().size());
   for(Path file:j.pending())for(String line:Files.readAllLines(file))assertTrue(JsonParser.parseString(line).isJsonObject());
  }
 }
 @Test void rotatesBoundedBatchesWithoutDroppingRecords() throws Exception {
  JsonObject value=new JsonObject();value.addProperty("n",1);
  try(Journal j=new Journal(directory)){
   for(int i=0;i<1001;i++)j.append(value);j.rotate();
   assertEquals(3,j.pending().size());int count=0;
   for(Path p:j.pending())count+=Files.readAllLines(p).size();assertEquals(1001,count);
  }
 }
 @Test void importSeparatesDropActionsAndItemTotals(){
  JsonObject data=JsonParser.parseString("{\"stats\":{\"minecraft:custom\":{\"minecraft:drop\":3,\"minecraft:play_time\":100},\"minecraft:dropped\":{\"minecraft:stone\":64},\"minecraft:mined\":{\"minecraft:diamond_ore\":7}}}").getAsJsonObject();
  JsonObject stats=Statistics.vanilla(data);
  assertEquals(3,stats.get("drop_count").getAsInt());assertEquals(64,stats.getAsJsonObject("drop").get("minecraft:stone").getAsInt());
  assertEquals(100,stats.get("play_one_minute").getAsInt());assertEquals(7,stats.getAsJsonObject("mine_block").get("minecraft:diamond_ore").getAsInt());
 }
}
