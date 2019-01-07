package cn.dashingqi.com.photowalldemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.ViewTreeObserver;
import android.widget.GridView;

public class MainActivity extends AppCompatActivity {

    private int mImageThumbSizeSpacing;
    private int mImageThumbSize;
    private GridView mPhotoWall;
    private PhotoWallAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImageThumbSizeSpacing = getResources().getDimensionPixelSize(R.dimen.image_thumbnail_spacing);
        mImageThumbSize = getResources().getDimensionPixelSize(R.dimen.image_thumbnail_size);
        mPhotoWall = findViewById(R.id.mGridView);
        mAdapter = new PhotoWallAdapter(this, 0, Images.imageThumbUrls, mPhotoWall);
        mPhotoWall.setAdapter(mAdapter);
        mPhotoWall.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                final int numColumns = (int) Math.floor(mPhotoWall
                        .getWidth()
                        / (mImageThumbSize + mImageThumbSizeSpacing));
                if (numColumns > 0) {
                    int columnWidth = (mPhotoWall.getWidth() / numColumns)
                            - mImageThumbSizeSpacing;
                    mAdapter.setItemHeight(columnWidth);
                    mPhotoWall.getViewTreeObserver()
                            .removeGlobalOnLayoutListener(this);
                }



            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAdapter.flushCache();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //退出程序的时候取消所有的下载
        mAdapter.cancelTask();
    }
}
