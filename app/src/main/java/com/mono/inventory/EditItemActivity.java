package com.mono.inventory;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.time.LocalDate;

public class EditItemActivity extends Activity {
    private static final int PICK_PHOTO=12, TAKE_PHOTO=13;
    private static final String[] CATEGORIES={"日用品","衣服","家具","爱好","生产力装备","食品","数码设备","书籍","其他"};
    private static final String[] STATUS={"正常","使用中","闲置","借出","维修中","损坏","待处理","已耗尽"};
    private static final String[] MODES={"单件物品","可计数库存"};
    private InventoryDb db; private Item item;
    private EditText name,location,quantity,unit,notes;
    private AutoCompleteTextView category;
    private Spinner status,mode;
    private TextView expiry;
    private ImageView photo;
    private Uri pendingCameraUri;
    private String stagedPhotoPath="";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);db=new InventoryDb(this);
        String id=getIntent().getStringExtra("item_id");item=id==null?new Item():db.get(id);
        if(item==null){Toast.makeText(this,"物品不存在",Toast.LENGTH_SHORT).show();finish();return;}
        if(state!=null){String pending=state.getString("camera_uri");if(pending!=null)pendingCameraUri=Uri.parse(pending);stagedPhotoPath=state.getString("staged_photo","");}
        LinearLayout root=Ui.vertical(this);root.setBackgroundColor(Ui.BG);setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);Ui.pad(header,this,18,16,18,10);root.addView(header);
        Button back=new Button(this);back.setText("‹ 返回");back.setAllCaps(false);header.addView(back);back.setOnClickListener(v->finish());
        TextView title=Ui.text(this,id==null?"添加物品":"编辑物品",23,true);header.addView(title);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout form=Ui.vertical(this);Ui.pad(form,this,20,8,20,24);scroll.addView(form);
        photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setBackground(Ui.shape(Color.rgb(232,237,233),14,this));
        PhotoStore.show(photo,stagedPhotoPath.isEmpty()?item.photoPath:stagedPhotoPath);
        form.addView(photo,Ui.lp(this,-1,178,10));
        LinearLayout photoActions=new LinearLayout(this);form.addView(photoActions,Ui.lp(this,-1,-2,20));
        Button camera=action("📷 拍照");photoActions.addView(camera,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        Button gallery=action("选择图片");LinearLayout.LayoutParams gal=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);gal.leftMargin=Ui.dp(this,10);photoActions.addView(gallery,gal);
        camera.setOnClickListener(v->takePhoto());gallery.setOnClickListener(v->pickPhoto());
        name=field(form,"物品名称 *",item.name,"例如：冬季外套");
        label(form,"分类");category=new AutoCompleteTextView(this);category.setSingleLine();category.setThreshold(1);
        category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,CATEGORIES));category.setText(item.category);decorate(category);form.addView(category,Ui.lp(this,-1,52,14));
        location=field(form,"存放地点",item.location,"例如：卧室 / 衣柜 A / 第二层");
        label(form,"当前状态");status=spinner(form,STATUS,index(STATUS,item.status));
        label(form,"数量管理方式");mode=spinner(form,MODES,"bulk".equals(item.quantityMode)?1:0);
        LinearLayout quantRow=new LinearLayout(this);form.addView(quantRow,Ui.lp(this,-1,-2,16));
        LinearLayout a=Ui.vertical(this),b=Ui.vertical(this);quantRow.addView(a,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1);bp.leftMargin=Ui.dp(this,12);quantRow.addView(b,bp);
        quantity=field(a,"数量",String.valueOf(item.quantity),"1");quantity.setInputType(8194);
        unit=field(b,"单位",item.unit,"件 / 个 / 瓶 / kg");
        label(form,"保质期 / 到期日（可不填）");expiry=Ui.text(this,item.expiryDate.isEmpty()?"选择到期日":item.expiryDate,16,false);
        expiry.setGravity(Gravity.CENTER_VERTICAL);expiry.setBackground(Ui.shape(Color.WHITE,10,this));Ui.pad(expiry,this,14,8,14,8);form.addView(expiry,Ui.lp(this,-1,52,8));expiry.setOnClickListener(v->selectDate());
        Button clearDate=action("清除日期");form.addView(clearDate,Ui.lp(this,-1,40,18));clearDate.setOnClickListener(v->expiry.setText("选择到期日"));
        notes=field(form,"备注",item.notes,"品牌、型号、购买信息、其他说明");notes.setSingleLine(false);notes.setMinLines(3);
        Button save=action("保存物品");save.setTextColor(Color.WHITE);save.setBackground(Ui.shape(Ui.GREEN,12,this));form.addView(save,Ui.lp(this,-1,54,12));save.setOnClickListener(v->save());
        if(item.id!=null){Button delete=action("删除此物品");delete.setTextColor(Color.rgb(157,49,49));form.addView(delete,Ui.lp(this,-1,46,0));delete.setOnClickListener(v->confirmDelete());}
    }
    private Button action(String title){Button b=new Button(this);b.setText(title);b.setAllCaps(false);return b;}
    private void label(LinearLayout p,String text){TextView t=Ui.text(this,text,14,true);p.addView(t,Ui.lp(this,-1,-2,6));}
    private void decorate(EditText e){e.setTextSize(15);e.setSingleLine(true);e.setBackground(Ui.shape(Color.WHITE,10,this));Ui.pad(e,this,12,8,12,8);}
    private EditText field(LinearLayout p,String label,String value,String hint){label(p,label);EditText e=new EditText(this);e.setText(value);e.setHint(hint);decorate(e);p.addView(e,Ui.lp(this,-1,52,14));return e;}
    private Spinner spinner(LinearLayout p,String[] values,int selected){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));s.setSelection(selected);s.setBackground(Ui.shape(Color.WHITE,10,this));Ui.pad(s,this,8,4,8,4);p.addView(s,Ui.lp(this,-1,52,14));return s;}
    private int index(String[] values,String val){for(int i=0;i<values.length;i++)if(values[i].equals(val))return i;return 0;}
    private void selectDate(){LocalDate date;
        try{date=LocalDate.parse(expiry.getText().toString());}catch(Exception e){date=LocalDate.now();}
        DatePickerDialog d=new DatePickerDialog(this,(view,y,m,day)->expiry.setText(String.format(java.util.Locale.ROOT,"%04d-%02d-%02d",y,m+1,day)),date.getYear(),date.getMonthValue()-1,date.getDayOfMonth());d.show();}
    private void save(){
        String n=name.getText().toString().trim();if(n.isEmpty()){name.setError("请填写物品名称");name.requestFocus();return;}
        double q;try{q=Double.parseDouble(quantity.getText().toString().trim());}catch(Exception ex){quantity.setError("请输入有效数量");return;}
        if(!Double.isFinite(q)||q<=0){quantity.setError("数量必须大于 0");return;}
        String unitValue=unit.getText().toString().trim();if(unitValue.isEmpty()){unit.setError("请输入单位");return;}
        item.name=n;item.category=nonempty(category.getText().toString(),"其他");item.location=nonempty(location.getText().toString(),"未设置");
        item.status=STATUS[status.getSelectedItemPosition()];item.quantityMode=mode.getSelectedItemPosition()==0?"single":"bulk";
        item.quantity=q;item.unit=unitValue;item.notes=notes.getText().toString().trim();
        String date=expiry.getText().toString();item.expiryDate=date.equals("选择到期日")?"":date;
        if(!stagedPhotoPath.isEmpty())item.photoPath=stagedPhotoPath;
        try{db.save(item);finish();}catch(Exception ex){new AlertDialog.Builder(this).setMessage("保存失败："+ex.getMessage()).setPositiveButton("知道了",null).show();}
    }
    private String nonempty(String s,String fallback){s=s.trim();return s.isEmpty()?fallback:s;}
    private void confirmDelete(){new AlertDialog.Builder(this).setTitle("删除物品？").setMessage("物品会从清单中隐藏，后续可通过同步日志追踪删除。")
        .setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{db.delete(item.id);finish();}).show();}
    private void pickPhoto(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_PHOTO);}
    private void takePhoto(){
        try{ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"mono_"+System.currentTimeMillis()+".jpg");values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
            pendingCameraUri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
            if(pendingCameraUri==null)throw new IllegalStateException("创建拍照文件失败");
            Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,pendingCameraUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setClipData(android.content.ClipData.newUri(getContentResolver(),"photo",pendingCameraUri));
            if(i.resolveActivity(getPackageManager())==null)throw new IllegalStateException("没有可用相机应用");
            startActivityForResult(i,TAKE_PHOTO);
        }catch(Exception ex){cleanupPending();Toast.makeText(this,"无法启动相机："+ex.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        Uri selected=null;
        if(result==RESULT_OK){if(request==PICK_PHOTO&&data!=null)selected=data.getData();if(request==TAKE_PHOTO)selected=pendingCameraUri;}
        if(selected!=null){try{stagedPhotoPath=PhotoStore.importPhoto(this,selected);PhotoStore.show(photo,stagedPhotoPath);}
            catch(Exception ex){Toast.makeText(this,"图片处理失败："+ex.getMessage(),Toast.LENGTH_LONG).show();}}
        if(request==TAKE_PHOTO)cleanupPending();
    }
    private void cleanupPending(){if(pendingCameraUri!=null){try{getContentResolver().delete(pendingCameraUri,null,null);}catch(Exception ignored){}pendingCameraUri=null;}}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(pendingCameraUri!=null)out.putString("camera_uri",pendingCameraUri.toString());out.putString("staged_photo",stagedPhotoPath);}
}
