package cn.dashingqi.com.myphotowalldemo;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private DiskLruCache mDiskLruCache;
    private int itemHeight;
    private BufferedInputStream bin;
    private BufferedOutputStream bout;
    private HttpURLConnection urlConnection;


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
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_grid_view, parent, false);
        } else {
            view = convertView;
        }

        ImageView mImageView = view.findViewById(R.id.mImageView);
        if (mImageView.getLayoutParams().height != itemHeight) {
            mImageView.getLayoutParams().height = itemHeight;
        }
        //为ImageView 设置唯一的标识。为了防止异步任务加载时出现乱序
        mImageView.setTag(url);
        mImageView.setImageResource(R.mipmap.ic_launcher);
        //去加载图片数据
        loadBitmaps(url, mImageView);
        return view;
    }

    /**
     * 加载图片
     */
    public void loadBitmaps(String url, ImageView mImageView) {
        //先从内存缓冲中获取数据
        Bitmap mBitmap = getBitmapFromLruCache(url);
        //缓存中没有数据
        if (mBitmap == null) {
            //开启异步任务，去下载图片数据
            WorkTask workTask = new WorkTask();
            taskCollections.add(workTask);
            workTask.execute(url);
        } else {
            if (mBitmap != null && mImageView != null) {
                mImageView.setImageBitmap(mBitmap);
            }
        }


    }

    /**
     * 从内存缓存中获取数据
     *
     * @param key
     * @return
     */
    public Bitmap getBitmapFromLruCache(String key) {
        return mLruCache.get(key);
    }

    /**
     * 将解析的Bitmap加入到内存缓存中
     *
     * @param key
     * @param bitmap
     */
    public void addBitMapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromLruCache(key) == null) {
            mLruCache.put(key, bitmap);
        }
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
        if (!Environment.isExternalStorageRemovable() || Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
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

        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 1;
    }

    /**
     * 从网络上下载图片
     *
     * @param imageUrl
     * @param outputStream
     * @return
     */
    public boolean downloadFromUrlToStream(String imageUrl, OutputStream outputStream) {
        try {
            URL url = new URL(imageUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setConnectTimeout(5000);
            urlConnection.setReadTimeout(5000);
            int code = urlConnection.getResponseCode();
            if (code == 200) {
                InputStream in = urlConnection.getInputStream();
                bin = new BufferedInputStream(in, 10 * 1024);
                bout = new BufferedOutputStream(outputStream, 10 * 1024);
                int b;
                while ((b = bin.read()) != -1) {
                    bout.write(b);
                }

                return true;
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            try {
                if (bin != null) {
                    bin.close();
                }

                if (bout != null) {
                    bout.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }


        }

        return false;
    }


    class WorkTask extends AsyncTask<String, Void, Bitmap> {

        private String imageUrl;
        private FileDescriptor fileDescriptor;
        private Bitmap bitmap;
        private FileInputStream inputStream;

        @Override
        protected Bitmap doInBackground(String... args) {
            imageUrl = args[0];
            //先去磁盘中查找一下，没有的话再去网络上下载图片
            //磁盘查找，根据MD5 key 去查找
            String key = hashKeyFromMd5(imageUrl);
            DiskLruCache.Snapshot snapshot = null;
            try {
                snapshot = mDiskLruCache.get(key);
                if (snapshot == null) {
                    DiskLruCache.Editor edit = mDiskLruCache.edit(key);
                    if (edit != null) {
                        OutputStream outputStream = edit.newOutputStream(0);
                        if (downloadFromUrlToStream(imageUrl, outputStream)) {
                            edit.commit();
                        } else {
                            edit.abort();
                        }

                    }
                    snapshot = mDiskLruCache.get(key);
                }


                if (snapshot != null) {
                    inputStream = (FileInputStream) snapshot.getInputStream(0);
                    fileDescriptor = inputStream.getFD();
                }

                if (fileDescriptor != null) {
                    bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
                }

                if (bitmap != null) {
                    //将Bitmap数据加入到内存缓存中
                    addBitMapToMemoryCache(imageUrl, bitmap);
                }

                return bitmap;


            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fileDescriptor == null && inputStream != null) {
                    try {
                        inputStream.close();
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
            //在这里将数据显示出来
            ImageView mImageView = mGridView.findViewWithTag(imageUrl);
            if (mImageView != null && bitmap != null) {
                mImageView.setImageBitmap(bitmap);
            }
            //完成一个异步任务，就将这个异步任务移除
            taskCollections.remove(this);

        }
    }

    public String hashKeyFromMd5(String url) {
        String diskCacheKey = null;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(url.getBytes());
            diskCacheKey = bytesToHexString(messageDigest.digest());

        } catch (NoSuchAlgorithmException e) {
            diskCacheKey = String.valueOf(url.hashCode());
        }

        return diskCacheKey;
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
     * 取消掉所有的异步任务
     */
    public void cancelTask() {
        if (taskCollections != null) {
            for (WorkTask task : taskCollections) {
                task.cancel(false);
            }
        }
    }

    /**
     * 刷新缓存记录到journal文件中
     */
    public void flushDiskCache() {
        if (mDiskLruCache != null) {
            try {
                mDiskLruCache.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 设置每个item的高度
     *
     * @param height
     */
    public void setItemHeight(int height) {
        if (height == itemHeight)
            return;
        itemHeight = height;
        notifyDataSetChanged();
    }
}
