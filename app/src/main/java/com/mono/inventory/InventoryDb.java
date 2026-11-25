package com.mono.inventory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InventoryDb extends SQLiteOpenHelper {
    private static final String DB_NAME="inventory.db";
    public InventoryDb(Context c) { super(c, DB_NAME, null, 3); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE groups (id TEXT PRIMARY KEY, name TEXT NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("INSERT INTO groups VALUES ('local-default','我的物品',0)");
        db.execSQL("CREATE TABLE members (id TEXT PRIMARY KEY, group_id TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
        db.execSQL("CREATE TABLE items (id TEXT PRIMARY KEY, group_id TEXT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, location TEXT NOT NULL, status TEXT NOT NULL, quantity_mode TEXT NOT NULL, quantity REAL NOT NULL, unit TEXT NOT NULL, expiry_date TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', photo_path TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, version INTEGER NOT NULL DEFAULT 1, deleted_at INTEGER)");
        db.execSQL("CREATE INDEX idx_items_group ON items(group_id,deleted_at)");
        db.execSQL("CREATE INDEX idx_items_expiry ON items(expiry_date)");
        db.execSQL("CREATE TABLE change_log (id INTEGER PRIMARY KEY AUTOINCREMENT, item_id TEXT NOT NULL, operation TEXT NOT NULL, changed_at INTEGER NOT NULL, version INTEGER NOT NULL)");
        createOptions(db);
        createSpaces(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<2) createOptions(db);
        if(oldVersion<3) { createSpaces(db); migrateSpaces(db); }
    }
    // v0.3: rooms own a tree of places; legacy locations and strings remain intact.
    public static final class Room {
        public final String id,name;
        public Room(String id,String name){this.id=id;this.name=name;}
    }
    public static final class Place {
        public final String id,roomId,parentId,name;
        public Place(String id,String roomId,String parentId,String name){this.id=id;this.roomId=roomId;this.parentId=parentId;this.name=name;}
    }
    public static final String DEFAULT_ROOM="default-room";
    private void createSpaces(SQLiteDatabase d){
        d.execSQL("CREATE TABLE rooms (id TEXT PRIMARY KEY, name TEXT NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0)");
        d.execSQL("CREATE TABLE places (id TEXT PRIMARY KEY, room_id TEXT NOT NULL, parent_id TEXT NOT NULL DEFAULT '', name TEXT NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0, UNIQUE(room_id,parent_id,name))");
        d.execSQL("CREATE INDEX idx_places_parent ON places(room_id,parent_id)");
        d.execSQL("ALTER TABLE items ADD COLUMN place_id TEXT NOT NULL DEFAULT ''");
        d.execSQL("INSERT INTO rooms(id,name) VALUES ('default-room','未分配房间')");
    }
    private void migrateSpaces(SQLiteDatabase d){
        try(Cursor c=d.rawQuery("SELECT DISTINCT location FROM items WHERE deleted_at IS NULL AND location<>'' AND location<>'未设置'",null)){
            while(c.moveToNext()){
                String name=c.getString(0);
                String id=insertPlace(d,DEFAULT_ROOM,"",name);
                ContentValues cv=new ContentValues();cv.put("place_id",id);
                d.update("items",cv,"location=? AND (place_id IS NULL OR place_id='')",new String[]{name});
            }
        }
    }
    private String insertPlace(SQLiteDatabase d,String room,String parent,String name){
        name=validName(name);
        ContentValues v=new ContentValues();v.put("id",UUID.randomUUID().toString());v.put("room_id",room);v.put("parent_id",parent);v.put("name",name);
        d.insertWithOnConflict("places",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        try(Cursor c=d.rawQuery("SELECT id FROM places WHERE room_id=? AND parent_id=? AND name=?",new String[]{room,parent,name})){
            if(c.moveToFirst())return c.getString(0);
        }
        throw new IllegalStateException("地点创建失败");
    }
    public List<Room> rooms(){
        List<Room> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name FROM rooms ORDER BY CASE WHEN id='default-room' THEN 1 ELSE 0 END, sort_order,name",null)){
            while(c.moveToNext())out.add(new Room(c.getString(0),c.getString(1)));
        }return out;
    }
    public String addRoom(String name){
        name=validName(name);String id=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("id",id);v.put("name",name);
        getWritableDatabase().insertOrThrow("rooms",null,v);return id;
    }
    public Room room(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name FROM rooms WHERE id=?",new String[]{id})){
        return c.moveToFirst()?new Room(c.getString(0),c.getString(1)):null;
    }}
    public Place place(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT id,room_id,parent_id,name FROM places WHERE id=?",new String[]{id})){
        return c.moveToFirst()?new Place(c.getString(0),c.getString(1),c.getString(2),c.getString(3)):null;
    }}
    public List<Place> places(String room,String parent){
        List<Place> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,room_id,parent_id,name FROM places WHERE room_id=? AND parent_id=? ORDER BY sort_order,name COLLATE NOCASE",new String[]{room,parent})){
            while(c.moveToNext())out.add(new Place(c.getString(0),c.getString(1),c.getString(2),c.getString(3)));
        }return out;
    }
    public String addPlace(String room,String parent,String name){
        if(room(room)==null)throw new IllegalArgumentException("房间不存在");
        if(!parent.isEmpty()){Place p=place(parent);if(p==null||!p.roomId.equals(room))throw new IllegalArgumentException("上级地点不属于此房间");}
        return insertPlace(getWritableDatabase(),room,parent,name);
    }
    public String placePath(String id){
        java.util.LinkedList<String> parts=new java.util.LinkedList<>();java.util.HashSet<String> seen=new java.util.HashSet<>();
        while(id!=null&&!id.isEmpty()&&seen.add(id)){Place p=place(id);if(p==null)break;parts.addFirst(p.name);id=p.parentId;}
        return String.join(" / ",parts);
    }
    public String locationLabel(Item i){
        Place p=place(i.placeId==null?"":i.placeId);
        if(p==null)return i.location;
        Room r=room(p.roomId);return (r==null?"":r.name+" / ")+placePath(p.id);
    }
    public int roomCount(String roomId){
        // Every place belongs to exactly one room; no recursion needed for counts.
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM items WHERE deleted_at IS NULL AND place_id IN (SELECT id FROM places WHERE room_id=?)",new String[]{roomId})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    public int placeCount(String placeId){
        int count=0;java.util.ArrayDeque<String> pending=new java.util.ArrayDeque<>();java.util.HashSet<String> seen=new java.util.HashSet<>();pending.add(placeId);
        while(!pending.isEmpty()){
            String id=pending.removeFirst();if(!seen.add(id))continue;
            try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM items WHERE deleted_at IS NULL AND place_id=?",new String[]{id})){if(c.moveToFirst())count+=c.getInt(0);}
            Place p=place(id);if(p!=null)for(Place child:places(p.roomId,id))pending.add(child.id);
        }return count;
    }
    public void renameRoom(String id,String name){ContentValues v=new ContentValues();v.put("name",validName(name));getWritableDatabase().update("rooms",v,"id=?",new String[]{id});}
    public void renamePlace(String id,String name){
        Place p=place(id);if(p==null)throw new IllegalArgumentException("地点不存在");
        ContentValues v=new ContentValues();v.put("name",validName(name));getWritableDatabase().update("places",v,"id=?",new String[]{id});
        updateLegacyPaths();
    }
    private void updateLegacyPaths(){
        SQLiteDatabase d=getWritableDatabase();try(Cursor c=d.rawQuery("SELECT id,place_id FROM items WHERE deleted_at IS NULL AND place_id<>''",null)){
            while(c.moveToNext()){String pid=c.getString(1);if(place(pid)==null)continue;ContentValues v=new ContentValues();v.put("location",placePath(pid));d.update("items",v,"id=?",new String[]{c.getString(0)});}
        }
    }
    public void movePlace(String id,String roomId,String parentId){
        Place current=place(id);if(current==null||room(roomId)==null)throw new IllegalArgumentException("地点或房间不存在");
        if(id.equals(parentId))throw new IllegalArgumentException("不能移动到自身");
        Place target=parentId.isEmpty()?null:place(parentId);
        if(!parentId.isEmpty()&&(target==null||!target.roomId.equals(roomId)))throw new IllegalArgumentException("目标地点无效");
        for(Place p=target;p!=null&&!p.parentId.isEmpty();p=place(p.parentId))if(id.equals(p.id))throw new IllegalArgumentException("不能移动到子地点");
        if(target!=null&&id.equals(target.id))throw new IllegalArgumentException("不能移动到自身");
        java.util.ArrayList<String> descendants=new java.util.ArrayList<>();java.util.ArrayDeque<String> todo=new java.util.ArrayDeque<>();todo.add(id);
        while(!todo.isEmpty()){String next=todo.removeFirst();descendants.add(next);Place p=place(next);if(p!=null)for(Place child:places(p.roomId,next))todo.add(child.id);}
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            ContentValues root=new ContentValues();root.put("room_id",roomId);root.put("parent_id",parentId);d.update("places",root,"id=?",new String[]{id});
            for(String desc:descendants){ContentValues v=new ContentValues();v.put("room_id",roomId);d.update("places",v,"id=?",new String[]{desc});}
            d.setTransactionSuccessful();
        }finally{d.endTransaction();}updateLegacyPaths();
    }
    public void deletePlace(String id){
        Place p=place(id);if(p==null)return;
        if(!places(p.roomId,id).isEmpty())throw new IllegalStateException("请先移动或删除子地点");
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            ContentValues item=new ContentValues();item.put("place_id","");item.put("location","未设置");
            d.update("items",item,"place_id=?",new String[]{id});
            d.delete("places","id=?",new String[]{id});d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    public void deleteRoom(String id){
        if(DEFAULT_ROOM.equals(id))throw new IllegalArgumentException("不能删除默认房间");
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM places WHERE room_id=?",new String[]{id})){
            if(c.moveToFirst()&&c.getInt(0)>0)throw new IllegalStateException("请先将地点移至其他房间");
        }
        getWritableDatabase().delete("rooms","id=?",new String[]{id});
    }
    public static final class Category {
        public final String id, name;
        Category(String id,String name){this.id=id;this.name=name;}
    }
    private void createOptions(SQLiteDatabase db){
        db.execSQL("CREATE TABLE categories (id TEXT PRIMARY KEY, parent_id TEXT NOT NULL DEFAULT '', name TEXT NOT NULL, UNIQUE(parent_id,name))");
        db.execSQL("CREATE TABLE locations (name TEXT PRIMARY KEY)");
        db.execSQL("CREATE TABLE tags (name TEXT PRIMARY KEY)");
        db.execSQL("ALTER TABLE items ADD COLUMN category_id TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE items ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'");
        for(String n:new String[]{"日用品","衣服","家具","爱好","生产力装备","食品","数码设备","书籍","其他"}) ensureCategory(db,"",n);
        List<String> oldCategories=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT DISTINCT category FROM items",null)){
            while(c.moveToNext()) oldCategories.add(c.getString(0));
        }
        for(String n:oldCategories){String id=ensureCategory(db,"",n);db.execSQL("UPDATE items SET category_id=? WHERE category=?",new Object[]{id,n});}
        db.execSQL("INSERT OR IGNORE INTO locations SELECT DISTINCT location FROM items WHERE location<>''");
        db.execSQL("INSERT OR IGNORE INTO locations VALUES ('未设置')");
    }
    private String ensureCategory(SQLiteDatabase db,String parent,String name){
        ContentValues v=new ContentValues();v.put("id",UUID.randomUUID().toString());v.put("parent_id",parent);v.put("name",name);
        db.insertWithOnConflict("categories",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        try(Cursor c=db.rawQuery("SELECT id FROM categories WHERE parent_id=? AND name=?",new String[]{parent,name})) {if(c.moveToFirst())return c.getString(0);}
        throw new IllegalStateException("无法创建分类");
    }
    public String createCategory(String parent,String name){
        if(!parent.isEmpty() && !categoryExists(parent))throw new IllegalArgumentException("上级分类不存在");
        return ensureCategory(getWritableDatabase(),parent,validName(name));
    }
    private boolean categoryExists(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM categories WHERE id=?",new String[]{id})){return c.moveToFirst();}}
    public List<Category> children(String parent){List<Category> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name FROM categories WHERE parent_id=? ORDER BY name COLLATE NOCASE",new String[]{parent})){while(c.moveToNext())out.add(new Category(c.getString(0),c.getString(1)));}return out;}
    public String categoryParent(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT parent_id FROM categories WHERE id=?",new String[]{id})){return c.moveToFirst()?c.getString(0):"";}}
    public String categoryPath(String id){java.util.LinkedList<String> parts=new java.util.LinkedList<>();java.util.HashSet<String> seen=new java.util.HashSet<>();while(!id.isEmpty()&&seen.add(id)){try(Cursor c=getReadableDatabase().rawQuery("SELECT parent_id,name FROM categories WHERE id=?",new String[]{id})){if(!c.moveToFirst())break;parts.addFirst(c.getString(1));id=c.getString(0);}}return String.join(" ＞ ",parts);}
    public String rootCategory(String name){return ensureCategory(getWritableDatabase(),"",name);}
    private String validName(String n){n=n.trim();if(n.isEmpty())throw new IllegalArgumentException("请输入名称");return n;}
    private String addOption(String table,String name){name=validName(name);ContentValues v=new ContentValues();v.put("name",name);getWritableDatabase().insertWithOnConflict(table,null,v,SQLiteDatabase.CONFLICT_IGNORE);return name;}
    public String addLocation(String name){return addOption("locations",name);}
    public String addTag(String name){return addOption("tags",name);}
    private List<String> options(String table){List<String> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT name FROM "+table+" ORDER BY name COLLATE NOCASE",null)){while(c.moveToNext())out.add(c.getString(0));}return out;}
    public List<String> locations(){return options("locations");}
    public List<String> tags(){return options("tags");}

    private Item read(Cursor c) {
        Item i=new Item();
        i.id=c.getString(c.getColumnIndexOrThrow("id")); i.groupId=c.getString(c.getColumnIndexOrThrow("group_id"));
        i.name=c.getString(c.getColumnIndexOrThrow("name")); i.category=c.getString(c.getColumnIndexOrThrow("category"));
        i.location=c.getString(c.getColumnIndexOrThrow("location")); i.status=c.getString(c.getColumnIndexOrThrow("status"));
        i.quantityMode=c.getString(c.getColumnIndexOrThrow("quantity_mode")); i.quantity=c.getDouble(c.getColumnIndexOrThrow("quantity"));
        i.unit=c.getString(c.getColumnIndexOrThrow("unit")); i.expiryDate=c.getString(c.getColumnIndexOrThrow("expiry_date"));
        i.notes=c.getString(c.getColumnIndexOrThrow("notes")); i.photoPath=c.getString(c.getColumnIndexOrThrow("photo_path"));
        i.createdAt=c.getLong(c.getColumnIndexOrThrow("created_at")); i.updatedAt=c.getLong(c.getColumnIndexOrThrow("updated_at"));
        i.version=c.getInt(c.getColumnIndexOrThrow("version")); i.categoryId=c.getString(c.getColumnIndexOrThrow("category_id")); i.tags=c.getString(c.getColumnIndexOrThrow("tags")); i.placeId=c.getString(c.getColumnIndexOrThrow("place_id")); return i;
    }
    public List<Item> all(String filter) {
        List<Item> list=new ArrayList<>();
        String query="SELECT * FROM items WHERE deleted_at IS NULL AND group_id=?";
        String[] args;
        if (filter==null || filter.trim().isEmpty()) args=new String[]{"local-default"};
        else { query+=" AND (name LIKE ? OR category LIKE ? OR location LIKE ? OR notes LIKE ? OR tags LIKE ?)"; String q="%"+filter.trim()+"%"; args=new String[]{"local-default",q,q,q,q,q}; }
        query+=" ORDER BY updated_at DESC";
        try(Cursor c=getReadableDatabase().rawQuery(query,args)) { while(c.moveToNext()) list.add(read(c)); }
        return list;
    }
    public Item get(String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM items WHERE id=? AND deleted_at IS NULL",new String[]{id})) {
            return c.moveToFirst()?read(c):null;
        }
    }
    public void save(Item i) {
        SQLiteDatabase db=getWritableDatabase(); long now=System.currentTimeMillis(); boolean create=i.id==null;
        if(create){i.id=UUID.randomUUID().toString(); i.createdAt=now; i.version=1;}
        else i.version++;
        i.updatedAt=now;
        ContentValues v=new ContentValues(); v.put("id",i.id);v.put("group_id",i.groupId);v.put("name",i.name);
        v.put("category",i.category);v.put("category_id",i.categoryId);v.put("place_id",i.placeId==null?"":i.placeId);v.put("tags",i.tags);v.put("location",i.location);v.put("status",i.status);
        v.put("quantity_mode",i.quantityMode);v.put("quantity",i.quantity);v.put("unit",i.unit);
        v.put("expiry_date",i.expiryDate);v.put("notes",i.notes);v.put("photo_path",i.photoPath);
        v.put("created_at",i.createdAt);v.put("updated_at",i.updatedAt);v.put("version",i.version);
        db.beginTransaction();
        try {
            if(create) db.insertOrThrow("items",null,v);
            else if(db.update("items",v,"id=? AND deleted_at IS NULL",new String[]{i.id})!=1) throw new IllegalStateException("物品不存在");
            log(db,i.id,create?"create":"update",now,i.version);
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    public void delete(String id) {
        SQLiteDatabase db=getWritableDatabase();long now=System.currentTimeMillis();
        db.beginTransaction();
        try {
            Item item=get(id);
            if(item!=null) { ContentValues v=new ContentValues();v.put("deleted_at",now);v.put("updated_at",now);v.put("version",item.version+1);
                db.update("items",v,"id=? AND deleted_at IS NULL",new String[]{id}); log(db,id,"delete",now,item.version+1); }
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    private void log(SQLiteDatabase db,String id,String operation,long at,int version) {
        ContentValues c=new ContentValues();c.put("item_id",id);c.put("operation",operation);c.put("changed_at",at);c.put("version",version);db.insertOrThrow("change_log",null,c);
    }
}
