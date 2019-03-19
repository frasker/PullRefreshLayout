/*
 * Copyright (C) 2019 Baidu, Inc. All Rights Reserved.
 */

package com.frasker.pullrefreshlayout;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * author: created by lvmo on 2019/3/4
 * email: lvmo@baidu.com
 */
public abstract class RefreshHeader extends FrameLayout implements IPullRefreshHeader {
    private static final String TAG = "RefreshHeader";

    public RefreshHeader(Context context) {
        super(context);
    }

    public RefreshHeader(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }


}
