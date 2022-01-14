package com.yunxian.android.scrolllinearlayout.demo;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yunxian.android.scrolllinearlayout.ScrollLinearLayout;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fillChildren(findViewById(R.id.scroll1));
        fillChildren(findViewById(R.id.scroll2));

    }

    private void fillChildren(ScrollLinearLayout scroll) {
        boolean horizontal = scroll.getOrientation() == LinearLayout.HORIZONTAL;
        int width = horizontal ? 180 : ViewGroup.LayoutParams.MATCH_PARENT;
        int height = horizontal ? ViewGroup.LayoutParams.MATCH_PARENT : 120;
        for (int i = 0; i < 20; i++) {
            TextView child = new TextView(this);
            child.setText("123");
            child.setTextColor(Color.BLACK);
            child.setTextSize(12);
            child.setLayoutParams(new ViewGroup.LayoutParams(width, height));
            scroll.addView(child);
        }
    }

}