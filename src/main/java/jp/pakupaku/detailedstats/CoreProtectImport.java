package jp.pakupaku.detailedstats;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Read-only, resumable import; checkpoints commit in the same MySQL transaction as rows. */
public final class CoreProtectImport {
    private final Store store;
    private final Path file;
    private final String prefix;
    private Map<Integer,String[]> users;
    private Map<Integer,String> worlds,materials;
    public CoreProtectImport(Store store,Path file,String prefix){
        if(!prefix.matches("[A-Za-z0-9_]+"))throw new IllegalArgumentException("Invalid CoreProtect prefix");
        this.store=store;this.file=file;this.prefix=prefix;
    }
    public boolean page() throws Exception {
        Class.forName("org.sqlite.JDBC");
        try(Connection sqlite=DriverManager.getConnection("jdbc:sqlite:file:"+file.toAbsolutePath()+"?mode=ro");Connection mysql=store.connection()){
            try(Statement s=sqlite.createStatement()){s.execute("PRAGMA query_only=ON");s.execute("PRAGMA busy_timeout=5000");}
            if(users==null){
                users=new HashMap<>();worlds=new HashMap<>();materials=new HashMap<>();
                try(Statement s=sqlite.createStatement();ResultSet r=s.executeQuery("SELECT id,user,uuid FROM "+prefix+"user")){while(r.next())users.put(r.getInt(1),new String[]{r.getString(2),r.getString(3)});}
                for(var spec:List.of(new Object[]{"world","world",worlds},new Object[]{"material_map","material",materials})){
                    @SuppressWarnings("unchecked") Map<Integer,String> map=(Map<Integer,String>)spec[2];
                    try(Statement s=sqlite.createStatement();ResultSet r=s.executeQuery("SELECT id,"+spec[1]+" FROM "+prefix+spec[0])){while(r.next())map.put(r.getInt(1),r.getString(2));}
                }
            }
            long last=0,high=-1;
            try(PreparedStatement p=mysql.prepareStatement("SELECT last_row,high_row,finished FROM detailed_stats_imports WHERE server_id=?")){
                p.setString(1,store.serverId());try(ResultSet r=p.executeQuery()){if(r.next()){if(r.getBoolean(3))return true;last=r.getLong(1);high=r.getLong(2);}}
            }
            if(high<0){
                try(PreparedStatement p=sqlite.prepareStatement("SELECT COALESCE(MAX(rowid),0) FROM "+prefix+"block WHERE time<=?")){p.setLong(1,store.installedAt()/1000);try(ResultSet r=p.executeQuery()){r.next();high=r.getLong(1);}}
                try(PreparedStatement p=mysql.prepareStatement("INSERT IGNORE INTO detailed_stats_imports VALUES(?,0,?,FALSE,?)")){p.setString(1,store.serverId());p.setLong(2,high);p.setLong(3,System.currentTimeMillis());p.executeUpdate();}
            }
            mysql.setAutoCommit(false);
            try(PreparedStatement read=sqlite.prepareStatement("SELECT rowid,time,user,wid,x,y,z,type,data,action,rolled_back FROM "+prefix+"block WHERE rowid>? AND rowid<=? ORDER BY rowid LIMIT 10000");
                PreparedStatement write=mysql.prepareStatement("INSERT INTO detailed_stats_coreprotect_blocks VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE source_row=VALUES(source_row)")){
                read.setLong(1,last);read.setLong(2,high);long scanned=last;int count=0;
                try(ResultSet r=read.executeQuery()){
                    while(r.next()){
                        scanned=r.getLong(1);String[] user=users.get(r.getInt(3));
                        if(user==null||user[1]==null||!user[1].matches("[0-9a-fA-F-]{36}")||r.getLong(2)*1000>store.installedAt())continue;
                        write.setString(1,store.serverId());write.setLong(2,scanned);write.setLong(3,r.getLong(2)*1000);
                        write.setString(4,user[1].toLowerCase(Locale.ROOT));write.setString(5,user[0]);write.setString(6,worlds.getOrDefault(r.getInt(4),"unknown:"+r.getInt(4)));
                        for(int i=5;i<=7;i++)write.setInt(i+2,r.getInt(i));
                        write.setString(10,materials.getOrDefault(r.getInt(8),"legacy:"+r.getInt(8)));
                        write.setInt(11,r.getInt(9));write.setInt(12,r.getInt(10));write.setInt(13,r.getInt(11));write.addBatch();count++;
                    }
                }
                if(count>0)write.executeBatch();
                boolean done=scanned==last||scanned>=high;
                try(PreparedStatement p=mysql.prepareStatement("UPDATE detailed_stats_imports SET last_row=?,finished=?,updated_ms=? WHERE server_id=?")){
                    p.setLong(1,scanned);p.setBoolean(2,done);p.setLong(3,System.currentTimeMillis());p.setString(4,store.serverId());p.executeUpdate();
                }
                mysql.commit();return done;
            }catch(Exception e){mysql.rollback();throw e;}
        }
    }
}
