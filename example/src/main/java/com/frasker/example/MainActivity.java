/*
 * Copyright (C) 2019 Baidu, Inc. All Rights Reserved.
 */

package com.frasker.example;

import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.frasker.pullrefreshlayout.PullRefreshLayout;

public class MainActivity extends AppCompatActivity {
    private String[] city = {"广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津", "广州", "深圳", "北京", "上海", "香港", "澳门", "天津"};
    PullRefreshLayout lvRefreshLayout;
    RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        lvRefreshLayout = findViewById(R.id.lv);
        //lvRefreshLayout.setIsPinContent(true);
        recyclerView = findViewById(R.id.rList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new MyAdapter(city));

        lvRefreshLayout.setOnRefreshListener(new PullRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                lvRefreshLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        lvRefreshLayout.setRefreshComplete(true);
                        Toast.makeText(MainActivity.this, "刷新完成", Toast.LENGTH_SHORT).show();
                    }
                }, 5000);
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
