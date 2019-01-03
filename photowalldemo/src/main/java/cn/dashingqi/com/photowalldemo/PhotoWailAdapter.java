package cn.dashingqi.com.photowalldemo;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;

/**
 * <p>文件描述：<p>
 * <p>作者：北京车车网络技术有限公司<p>
 * <p>创建时间：2019/1/3<p>
 * <p>更改时间：2019/1/3<p>
 * <p>版本号：1<p>
 */
public class PhotoWailAdapter extends ArrayAdapter<String> {

    private GridView mPhotoWall;
    private final LruCache<String, Bitmap> mMemoryCache;
    private DiskLruCache mDiskLruCache;

    /**
     * 记录每个子项的高度
     */
    private int mItemHeight = 0;
    private HttpURLConnection urlConnection;
    private BufferedInputStream in;
    private BufferedOutputStream out;

    public PhotoWailAdapter(@NonNull Context context, int resource, int textViewResourceId, @NonNull String[] objects, GridView photoWall) {
        super(context, resource, textViewResourceId, objects);
        mPhotoWall = photoWall;

        //获取到应用程序最大的内存
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        //缓存内存为最大内存的1/8
        int cacheSize = maxMemory / 8;

        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
                return value.getByteCount();
            }
        };

        try {
            File diskCacheFile = getDiskCacheDir(context, "thumb");
            if (!diskCacheFile.exists()) {
                diskCacheFile.mkdirs();
            }
            mDiskLruCache = DiskLruCache.open(diskCacheFile, getAppVersion(context),
                    1, 10 * 1024 * 1024);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        String url = getItem(position);

        View view;
        if (convertView == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.photo_layout, null, false);
        } else {
            view = convertView;
        }

        ImageView mImageView = view.findViewById(R.id.mImageView);
        if (mImageView.getLayoutParams().height != mItemHeight) {
            mImageView.getLayoutParams().height = mItemHeight;
        }

        //给ImageView设置一个Tag，保证异步加载图片时不会乱序
        mImageView.setTag(url);
        mImageView.setImageResource(R.mipmap.ic_launcher);
        loadBitmaps(mImageView, url);
        return view;
    }

    /**
     * 从内存中获取一张图片，如果不存在就返回为null
     *
     * @param key
     * @return
     */
    public Bitmap getBitmapFromMemoryCache(String key) {
        return mMemoryCache.get(key);
    }

    public void loadBitmaps(ImageView imageView, String imageUrl) {
        try {

            Bitmap bitmap = getBitmapFromMemoryCache(imageUrl);
            if (bitmap == null) {
                //就开启异步任务去下载
            } else {
                if (imageView != null && bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 根据传入的uniqueName获取到硬盘缓存的路径地址
     *
     * @param context
     * @param name    用来确认缓存的类别
     * @return
     */
    private File getDiskCacheDir(Context context, String name) {
        String diskCachePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
            diskCachePath = context.getExternalCacheDir().getPath();
        } else {
            diskCachePath = context.getCacheDir().getPath();
        }

        return new File(diskCachePath + File.separator + name);
    }

    /**
     * 获取到应用的版本号
     *
     * @param context
     * @return
     */
    private int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return 1;
    }

    /**
     * 从网络中下载一张图片
     *
     * @param urlString
     */
    private boolean downLoadUrlToStream(String urlString, OutputStream outputStream) {
        try {
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setConnectTimeout(5000);
            urlConnection.setReadTimeout(5000);
            int code = urlConnection.getResponseCode();
            if (code == 200) {
                in = new BufferedInputStream(urlConnection.getInputStream(), 10 * 1024);
                out = new BufferedOutputStream(outputStream, 10 * 1024);
                int b;
                while ((b = in.read()) != -1) {
                    out.write(b);
                }
                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            try {
                if (in != null) {
                    in.close();
                }

                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    /**
     * 使用MD5算法对传入的key进行加密并返回
     *
     * @param key
     * @return
     */
    public String hashKeyForDisk(String key) {
        String cacheKey;
        try {
            MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (Exception e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            sb.append(hex);
        }

        return sb.toString();
    }

    /**
     * 异步下载图片的任务
     */
    class BitmapWorkerTask extends AsyncTask<String,Void,Bitmap>{

        @Override
        protected Bitmap doInBackground(String... params) {

            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
        }
    }
}
