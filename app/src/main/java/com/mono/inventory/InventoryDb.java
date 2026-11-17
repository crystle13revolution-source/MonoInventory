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
    public InventoryDb(Context c) { super(c, DB_NAME, null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE groups (id TEXT PRIMARY KEY, name TEXT NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("INSERT INTO groups VALUES ('local-default','我的物品',0)");
        db.execSQL("CREATE TABLE members (id TEXT PRIMARY KEY, group_id TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
        db.execSQL("CREATE TABLE items (id TEXT PRIMARY KEY, group_id TEXT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, location TEXT NOT NULL, status TEXT NOT NULL, quantity_mode TEXT NOT NULL, quantity REAL NOT NULL, unit TEXT NOT NULL, expiry_date TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', photo_path TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, version INTEGER NOT NULL DEFAULT 1, deleted_at INTEGER)");
        db.execSQL("CREATE INDEX idx_items_group ON items(group_id,deleted_at)");
        db.execSQL("CREATE INDEX idx_items_expiry ON items(expiry_date)");
        db.execSQL("CREATE TABLE change_log (id INTEGER PRIMARY KEY AUTOINCREMENT, item_id TEXT NOT NULL, operation TEXT NOT NULL, changed_at INTEGER NOT NULL, version INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { /* Future migrations go here; never drop user data. */ }
    private Item read(Cursor c) {
        Item i=new Item();
        i.id=c.getString(c.getColumnIndexOrThrow("id")); i.groupId=c.getString(c.getColumnIndexOrThrow("group_id"));
        i.name=c.getString(c.getColumnIndexOrThrow("name")); i.category=c.getString(c.getColumnIndexOrThrow("category"));
        i.location=c.getString(c.getColumnIndexOrThrow("location")); i.status=c.getString(c.getColumnIndexOrThrow("status"));
        i.quantityMode=c.getString(c.getColumnIndexOrThrow("quantity_mode")); i.quantity=c.getDouble(c.getColumnIndexOrThrow("quantity"));
        i.unit=c.getString(c.getColumnIndexOrThrow("unit")); i.expiryDate=c.getString(c.getColumnIndexOrThrow("expiry_date"));
        i.notes=c.getString(c.getColumnIndexOrThrow("notes")); i.photoPath=c.getString(c.getColumnIndexOrThrow("photo_path"));
        i.createdAt=c.getLong(c.getColumnIndexOrThrow("created_at")); i.updatedAt=c.getLong(c.getColumnIndexOrThrow("updated_at"));
        i.version=c.getInt(c.getColumnIndexOrThrow("version")); return i;
    }
    public List<Item> all(String filter) {
        List<Item> list=new ArrayList<>();
        String query="SELECT * FROM items WHERE deleted_at IS NULL AND group_id=?";
        String[] args;
        if (filter==null || filter.trim().isEmpty()) args=new String[]{"local-default"};
        else { query+=" AND (name LIKE ? OR category LIKE ? OR location LIKE ? OR notes LIKE ?)"; String q="%"+filter.trim()+"%"; args=new String[]{"local-default",q,q,q,q}; }
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
        v.put("category",i.category);v.put("location",i.location);v.put("status",i.status);
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
