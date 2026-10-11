package jp.pakupaku.detailedstats;

import com.google.gson.*;
import com.zaxxer.hikari.*;
import org.bukkit.configuration.ConfigurationSection;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;

public final class Store implements AutoCloseable {
    private final HikariDataSource pool;
    private final String server;
    private final long installed;
    private boolean initialized;
    public Connection connection() throws SQLException {return pool.getConnection();}
    public String serverId(){return server;}
    public long installedAt(){return installed;}
    public Store(ConfigurationSection mysql, String server, long installed) {
        this.server=server; this.installed=installed;
        String db=mysql.getString("database", "minecraft");
        if (!db.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Invalid database name");
        HikariConfig h=new HikariConfig();
        h.setJdbcUrl("jdbc:mysql://"+mysql.getString("host")+":"+mysql.getInt("port",3306)+"/"+db+"?useSSL="+mysql.getBoolean("use-ssl",false)+"&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&rewriteBatchedStatements=true");
        h.setUsername(mysql.getString("username"));h.setPassword(mysql.getString("password"));
        h.setMaximumPoolSize(2);h.setMinimumIdle(0);h.setConnectionTimeout(5000);h.setInitializationFailTimeout(-1);
        pool=new HikariDataSource(h);
    }
    public synchronized void initialize() throws Exception {
        if (initialized) return;
        try(Connection c=pool.getConnection(); Statement s=c.createStatement()) {
            String ddl=new String(getClass().getResourceAsStream("/schema.sql").readAllBytes(),StandardCharsets.UTF_8);
            for(String sql:ddl.split(";")) if(!sql.isBlank()) s.execute(sql);
            try(PreparedStatement p=c.prepareStatement("INSERT INTO detailed_stats_servers VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE heartbeat_ms=VALUES(heartbeat_ms),version=VALUES(version)")) {
                p.setString(1,server);p.setLong(2,installed);p.setLong(3,System.currentTimeMillis());p.setString(4,"1.0.1");p.executeUpdate();
            }
            // Frozen legacy placed counts, never added again on a restart.
            try(PreparedStatement p=c.prepareStatement("INSERT IGNORE INTO detailed_stats_legacy_placements SELECT s.player_uuid,s.server_id,JSON_EXTRACT(s.snapshot,'$.statistics.placed_blocks'),UNIX_TIMESTAMP(s.updated_at)*1000 FROM world_state_sync_portal_snapshot s WHERE s.server_id=? AND UNIX_TIMESTAMP(s.updated_at)*1000<=? AND JSON_CONTAINS_PATH(s.snapshot,'one','$.statistics.placed_blocks') AND NOT EXISTS (SELECT 1 FROM world_state_sync_portal_snapshot n WHERE n.player_uuid=s.player_uuid AND n.server_id=s.server_id AND n.updated_at>s.updated_at AND UNIX_TIMESTAMP(n.updated_at)*1000<=?)")) {
                p.setString(1,server);p.setLong(2,installed);p.setLong(3,installed);p.executeUpdate();
            } catch(SQLException e) { if(e.getErrorCode()!=1146) throw e; }
        }
        initialized=true;
    }
    public void heartbeat() throws SQLException {
        try(Connection c=pool.getConnection();PreparedStatement p=c.prepareStatement("UPDATE detailed_stats_servers SET heartbeat_ms=? WHERE server_id=?")) {
            p.setLong(1,System.currentTimeMillis());p.setString(2,server);p.executeUpdate();
        }
    }
    public void replay(Path path) throws Exception {
        String batch=path.getFileName().toString().replace(".jsonl", "");
        try(Connection c=pool.getConnection()) {
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement("INSERT IGNORE INTO detailed_stats_batches VALUES(?,?)")) {
                    p.setString(1,batch);p.setLong(2,System.currentTimeMillis());
                    if(p.executeUpdate()==0) { c.rollback();Files.delete(path);return; }
                }
                try(var lines=Files.lines(path,StandardCharsets.UTF_8)) {
                    for(String line:lines.toList()) {
                        JsonObject r=JsonParser.parseString(line).getAsJsonObject();
                        if(r.get("record").getAsString().equals("snapshot")) snapshot(c,r); else event(c,r);
                    }
                }
                c.commit(); Files.delete(path);
            } catch(Exception e) { c.rollback();throw e; }
        }
    }
    private void snapshot(Connection c,JsonObject r) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("INSERT INTO detailed_stats_snapshots VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE player_name=IF(VALUES(observed_ms)>=observed_ms,VALUES(player_name),player_name),statistics=IF(VALUES(observed_ms)>=observed_ms,VALUES(statistics),statistics),source=IF(VALUES(observed_ms)>=observed_ms,VALUES(source),source),observed_ms=GREATEST(observed_ms,VALUES(observed_ms))")) {
            p.setString(1,str(r,"player"));p.setString(2,str(r,"server"));p.setString(3,str(r,"name"));p.setString(4,r.get("statistics").toString());p.setLong(5,r.get("time").getAsLong());p.setString(6,str(r,"source"));p.executeUpdate();
        }
    }
    private void event(Connection c,JsonObject r) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("INSERT INTO detailed_stats_events VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            int i=1;
            for(String key:new String[]{"id","player","name","server"}) p.setString(i++,str(r,key));
            p.setLong(i++,r.get("time").getAsLong());p.setString(i++,str(r,"kind"));p.setString(i++,str(r,"subject"));p.setBigDecimal(i++,r.get("amount").getAsBigDecimal());
            p.setString(i++,str(r,"world_uuid"));p.setString(i++,str(r,"world"));
            for(String key:new String[]{"x","y","z"})p.setInt(i++,r.get(key).getAsInt());
            p.setString(i++,str(r,"biome"));p.setString(i++,str(r,"mode"));p.setString(i++,r.get("tool").toString());p.setString(i,r.get("context").toString());
            try { p.executeUpdate(); } catch(SQLException e) { if(e.getErrorCode()==1062)return;throw e; }
        }
        String kind=str(r,"kind");
        String queue=java.util.Set.of("join","reward_presence").contains(kind)?"reward_login_queue":
            java.util.Set.of("block_break","block_place","mob_kill","statistic_change","fish_catch","smelt_collect","harvest","breed","tame","item_consume").contains(kind)?"reward_quest_queue":null;
        if(queue!=null)try(PreparedStatement p=c.prepareStatement("INSERT INTO "+queue+"(event_id) VALUES(?)")){p.setString(1,str(r,"id"));p.executeUpdate();}
        try(PreparedStatement p=c.prepareStatement("INSERT INTO detailed_stats_daily VALUES(?,?,?,?,?,?,?,?,1) ON DUPLICATE KEY UPDATE amount=amount+VALUES(amount),event_count=event_count+1,world_name=VALUES(world_name)")) {
            p.setString(1,str(r,"player"));p.setString(2,str(r,"server"));
            p.setDate(3,java.sql.Date.valueOf(Instant.ofEpochMilli(r.get("time").getAsLong()).atZone(ZoneOffset.UTC).toLocalDate()));
            p.setString(4,str(r,"world_uuid"));p.setString(5,str(r,"world"));p.setString(6,str(r,"kind"));p.setString(7,str(r,"subject"));p.setBigDecimal(8,r.get("amount").getAsBigDecimal());p.executeUpdate();
        }
    }
    private static String str(JsonObject r,String key){return r.get(key).getAsString();}
    @Override public void close(){pool.close();}
}
