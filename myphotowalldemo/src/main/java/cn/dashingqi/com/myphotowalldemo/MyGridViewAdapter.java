package cn.dashingqi.com.myphotowalldemo;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

/**
 * <p>文件描述：<p>
 * <p>作者：北京车车网络技术有限公司<p>
 * <p>创建时间：2019/1/7<p>
 * <p>更改时间：2019/1/7<p>
 * <p>版本号：1<p>
 */
public class MyGridViewAdapter extends ArrayAdapter<String> {


    private GridView mGridView;
    private final LruCache<String, Bitmap> mLruCache;
    private HashSet<WorkTask> taskCollections;
    private  DiskLruCache mDiskLruCache;


    public MyGridViewAdapter(@NonNull Context context, int textViewResourceId, @NonNull String[] objects, GridView mGridView) {
        super(context, textViewResourceId, objects);
        this.mGridView = mGridView;

        //获取到应用运行时最大内存
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        //为内存缓存赋值
        int memoryCacheSize = maxMemory / 8;

        //用来保存异步任务
        taskCollections = new HashSet<>();

        mLruCache = new LruCache<String, Bitmap>(memoryCacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes();
            }
        };

        File diskCacheFile = getDiskCacheFile(getContext(), "thumb");
        if (!diskCacheFile.exists()) {
            diskCacheFile.mkdirs();
        }

        try {
            mDiskLruCache = DiskLruCache.open(diskCacheFile, getAppVersion(getContext()), 1, 10 * 1024 * 1024);
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        String url = getItem(position);
        View view = null;
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_grid_view, parent, false);
        } else {
            view = convertView;
        }

        ImageView mImageView = convertView.findViewById(R.id.mImageView);
        //为ImageView 设置唯一的标识。为了防止异步任务加载时出现乱序
        mImageView.setTag(url);
        return view;
    }

    /**
     * 加载图片
     */
    public void loadBitmaps(String url,ImageView mImageView){
        //先从内存缓冲中获取数据
        Bitmap mBitmap = getBitmapFromLruCache(url);
        //缓存中没有数据
        if (mBitmap == null){

        }else{
            if (mBitmap!= null && mImageView != null){
                mImageView.setImageBitmap(mBitmap);
            }
        }


    }

    public Bitmap getBitmapFromLruCache(String key){
        return mLruCache.get(key);
    }

    /**
     * 获取到磁盘缓存的文件
     *
     * @param context
     * @param name
     * @return
     */
    public File getDiskCacheFile(Context context, String name) {
        String cachePath;
        if (!Environment.isExternalStorageRemovable() || Environment.getExternalStorageDirectory() != null) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }

        return new File(cachePath + File.separator + name);
    }

    /**
     * 获取到App的版本号
     *
     * @param context
     * @return
     */
    public int getAppVersion(Context context) {
        PackageInfo packageInfo = null;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return packageInfo.versionCode;
    }


    class WorkTask extends AsyncTask<String, Integer, Bitmap> {

        @Override
        protected Bitmap doInBackground(String... strings) {
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
        }
    }
}
