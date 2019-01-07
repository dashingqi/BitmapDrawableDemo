package cn.dashingqi.com.photowalldemo;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;

/**
 * <p>文件描述：<p>
 * <p>作者：北京车车网络技术有限公司<p>
 * <p>创建时间：2019/1/3<p>
 * <p>更改时间：2019/1/3<p>
 * <p>版本号：1<p>
 */
public class PhotoWallAdapter extends ArrayAdapter<String> {

    private GridView mPhotoWall;
    private final LruCache<String, Bitmap> mMemoryCache;
    private DiskLruCache mDiskLruCache;
    private HashSet<BitmapWorkerTask> taskCollection;

    /**
     * 记录每个子项的高度
     */
    private int mItemHeight = 0;
    private HttpURLConnection urlConnection;
    private BufferedInputStream in;
    private BufferedOutputStream out;

    public PhotoWallAdapter(@NonNull Context context, int textViewResourceId, @NonNull String[] objects, GridView photoWall) {
        super(context, textViewResourceId, objects);
        mPhotoWall = photoWall;

        //获取到应用程序最大的内存
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        //缓存内存为最大内存的1/8
        int cacheSize = maxMemory / 8;

        taskCollection = new HashSet<>();

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

    /**
     * 将一张图片存储到LruCache中
     *
     * @param key
     * @param bitmap 这里传入从网络上下载的Bitmap对象
     */
    public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemoryCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    /**
     * 加载图片资源
     *
     * @param imageView
     * @param imageUrl
     */
    public void loadBitmaps(ImageView imageView, String imageUrl) {
        try {

            Bitmap bitmap = getBitmapFromMemoryCache(imageUrl);
            if (bitmap == null) {
                //就开启异步任务去下载
                BitmapWorkerTask task = new BitmapWorkerTask();
                taskCollection.add(task);
                task.execute(imageUrl);

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
     * 获取硬盘缓存的路径
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
     * 设置Item子项的高度
     *
     * @param columnWidth
     */
    public void setItemHeight(int columnWidth) {
        if (columnWidth == mItemHeight)
            return;
        mItemHeight = columnWidth;
        notifyDataSetChanged();

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
     * 取消所有异步下载或者等待的任务
     */
    public void cancelTask() {
        if (taskCollection != null) {
            for (BitmapWorkerTask task : taskCollection) {
                task.cancel(false);

            }
        }
    }

    /**
     * 将缓存记录同步到journal文件中
     */
    public void flushCache() {
        if (mDiskLruCache != null) {
            try {
                mDiskLruCache.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 异步下载图片的任务
     */
    class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {

        private FileInputStream fileInputStream;
        private FileDescriptor fileDescriptor;
        private String imageUrl;

        @Override
        protected Bitmap doInBackground(String... params) {
            imageUrl = params[0];

            try {
                //先从磁盘中查找
                String imageKey = hashKeyForDisk(imageUrl);
                DiskLruCache.Snapshot snapshot = mDiskLruCache.get(imageKey);
                if (snapshot == null) {
                    DiskLruCache.Editor edit = mDiskLruCache.edit(imageKey);
                    if (edit != null) {
                        OutputStream outputStream = edit.newOutputStream(0);
                        if (downLoadUrlToStream(imageUrl, outputStream)) {
                            edit.commit();
                        } else {
                            edit.abort();
                        }
                    }

                    snapshot = mDiskLruCache.get(imageKey);
                }

                if (snapshot != null) {
                    fileInputStream = (FileInputStream) snapshot.getInputStream(0);
                    fileDescriptor = fileInputStream.getFD();
                }

                //将缓存的数据解析成Bitmap
                Bitmap bitmap = null;
                if (fileDescriptor != null) {
                    bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
                }

                if (bitmap != null) {
                    //将Bitmap再次添加到内存当中
                    addBitmapToMemoryCache(params[0], bitmap);
                }

                return bitmap;

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fileDescriptor == null && fileInputStream != null) {
                    try {
                        fileInputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            //根据Tag找到对应的ImageView，将下载好的图片显示出来

            ImageView imageView = mPhotoWall.findViewWithTag(imageUrl);
            if (imageView != null && bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }

            taskCollection.remove(this);

        }
    }
}
