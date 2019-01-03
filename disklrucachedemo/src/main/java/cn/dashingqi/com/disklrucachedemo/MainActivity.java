package cn.dashingqi.com.disklrucachedemo;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MainActivity extends AppCompatActivity {

    private DiskLruCache diskLruCache;
    private HttpURLConnection urlConnection;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private Button btnGetCache;
    private ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();

        //打开缓存
        try {
            diskLruCache = DiskLruCache.open(getDiskCacheDir(this, "bitmap"), getAppVersion(this), 1, 1024 * 1024 * 10);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //写入缓存
        writeToCache();

        btnGetCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getCache();
            }
        });

    }

    private void initView() {
        btnGetCache = findViewById(R.id.btn_get_cache);
        mImageView = findViewById(R.id.mImageView);
    }

    /**
     * 获取到缓存的路径
     *
     * @param context    上线文环境
     * @param uniqueName 不同类型文件的区分
     * @return
     */
    public File getDiskCacheDir(Context context, String uniqueName) {
        String cachePath;
        //当SD卡存在或者不可被移除的时候，就获取到 /sdcard/Android/data/<application package> /cache 这个路径。
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ||
                !Environment.isExternalStorageRemovable()) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            //否则就获取到 /data/data/<application package>/cache这个路径
            cachePath = context.getCacheDir().getPath();
        }

        return new File(cachePath + File.separator + uniqueName);
    }

    /**
     * 获取到App的版本号 每当换版本号的时候会将之前的缓存清除掉，需要重新从网络中获取
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
     * 下载一张图片
     *
     * @param urlString
     * @param outputStream
     * @return
     */
    private boolean downloadUrlToStream(String urlString, OutputStream outputStream) {

        try {
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setConnectTimeout(5000);
            urlConnection.setReadTimeout(5000);
            int responseCode = urlConnection.getResponseCode();
            if (responseCode == 200) {
                in = new BufferedInputStream(urlConnection.getInputStream(), 8 * 1024);

                out = new BufferedOutputStream(outputStream, 8 * 1024);
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

                if (out != null) {
                    out.close();
                }

                if (in != null) {
                    in.close();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        return false;
    }

    /**
     * 将字符串转化成MD5值
     *
     * @param key
     * @return
     */
    public String hashKeyForDisk(String key) {
        String cacheKey;
        try {
            MessageDigest mDigest = MessageDigest.getInstance("MD5");
            cacheKey = bytesToHexString(mDigest.digest());

        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }

        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    private void writeToCache() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String imageUrl = "https://img-my.csdn.net/uploads/201309/01/1378037235_7476.jpg";
                    String key = hashKeyForDisk(imageUrl);
                    DiskLruCache.Editor edit = diskLruCache.edit(key);
                    OutputStream outputStream = edit.newOutputStream(0);
                    if (downloadUrlToStream(imageUrl, outputStream)) {
                        //提交本次写入
                        edit.commit();
                    } else {
                        //终止本次写入
                        edit.abort();
                    }


                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * 获取到缓存
     */
    private void getCache() {
        String imageUrl = "https://img-my.csdn.net/uploads/201309/01/1378037235_7476.jpg";
        String key = hashKeyForDisk(imageUrl);
        try {
            DiskLruCache.Snapshot snapshot = diskLruCache.get(key);
            InputStream inputStream = snapshot.getInputStream(0);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            mImageView.setImageBitmap(bitmap);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 移除对应标识的缓存
     */
    private void deleteCache(){
        try{
            String imageUrl = "https://img-my.csdn.net/uploads/201309/01/1378037235_7476.jpg";
            String key = hashKeyForDisk(imageUrl);
            diskLruCache.remove(key);

        }catch(IOException e){

        }
    }
}
