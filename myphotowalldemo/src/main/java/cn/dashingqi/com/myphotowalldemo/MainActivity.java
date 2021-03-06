package cn.dashingqi.com.myphotowalldemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.GridView;

public class MainActivity extends AppCompatActivity {

    private MyGridViewAdapter mAdapter;
    private static final String TAG = "MainActivity";
    private GridView mGridView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mGridView = findViewById(R.id.mGridView);
        mAdapter = new MyGridViewAdapter(this, 0, Images.imageThumbUrls, mGridView);
        mGridView.setAdapter(mAdapter);
        final int spacing = getResources().getDimensionPixelSize(R.dimen.grid_view_spacing);
        final int imageThumbnailSize = getResources().getDimensionPixelSize(R.dimen.image_thumbnail_size);

        mGridView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {

                //向下取整
                final int numColumns = (int) Math.floor(mGridView.getWidth() / (imageThumbnailSize + spacing));
                if (numColumns > 0) {
                    int columnWidth = (mGridView.getWidth() / numColumns)
                            - spacing;
                    mAdapter.setItemHeight(columnWidth);
                    mGridView.getViewTreeObserver()
                            .removeGlobalOnLayoutListener(this);
                }
            }
        });


    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume: "+mGridView.getHeight());
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAdapter.flushDiskCache();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAdapter.cancelTask();
    }

}
