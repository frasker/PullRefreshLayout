/*
 * Copyright (C) 2019 Baidu, Inc. All Rights Reserved.
 */

package com.frasker.example;

import android.graphics.Color;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.frasker.pullrefreshlayout.PullRefreshLayout;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private String[] city = {"广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津"};
    private PullRefreshLayout lvRefreshLayout;
    private RecyclerView recyclerView;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        handler = new Handler(getMainLooper());
        lvRefreshLayout = findViewById(R.id.lv);
        //lvRefreshLayout.setIsPinContent(true);
        recyclerView = findViewById(R.id.rList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new MyAdapter(city));

        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss:SSS");
        lvRefreshLayout.setOnRefreshListener(new PullRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Log.i(TAG, "onRefresh: "+simpleDateFormat.format(new Date(System.currentTimeMillis())));
                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, "setRefreshComplete: "+simpleDateFormat.format(new Date(System.currentTimeMillis())));
                        lvRefreshLayout.setRefreshComplete(true);
                    }
                }, 2000);
            }
        });
    }

    private static class MyAdapter extends RecyclerView.Adapter<MyAdapter.MyHolder> {

        private String[] city;

        public MyAdapter(String[] city) {
            this.city = city;
        }

        @NonNull
        @Override
        public MyHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View inflate = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            inflate.setBackgroundColor(Color.LTGRAY);
            return new MyHolder(inflate);
        }

        @Override
        public void onBindViewHolder(@NonNull MyHolder holder, int position) {
            holder.textView.setText(city[position]);
        }

        @Override
        public int getItemCount() {
            return city.length;
        }

        public static class MyHolder extends RecyclerView.ViewHolder {
            TextView textView;

            public MyHolder(View itemView) {
                super(itemView);
                textView = itemView.findViewById(android.R.id.text1);
            }
        }
    }
}
