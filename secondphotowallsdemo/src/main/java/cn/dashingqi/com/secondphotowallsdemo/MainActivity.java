package cn.dashingqi.com.secondphotowallsdemo;

import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.GridView;

public class MainActivity extends AppCompatActivity {

    private GridView mGridView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        final int size = getResources().getDimensionPixelSize(R.dimen.grid_view_size);
        final int spacing = getResources().getDimensionPixelSize(R.dimen.grid_view_spacing);

        final MyAdapter myAdapter = new MyAdapter(this, 0, Images.imageThumbUrls, mGridView);
        mGridView.setAdapter(myAdapter);

        mGridView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void onGlobalLayout() {
                int numColums =
                        (int) Math.floor(mGridView.getWidth() / (size + spacing));
                if (numColums > 0) {
                    int columnWidth =
                            (mGridView.getWidth() / numColums) - spacing;
                    myAdapter.setItemHeight(columnWidth);
                    mGridView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            }
        });
    }

    private void initView() {
        mGridView = findViewById(R.id.mGridView);
    }
}
