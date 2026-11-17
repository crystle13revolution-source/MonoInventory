package com.mono.inventory;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.widget.ImageView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

public final class PhotoStore {
    private PhotoStore() {}
    public static String importPhoto(Context context, Uri source) throws Exception {
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
        try(InputStream in=context.getContentResolver().openInputStream(source)){BitmapFactory.decodeStream(in,null,opts);}
        if(opts.outWidth<=0 || opts.outHeight<=0) throw new IllegalArgumentException("不是有效图片");
        int sample=1;while(Math.max(opts.outWidth/sample,opts.outHeight/sample)>1600) sample*=2;
        opts.inJustDecodeBounds=false;opts.inSampleSize=sample;
        Bitmap bmp;
        try(InputStream in=context.getContentResolver().openInputStream(source)){bmp=BitmapFactory.decodeStream(in,null,opts);}
        if(bmp==null) throw new IllegalArgumentException("图片解码失败");
        int rotation=0;
        try(InputStream in=context.getContentResolver().openInputStream(source)) {
            ExifInterface exif=new ExifInterface(in);
            int o=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            if(o==ExifInterface.ORIENTATION_ROTATE_90)rotation=90;
            if(o==ExifInterface.ORIENTATION_ROTATE_180)rotation=180;
            if(o==ExifInterface.ORIENTATION_ROTATE_270)rotation=270;
        } catch(Exception ignored) {}
        if(rotation!=0){Matrix m=new Matrix();m.postRotate(rotation);Bitmap b=Bitmap.createBitmap(bmp,0,0,bmp.getWidth(),bmp.getHeight(),m,true);bmp.recycle();bmp=b;}
        File dir=new File(context.getFilesDir(),"photos");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("无法创建图片目录");
        File dest=new File(dir,UUID.randomUUID()+".jpg");
        try(FileOutputStream out=new FileOutputStream(dest)) {if(!bmp.compress(Bitmap.CompressFormat.JPEG,82,out))throw new IllegalStateException("压缩失败");}
        finally {bmp.recycle();}
        return dest.getAbsolutePath();
    }
    public static void show(ImageView image,String path) {
        if(path==null||path.isEmpty()){image.setImageDrawable(null);return;}
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,o);
        o.inSampleSize=1;while(Math.max(o.outWidth/o.inSampleSize,o.outHeight/o.inSampleSize)>420)o.inSampleSize*=2;
        o.inJustDecodeBounds=false;image.setImageBitmap(BitmapFactory.decodeFile(path,o));
    }
}
