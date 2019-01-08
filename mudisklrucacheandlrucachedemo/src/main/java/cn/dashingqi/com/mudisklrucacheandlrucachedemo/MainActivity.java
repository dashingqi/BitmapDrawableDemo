package cn.dashingqi.com.mudisklrucacheandlrucachedemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.GridView;

public class MainActivity extends AppCompatActivity {

    private GridView mGridView;
    private MYAdapter myAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final int spacing = getResources().getDimensionPixelSize(R.dimen.grid_view_spacing);
        final int size = getResources().getDimensionPixelSize(R.dimen.grid_view_size);
        mGridView = findViewById(R.id.mGridView);
        myAdapter = new MYAdapter(MainActivity.this, 0, Images.imageThumbUrls, mGridView);
        mGridView.setAdapter(myAdapter);
        mGridView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {

                final int numColumns = (int) Math.floor(mGridView
                        .getWidth()
                        / (size + spacing));
                if (numColumns > 0) {
                    int columnWidth = (mGridView.getWidth() / numColumns)
                            - spacing;
                    myAdapter.setItemHeight(columnWidth);
                    mGridView.getViewTreeObserver()
                            .removeGlobalOnLayoutListener(this);
                }

            }
        });

    }

    @Override
    protected void onPause() {
        super.onPause();
        myAdapter.flushDiskLruCache();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        myAdapter.cancelTask();

    }
}
