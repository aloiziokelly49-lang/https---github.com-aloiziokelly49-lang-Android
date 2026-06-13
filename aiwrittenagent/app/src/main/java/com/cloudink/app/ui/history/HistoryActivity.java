package com.cloudink.app.ui.history;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.cloudink.app.R;

/** Hosts HistoryFragment. */
public class HistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setSubtitle("存储路径: 内部存储/历史档案室 | 导出: Pictures/CloudInk");
        toolbar.setNavigationOnClickListener(v -> finish());
    }
}
