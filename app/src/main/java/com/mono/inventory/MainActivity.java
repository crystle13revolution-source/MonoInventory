package com.mono.inventory;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.*;
import java.time.LocalDate;
import java.util.*;

public class MainActivity extends Activity {
    private InventoryDb db;
    private LinearLayout content;
    private String keyword="";
    private boolean byPlace=true;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);db=new InventoryDb(this);
        LinearLayout root=Ui.vertical(this);root.setBackgroundColor(Ui.BG);Ui.pad(root,this,17,20,17,6);setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.logo_app);header.addView(logo,Ui.lp(this,42,42,0));
        TextView title=Ui.text(this," 物集",29,true);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button add=button("＋ 物品",true);header.addView(add);add.setOnClickListener(v->openItem(null));root.addView(header,Ui.lp(this,-1,-2,14));
        EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(15);search.setHint("搜索物品 / 房间 / 地点 / 分类");search.setBackground(Ui.shape(Color.WHITE,14,this));Ui.pad(search,this,14,12,14,12);root.addView(search,Ui.lp(this,-1,-2,15));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int before,int count){keyword=s.toString().trim();render();}public void afterTextChanged(Editable e){}});
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));content=Ui.vertical(this);scroll.addView(content);render();
        TextView footer=Ui.text(this,"本地模式 · 数据只保存在这台设备",12,false);footer.setGravity(Gravity.CENTER);footer.setTextColor(Ui.MUTED);root.addView(footer,Ui.lp(this,-1,-2,8));
    }
    @Override protected void onResume(){super.onResume();if(content!=null)render();}
    private Button button(String text,boolean green){Button b=new Button(this);b.setAllCaps(false);b.setText(text);if(green){b.setTextColor(Color.WHITE);b.setBackground(Ui.shape(Ui.GREEN,12,this));}return b;}
    private void heading(LinearLayout target,String name){TextView t=Ui.text(this,name,19,true);target.addView(t,Ui.lp(this,-1,-2,8));}
    private void openItem(String id){Intent intent=new Intent(this,EditItemActivity.class);if(id!=null)intent.putExtra("item_id",id);startActivity(intent);}
    private void nameDialog(String title,java.util.function.Consumer<String> save){
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("名称");
        LinearLayout wrap=Ui.vertical(this);Ui.pad(wrap,this,20,0,20,0);wrap.addView(input);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(wrap).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{String n=input.getText().toString().trim();if(n.isEmpty()){input.setError("请输入名称");return;}try{save.accept(n);dialog.dismiss();render();}catch(Exception ex){input.setError(ex.getMessage());}}));dialog.show();
    }
    private void createPlace(){List<InventoryDb.Room> rooms=db.rooms();String[] labels=new String[rooms.size()];for(int i=0;i<rooms.size();i++)labels[i]=rooms.get(i).name;
        new AlertDialog.Builder(this).setTitle("选择房间").setItems(labels,(d,which)->browsePlaceForCreate(rooms.get(which).id,"")).show();}
    private void browsePlaceForCreate(String room,String parent){
        List<InventoryDb.Place> nodes=db.places(room,parent);List<String> labels=new ArrayList<>();labels.add("＋ 在这里创建地点");for(InventoryDb.Place n:nodes)labels.add("📁 "+n.name+"  ›");
        new AlertDialog.Builder(this).setTitle(parent.isEmpty()?db.room(room).name:db.placePath(parent)).setItems(labels.toArray(new String[0]),(d,which)->{
            if(which==0){nameDialog("创建地点",n->db.addPlace(room,parent,n));return;}
            browsePlaceForCreate(room,nodes.get(which-1).id);
        }).setNegativeButton("关闭",null).show();
    }
    private void render(){
        content.removeAllViews();List<Item> filtered=db.all(keyword);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView roomTitle=Ui.text(this,"我的房间",20,true);actions.addView(roomTitle,new LinearLayout.LayoutParams(0,-2,1));
        Button roomBtn=button("＋ 房间",false);actions.addView(roomBtn);roomBtn.setOnClickListener(v->nameDialog("创建房间",db::addRoom));
        Button placeBtn=button("＋ 地点",false);actions.addView(placeBtn);placeBtn.setOnClickListener(v->createPlace());content.addView(actions,Ui.lp(this,-1,-2,9));
        List<InventoryDb.Room> rooms=db.rooms();
        if(rooms.size()==1&&db.roomCount(InventoryDb.DEFAULT_ROOM)==0){TextView hint=Ui.text(this,"创建房间，开始整理你的空间",14,false);hint.setTextColor(Ui.MUTED);content.addView(hint,Ui.lp(this,-1,-2,8));}
        for(InventoryDb.Room r:rooms){int n=db.roomCount(r.id);if(r.id.equals(InventoryDb.DEFAULT_ROOM)&&n==0&&db.places(r.id,"").isEmpty())continue;
            if(!keyword.isEmpty()&&!r.name.contains(keyword)&&!roomMatches(r.id,filtered))continue;
            LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setBackground(Ui.shape(Color.WHITE,14,this));Ui.pad(card,this,16,14,12,14);
            TextView symbol=Ui.text(this,"⌂",28,true);symbol.setTextColor(Ui.GREEN);card.addView(symbol,Ui.lp(this,40,-2,0));
            LinearLayout body=Ui.vertical(this);card.addView(body,new LinearLayout.LayoutParams(0,-2,1));body.addView(Ui.text(this,r.name,18,true));
            TextView count=Ui.text(this,n+" 件物品",13,false);count.setTextColor(Ui.MUTED);body.addView(count);
            TextView arrow=Ui.text(this,"›",26,false);card.addView(arrow);card.setOnClickListener(v->openRoom(r.id));card.setOnLongClickListener(v->{roomActions(r);return true;});content.addView(card,Ui.lp(this,-1,-2,9));
        }
        LinearLayout tabs=new LinearLayout(this);Button loc=button("按地点",byPlace),cat=button("按分类",!byPlace);
        tabs.addView(loc,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));tabs.addView(cat,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));content.addView(tabs,Ui.lp(this,-1,-2,12));
        loc.setOnClickListener(v->{byPlace=true;render();});cat.setOnClickListener(v->{byPlace=false;render();});
        if(!keyword.isEmpty()){
            heading(content,"搜索结果 · "+filtered.size()+" 件物品");for(Item i:filtered)itemRow(content,i);
        }else if(byPlace){
            for(InventoryDb.Room room:rooms){List<InventoryDb.Place> places=db.places(room.id,"");if(places.isEmpty())continue;
                heading(content,room.name);for(InventoryDb.Place p:places)placeRow(content,p);
            }
        }else{
            LinkedHashMap<String,Integer> categories=new LinkedHashMap<>();for(Item i:filtered)categories.put(i.category,categories.getOrDefault(i.category,0)+1);
            for(Map.Entry<String,Integer> e:categories.entrySet()){TextView t=Ui.text(this,"▦   "+e.getKey()+"    "+e.getValue()+" 件  ›",16,true);t.setBackground(Ui.shape(Color.WHITE,12,this));Ui.pad(t,this,16,14,16,14);content.addView(t,Ui.lp(this,-1,-2,8));t.setOnClickListener(v->{showCategory(e.getKey());});}
        }
        int expired=0,soon=0;LocalDate now=LocalDate.now();for(Item i:db.all(""))if(!i.expiryDate.isEmpty())try{LocalDate d=LocalDate.parse(i.expiryDate);if(d.isBefore(now))expired++;else if(!d.isAfter(now.plusDays(7)))soon++;}catch(Exception ignored){}
        TextView info=Ui.text(this,"物品 "+db.all("").size()+" 件 · 已过期 "+expired+" · 7 天内到期 "+soon,12,false);info.setTextColor(Ui.MUTED);content.addView(info,Ui.lp(this,-1,-2,12));
    }
    private boolean roomMatches(String roomId,List<Item> filtered){for(Item i:filtered){InventoryDb.Place p=db.place(i.placeId);if(p!=null&&p.roomId.equals(roomId))return true;}return false;}
    private void showCategory(String category){List<Item> all=db.all("");List<Item> matched=new ArrayList<>();for(Item i:all)if(category.equals(i.category))matched.add(i);showItems(category,matched);}
    private void showItems(String title,List<Item> items){LinearLayout root=Ui.vertical(this);root.setBackgroundColor(Ui.BG);Ui.pad(root,this,18,18,18,10);setContentView(root);Button back=button("‹ 返回首页",false);root.addView(back);back.setOnClickListener(v->recreate());heading(root,title+" · "+items.size()+" 件");ScrollView scroll=new ScrollView(this);root.addView(scroll);LinearLayout list=Ui.vertical(this);scroll.addView(list);for(Item i:items)itemRow(list,i);}
    private void itemRow(LinearLayout target,Item i){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setBackground(Ui.shape(Color.WHITE,12,this));Ui.pad(row,this,12,10,12,10);
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);PhotoStore.show(image,i.photoPath);row.addView(image,Ui.lp(this,58,58,0));
        LinearLayout text=Ui.vertical(this);Ui.pad(text,this,10,0,0,0);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));text.addView(Ui.text(this,i.name,16,true));
        TextView meta=Ui.text(this,i.category+" · "+db.locationLabel(i),12,false);meta.setTextColor(Ui.MUTED);text.addView(meta);
        row.setOnClickListener(v->openItem(i.id));target.addView(row,Ui.lp(this,-1,-2,8));
    }
    private void placeRow(LinearLayout target,InventoryDb.Place p){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setBackground(Ui.shape(Color.WHITE,12,this));Ui.pad(row,this,14,10,12,10);
        TextView icon=Ui.text(this,"▣",23,true);icon.setTextColor(Ui.GREEN);row.addView(icon,Ui.lp(this,38,-2,0));
        LinearLayout text=Ui.vertical(this);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));text.addView(Ui.text(this,p.name,16,true));TextView count=Ui.text(this,db.placeCount(p.id)+" 件物品",12,false);count.setTextColor(Ui.MUTED);text.addView(count);
        TextView arrow=Ui.text(this,"›",24,false);row.addView(arrow);row.setOnClickListener(v->openPlace(p.id));row.setOnLongClickListener(v->{placeActions(p);return true;});target.addView(row,Ui.lp(this,-1,-2,8));
    }
    private void openRoom(String id){InventoryDb.Room room=db.room(id);if(room==null)return;showSpace(room.name,id,"");}
    private void openPlace(String id){InventoryDb.Place place=db.place(id);if(place==null)return;showSpace(db.placePath(id),place.roomId,id);}
    private void showSpace(String name,String roomId,String parent){
        LinearLayout root=Ui.vertical(this);root.setBackgroundColor(Ui.BG);Ui.pad(root,this,18,16,18,10);setContentView(root);
        Button back=button("‹ 首页",false);root.addView(back);back.setOnClickListener(v->recreate());heading(root,name);
        Button create=button("＋ 在此创建地点",true);root.addView(create,Ui.lp(this,-1,-2,12));create.setOnClickListener(v->nameDialog("创建地点",n->{db.addPlace(roomId,parent,n);showSpace(name,roomId,parent);}));
        ScrollView scroll=new ScrollView(this);root.addView(scroll);LinearLayout contents=Ui.vertical(this);scroll.addView(contents);
        List<InventoryDb.Place> places=db.places(roomId,parent);for(InventoryDb.Place p:places)placeRow(contents,p);
        heading(contents,"物品");for(Item i:db.all("")){
            InventoryDb.Place p=db.place(i.placeId);
            if(parent.isEmpty()?(p!=null&&p.roomId.equals(roomId)):(p!=null&&inSubtree(p,parent)))itemRow(contents,i);
        }
    }
    private boolean inSubtree(InventoryDb.Place p,String root){HashSet<String> seen=new HashSet<>();while(p!=null&&seen.add(p.id)){if(p.id.equals(root))return true;p=db.place(p.parentId);}return false;}
    private void roomActions(InventoryDb.Room r){new AlertDialog.Builder(this).setTitle(r.name).setItems(new String[]{"重命名","删除空房间"},(d,which)->{
        if(which==0)nameDialog("重命名房间",n->db.renameRoom(r.id,n));else new AlertDialog.Builder(this).setMessage("删除房间前需要先将内部地点移动到其他房间。").setNegativeButton("取消",null).setPositiveButton("删除",(a,b)->{try{db.deleteRoom(r.id);recreate();}catch(Exception ex){Toast.makeText(this,ex.getMessage(),Toast.LENGTH_LONG).show();}}).show();
    }).show();}
    private void placeActions(InventoryDb.Place p){new AlertDialog.Builder(this).setTitle(p.name).setItems(new String[]{"重命名","移动地点","删除空子地点"},(d,which)->{
        if(which==0)nameDialog("重命名地点",n->db.renamePlace(p.id,n));
        if(which==1)chooseMoveRoom(p);
        if(which==2)new AlertDialog.Builder(this).setMessage("删除地点后，其直属物品将变为未设置地点。请先移动所有子地点。").setNegativeButton("取消",null).setPositiveButton("删除",(a,b)->{try{db.deletePlace(p.id);recreate();}catch(Exception ex){Toast.makeText(this,ex.getMessage(),Toast.LENGTH_LONG).show();}}).show();
    }).show();}
    private void chooseMoveRoom(InventoryDb.Place p){List<InventoryDb.Room> rooms=db.rooms();String[] names=new String[rooms.size()];for(int i=0;i<names.length;i++)names[i]=rooms.get(i).name;
        new AlertDialog.Builder(this).setTitle("移动到哪个房间？").setItems(names,(d,choice)->chooseMoveParent(p,rooms.get(choice).id,"")).show();}
    private void chooseMoveParent(InventoryDb.Place p,String room,String parent){List<InventoryDb.Place> children=db.places(room,parent);List<String> opts=new ArrayList<>();opts.add("✓ 移动到此处");for(InventoryDb.Place c:children)opts.add("📁 "+c.name+" ›");new AlertDialog.Builder(this).setTitle(parent.isEmpty()?db.room(room).name:db.placePath(parent)).setItems(opts.toArray(new String[0]),(d,index)->{
        if(index==0){try{db.movePlace(p.id,room,parent);recreate();}catch(Exception ex){Toast.makeText(this,ex.getMessage(),Toast.LENGTH_LONG).show();}}else chooseMoveParent(p,room,children.get(index-1).id);
    }).setNegativeButton("取消",null).show();}
}
