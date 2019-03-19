/*
 * Copyright (C) 2019 Baidu, Inc. All Rights Reserved.
 */

package com.frasker.example;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.frasker.pullrefreshlayout.PullRefreshLayout;
import com.frasker.pullrefreshlayout.RefreshHeader;

/**
 * author: created by lvmo on 2019/3/4
 * email: lvmo@baidu.com
 */
public class MyHeader extends RefreshHeader {
    TextView textView;

    public MyHeader(Context context) {
        super(context);
    }

    public MyHeader(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        textView = new TextView(context);
        textView.setGravity(Gravity.CENTER);
        LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER;
        addView(textView, layoutParams);

        setWillNotDraw(false);
    }


    @Override
    public void onReady(PullRefreshLayout refreshLayout) {

    }

    @Override
    public void onReset() {

    }

    @Override
    public void onOffsetTopChanged(PullRefreshLayout refreshLayout, int offset, float progress, PullRefreshLayout.State state) {
        textView.setText(state.toString());
    }

}
