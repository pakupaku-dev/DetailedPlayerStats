package jp.pakupaku.detailedstats;

import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import java.util.*;

public final class Statistics {
    public static JsonObject capture(Player player) {
        JsonObject out = new JsonObject();
        for (Statistic stat : Statistic.values()) {
            String key = stat.name().toLowerCase(Locale.ROOT);
            if (stat.getType() == Statistic.Type.UNTYPED) { out.addProperty(key, player.getStatistic(stat)); continue; }
            JsonObject counts = new JsonObject();
            if (stat.getType() == Statistic.Type.ENTITY) {
                for (EntityType type : EntityType.values()) {
                    if (type == EntityType.UNKNOWN) continue;
                    try { int count = player.getStatistic(stat, type); if (count > 0) counts.addProperty(type.getKey().toString(), count); }
                    catch (IllegalArgumentException ignored) { }
                }
            } else {
                for (Material type : Material.values()) {
                    if (type.isLegacy() || (stat.getType() == Statistic.Type.BLOCK ? !type.isBlock() : !type.isItem())) continue;
                    try { int count = player.getStatistic(stat, type); if (count > 0) counts.addProperty(type.getKey().toString(), count); }
                    catch (IllegalArgumentException ignored) { }
                }
            }
            out.add(key, counts);
        }
        return out;
    }
    public static JsonObject vanilla(JsonObject root) {
        Map<String,String> categories = Map.of("mined","mine_block","crafted","craft_item","used","use_item","broken","break_item","picked_up","pickup","dropped","drop","killed","kill_entity","killed_by","entity_killed_by");
        JsonObject result = new JsonObject();
        if (!root.has("stats")) return result;
        for (var entry : root.getAsJsonObject("stats").entrySet()) {
            String key = entry.getKey().replace("minecraft:", "");
            if (key.equals("custom")) {
                for (var custom : entry.getValue().getAsJsonObject().entrySet()) {
                    String name = custom.getKey().replace("minecraft:", "");
                    if (name.equals("drop")) name = "drop_count";
                    if (name.equals("play_time")) name = "play_one_minute";
                    result.add(name, custom.getValue());
                }
            } else if (categories.containsKey(key)) result.add(categories.get(key), entry.getValue());
        }
        return result;
    }
}
