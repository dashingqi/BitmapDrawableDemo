package cn.dashingqi.com.mudisklrucacheandlrucachedemo;

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
 * <p>创建时间：2019/1/8<p>
 * <p>更改时间：2019/1/8<p>
 * <p>版本号：1<p>
 */
public class MYAdapter extends ArrayAdapter<String> {

    private final LruCache<String, Bitmap> mLruCache;
    private GridView mGridView;
    private DiskLruCache mDiskLruCache;
    private int itemHeight;
    private HttpURLConnection urlConnection;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private FileDescriptor fileDescriptor;
    private HashSet<WorkTask> workCollections;

    public MYAdapter(@NonNull Context context, int textViewResourceId, @NonNull String[] objects, GridView mGridView) {
        super(context, textViewResourceId, objects);
        this.mGridView = mGridView;

        //获取到程序运行时的最大内存
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        //将内存缓存大小设置为 maxMemory/8
        int lruCacheSize = maxMemory / 8;

        workCollections = new HashSet<>();

        //开启内存缓存
        mLruCache = new LruCache<String, Bitmap>(lruCacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };

        File diskLruCacheFile = getDiskLruCacheFile(getContext(), "bitmap");
        if (!diskLruCacheFile.exists()) {
            diskLruCacheFile.mkdirs();
        }
        try {
            //打开磁盘缓存
            mDiskLruCache = DiskLruCache.open(diskLruCacheFile, getAppVersion(getContext()), 1, 10 * 1024 * 1024);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        String imageUrl = getItem(position);

        View view;
        if (convertView == null) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_grid_item, parent, false);
        } else {
            view = convertView;
        }

        ImageView mImageView = view.findViewById(R.id.mImageView);
        if (mImageView.getLayoutParams().height != itemHeight) {
            mImageView.getLayoutParams().height = itemHeight;
        }
        //将ImageView设置一个标识，为了在异步任务中展示出现乱序
        mImageView.setTag(imageUrl);
        mImageView.setImageResource(R.mipmap.ic_launcher);
        loadBitmaps(imageUrl, mImageView);

        return view;
    }

    private void loadBitmaps(String url, ImageView mImageView) {
        //先从内存缓存中获取信息
        Bitmap mBitmap = getBitmapFromLruCache(url);
        if (mBitmap == null) {
            //先看看磁盘缓冲中有没有
            //需要获取图片路径的MD5key
            //开启异步任务去下载图片
            WorkTask workTask = new WorkTask();
            workCollections.add(workTask);
            workTask.execute(url);
        } else {
            if (mBitmap != null && mImageView != null) {
                mImageView.setImageBitmap(mBitmap);
            }

        }

    }

    class WorkTask extends AsyncTask<String, Void, Bitmap> {

        private String imageUrl;
        private Bitmap mBitmap;
        private FileInputStream inputStream;

        @Override
        protected Bitmap doInBackground(String... args) {
            imageUrl = args[0];
            try {
                String key = hashKeyToDisk(imageUrl);
                //看看磁盘缓存中有没有
                DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
                if (snapshot == null) {
                    //表示没有
                    DiskLruCache.Editor edit = mDiskLruCache.edit(key);
                    if (edit != null) {
                        OutputStream outputStream = edit.newOutputStream(0);
                        if (downloadUrlToStream(imageUrl, outputStream)) {
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
                    mBitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
                }

                if (mBitmap != null) {
                    //将Bitmap 加入到内存中
                    addBitmapToLruCache(mBitmap, imageUrl);
                }

                return mBitmap;
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
            ImageView mImageView = mGridView.findViewWithTag(imageUrl);
            if (mImageView != null && bitmap != null) {
                mImageView.setImageBitmap(bitmap);
            }

            workCollections.remove(this);
        }
    }

    /**
     * 将Bitmap加入到内存缓存中
     *
     * @param mBitmap
     * @param url
     */
    private void addBitmapToLruCache(Bitmap mBitmap, String url) {
        if (getBitmapFromLruCache(url) == null) {
            mLruCache.put(url, mBitmap);
        }
    }

    /**
     * 从网络中下载图片
     *
     * @param imageUrl
     */
    private boolean downloadUrlToStream(String imageUrl, OutputStream outputStream) {
        try {
            URL url = new URL(imageUrl);
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
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
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
     * 图片路径MD5加密
     *
     * @param imageUrl
     * @return
     */
    private String hashKeyToDisk(String imageUrl) {
        String diskMd5Key = null;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(imageUrl.getBytes());
            diskMd5Key = bytesToHexString(messageDigest.digest());


        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(imageUrl.hashCode());
        }

        return diskMd5Key;
    }

    private String bytesToHexString(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            String hex = Integer.toHexString(0xFF & data[i]);
            sb.append(hex);
        }

        return sb.toString();
    }

    /**
     * 从内存缓冲中获取Bitmap
     *
     * @param url
     * @return
     */
    public Bitmap getBitmapFromLruCache(String url) {
        return mLruCache.get(url);
    }

    /**
     * 获取到磁盘缓存的路径
     *
     * @param context
     * @param name
     * @return
     */
    private File getDiskLruCacheFile(Context context, String name) {
        String cachePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || !Environment.isExternalStorageRemovable()) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }

        return new File(cachePath + File.separator + name);
    }

    /**
     * 获取到应用的版本号
     *
     * @param context
     * @return
     */
    private int getAppVersion(Context context) {
        try {
            PackageInfo activityInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return activityInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return 1;
    }

    /**
     * 取消所有的异步下载任务
     */
    public void cancelTask() {
        if (workCollections != null) {
            for (WorkTask workTask : workCollections) {
                workTask.cancel(false);
            }
        }
    }

    /**
     * 设置子View的高度
     *
     * @param height
     */
    public void setItemHeight(int height) {
        if (itemHeight == height)
            return;
        itemHeight = height;
        notifyDataSetChanged();
    }

    /**
     * 将磁盘缓存的记录同步到journal文件中
     */
    public void flushDiskLruCache() {
        if (mDiskLruCache != null) {
            try {
                mDiskLruCache.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
