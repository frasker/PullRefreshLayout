# PullRefreshLayout
轻量级支持下拉刷新的控件,思想来自于SwipeRefreshLayout
## 使用方式
PullRefreshLayout提供IPullRefreshHeader来回调下滑状态，header需要实现这个接口来接收下滑状态
```
public interface IPullRefreshHeader {
    // 准备开始下拉前的回调，可以做一些准备操作
    void onReady(PullRefreshLayout refreshLayout);
    // 刷新结束后回调，释放资源
    void onReset(PullRefreshLayout refreshLayout);
    // 下滑距离变化的回调，可以根据offset 或者 progress 处理展示行为
    void onOffsetTopChanged(PullRefreshLayout refreshLayout, int offset, float progress, PullRefreshLayout.State state);
    // PullRefreshLayout 定义了几种刷新状态，当状态改变时回调
    void onStateChanged(PullRefreshLayout refreshLayout,PullRefreshLayout.State newState);
}
```
header 设置后直接在xml引用
```
<com.frasker.pullrefreshlayout.PullRefreshLayout
        android:id="@+id/lv"
        android:layout_width="match_parent"
        android:layout_height="match_parent">
        <com.frasker.example.MyHeader
            android:layout_width="match_parent"
            android:layout_height="60dp"/>
        <android.support.v7.widget.RecyclerView
            android:id="@+id/rList"
            android:layout_width="match_parent"
            android:layout_height="match_parent"/>
    </com.frasker.pullrefreshlayout.PullRefreshLayout>
```
## 刷新回调
需要为PullRefreshLayout设置listener来监听刷新状态
```
pullRefreshLayout.setOnRefreshListener(new PullRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
               ... // 处理网络请求
               if(刷新完成)
                   pullRefreshLayout.setRefreshComplete(true);
            }
        });
```
## PIN模式
PullRefreshLayout支持SwipeRefreshLayout内容固定的模式
```
pullRefreshLayout.setIsPinContent(true);
```
## 支持View类型
PullRefreshLayout支持任何类型的View，内部实现了嵌套滑动和触摸事件两种实现机制
## 支持配置属性
```
app:p_maxDragDistance // 最大拖拽距离 默认160dp
app:p_refreshingHeight // 刷新时展示高度 默认Header高度
app:p_triggerRefreshDistance // 下拉出发刷新的距离，默认0.6最大拖拽距离
app:p_refreshSuccessShowDuration // 刷新成功后展示时间
app:p_refreshFailureShowDuration // 刷新失败后展示时间
app:p_dragRate // 拖拽阻尼比
```
