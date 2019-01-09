package cn.dashingqi.com.bitmapmemorydemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageView mImageView = findViewById(R.id.mImageView);

        DisplayMetrics dm = getResources().getDisplayMetrics();

        //横向与纵向的密度
        float xdpi = dm.xdpi;
        float ydpi = dm.ydpi;

        //设备密度
        int densityDpi = dm.densityDpi;


        float density = dm.density;

        //屏幕分辨率
        int heightPixels = dm.heightPixels;
        int widthPixels = dm.widthPixels;

        Log.i(TAG, "onCreate: xdpi = " + xdpi);
        Log.i(TAG, "onCreate: ydpi = " + ydpi);
        Log.i(TAG, "onCreate: densityDpi = " + densityDpi);
        Log.i(TAG, "onCreate: density = " + density);
        Log.i(TAG, "onCreate: heightPixels = " + heightPixels);
        Log.i(TAG, "onCreate: widthPixels = " + widthPixels);
    }
}
