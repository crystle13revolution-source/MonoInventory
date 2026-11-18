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
    public InventoryDb(Context c) { super(c, DB_NAME, null, 2); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE groups (id TEXT PRIMARY KEY, name TEXT NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("INSERT INTO groups VALUES ('local-default','我的物品',0)");
        db.execSQL("CREATE TABLE members (id TEXT PRIMARY KEY, group_id TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
        db.execSQL("CREATE TABLE items (id TEXT PRIMARY KEY, group_id TEXT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, location TEXT NOT NULL, status TEXT NOT NULL, quantity_mode TEXT NOT NULL, quantity REAL NOT NULL, unit TEXT NOT NULL, expiry_date TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', photo_path TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, version INTEGER NOT NULL DEFAULT 1, deleted_at INTEGER)");
        db.execSQL("CREATE INDEX idx_items_group ON items(group_id,deleted_at)");
        db.execSQL("CREATE INDEX idx_items_expiry ON items(expiry_date)");
        db.execSQL("CREATE TABLE change_log (id INTEGER PRIMARY KEY AUTOINCREMENT, item_id TEXT NOT NULL, operation TEXT NOT NULL, changed_at INTEGER NOT NULL, version INTEGER NOT NULL)");
        createOptions(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<2) createOptions(db);
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
        i.version=c.getInt(c.getColumnIndexOrThrow("version")); i.categoryId=c.getString(c.getColumnIndexOrThrow("category_id")); i.tags=c.getString(c.getColumnIndexOrThrow("tags")); return i;
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
        v.put("category",i.category);v.put("category_id",i.categoryId);v.put("tags",i.tags);v.put("location",i.location);v.put("status",i.status);
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
