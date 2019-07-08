package com.frasker.pullrefreshlayout;

/**
 * author: created by lvmo on 2019/3/14
 */
public interface IPullRefreshHeader {

    void onReady(PullRefreshLayout refreshLayout);

    void onReset(PullRefreshLayout refreshLayout);

    void onOffsetTopChanged(PullRefreshLayout refreshLayout, int offset, float progress, PullRefreshLayout.State state);

    void onStateChanged(PullRefreshLayout refreshLayout,PullRefreshLayout.State newState);
}
