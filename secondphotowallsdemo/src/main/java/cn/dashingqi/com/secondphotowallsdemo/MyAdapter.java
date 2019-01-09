package cn.dashingqi.com.secondphotowallsdemo;

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
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;

/**
 * <p>文件描述：<p>
 * <p>作者：北京车车网络技术有限公司<p>
 * <p>创建时间：2019/1/9<p>
 * <p>更改时间：2019/1/9<p>
 * <p>版本号：1<p>
 */
public class MyAdapter extends ArrayAdapter<String> {

    private GridView mGridView;
    HashSet<WorkTask> collectionTask;
    private final LruCache<String, Bitmap> mLruCache;
    private DiskLruCache mDiskLruCache;
    private int itemHeight;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private HttpURLConnection urlConnection;


    public MyAdapter(@NonNull Context context, int resource, @NonNull String[] objects, GridView mGridView) {
        super(context, resource, objects);
        mGridView = mGridView;
        collectionTask = new HashSet<>();
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int memoryLruCache = maxMemory / 8;

        mLruCache = new LruCache<String, Bitmap>(memoryLruCache) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };

        File fileDiskLruCache = getDiskLruCacheFile(getContext(), "bitmap");
        if (!fileDiskLruCache.exists()) {
            fileDiskLruCache.mkdirs();
        }

        try {
            mDiskLruCache = DiskLruCache.open(fileDiskLruCache, getAppVersion(getContext()), 1, 10 * 1024 * 1024);
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
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.grid_view_layout, parent, false);
        } else {
            view = convertView;
        }

        ImageView mImageView = view.findViewById(R.id.mImageView);
        if (mImageView.getLayoutParams().height != itemHeight) {
            mImageView.getLayoutParams().height = itemHeight;
        }

        mImageView.setTag(imageUrl);
        mImageView.setImageResource(R.mipmap.ic_launcher);
        loadBitmaps(imageUrl, mImageView);
        return view;
    }

    private void loadBitmaps(String imageUrl, ImageView mImageView) {

        //从内存中获取数据
        Bitmap mBitmap = getBitmapFromLruCache(imageUrl);
        if (mBitmap == null) {
            //内存缓存中没有，开启异步任务先去磁盘中找，没有的话再去网络中下载
            WorkTask workTask = new WorkTask();
            collectionTask.add(workTask);
            workTask.execute(imageUrl);
        } else {
            if (mImageView != null) {
                mImageView.setImageBitmap(mBitmap);
            }
        }

    }


    private Bitmap getBitmapFromLruCache(String imageUrl) {
        return mLruCache.get(imageUrl);
    }

    public void addBitmapToLruCache(Bitmap bitmap, String imageUrl) {
        if (getBitmapFromLruCache(imageUrl) == null) {
            mLruCache.put(imageUrl, bitmap);
        }

    }

    private File getDiskLruCacheFile(Context context, String name) {
        String cachePath;
        if (!Environment.isExternalStorageRemovable() || Environment.MEDIA_MOUNTED.equalsIgnoreCase(Environment.getExternalStorageState())) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }

        return new File(cachePath + File.separator + name);
    }

    private int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return 1;
    }


    class WorkTask extends AsyncTask<String, Void, Bitmap> {

        private FileDescriptor fileDescriptor;
        private Bitmap mBitmap;
        private FileInputStream inputStream;
        private String imageUrl;

        @Override
        protected Bitmap doInBackground(String... args) {
            imageUrl = args[0];
            //先从磁盘中找下
            //MD5加密的路径
            String key = hashKeyFromMd5(imageUrl);
            try {
                DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
                if (snapshot == null) {
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
                    //将bitmap加入到缓存内存中
                    addBitmapToLruCache(mBitmap, imageUrl);

                }


            } catch (IOException e) {
                e.printStackTrace();
            }

            return mBitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            ImageView mImageView = mGridView.findViewWithTag(imageUrl);
            if (bitmap != null && mImageView != null) {
                mImageView.setImageBitmap(bitmap);
            }

            collectionTask.remove(this);
        }
    }

    public String hashKeyFromMd5(String imageUrl) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(imageUrl.getBytes());
            String md5Key = hexKeyToString(messageDigest.digest());
            return md5Key;
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(imageUrl.hashCode());
        }
    }

    private String hexKeyToString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            sb.append(hex);
        }

        return sb.toString();

    }


    public boolean downloadUrlToStream(String imageUrl, OutputStream outputStream) {
        try {
            URL url = new URL(imageUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setReadTimeout(5000);
            urlConnection.setConnectTimeout(5000);
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

    public void flushDiakLruCahe() {
        if (mDiskLruCache != null) {
            try {
                mDiskLruCache.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void removeTask() {
        if (collectionTask != null && collectionTask.size() > 0) {
            for (WorkTask workTask : collectionTask) {
                workTask.cancel(false);
            }
        }
    }

    public void setItemHeight(int height){
        if (height == itemHeight)
            return;
        itemHeight = height;
        notifyDataSetChanged();
    }
}
