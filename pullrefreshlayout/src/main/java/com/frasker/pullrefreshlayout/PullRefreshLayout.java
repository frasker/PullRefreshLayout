/*
 * Copyright (C) 2019 Baidu, Inc. All Rights Reserved.
 */

package com.frasker.pullrefreshlayout;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.NestedScrollingChild2;
import android.support.v4.view.NestedScrollingChildHelper;
import android.support.v4.view.NestedScrollingParent2;
import android.support.v4.view.NestedScrollingParentHelper;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ListViewCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.OverScroller;

/**
 * The EasyRefreshLayout should be used whenever the user can refresh the
 * contents of a view via a vertical swipe gesture. The activity that
 * instantiates this view should add an OnRefreshListener to be notified
 * whenever the swipe to refresh gesture is completed. The SwipeRefreshLayout
 * will notify the listener each and every time the gesture is completed again;
 * the listener is responsible for correctly determining when to actually
 * initiate a refresh of its content. If the listener determines there should
 * not be a refresh, it must call setRefreshing(false) to cancel any visual
 * indication of a refresh. If an activity wishes to show just the progress
 * animation, it should call setRefreshing(true). To disable the gesture and
 * progress animation, call setEnabled(false) on the view.
 * <p>
 * This layout should be made the parent of the view that will be refreshed as a
 * result of the gesture and can only support one direct child. This view will
 * also be made the target of the gesture and will be forced to match both the
 * width and the height supplied in this layout. The SwipeRefreshLayout does not
 * provide accessibility events; instead, a menu item must be provided to allow
 * refresh of the content wherever this gesture is used.
 * </p>
 */
public class PullRefreshLayout extends ViewGroup implements NestedScrollingParent2,
        NestedScrollingChild2 {

    private static final String TAG = PullRefreshLayout.class.getName();
    private static final int MAX_OFFSET_ANIMATION_DURATION = 600; // ms
    private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;
    private static final int INVALID_POINTER = -1;
    private float dragRate = .5f;

    // Default offset in dips from the top of the view to where the progress spinner should stop
    private static final int DEFAULT_MAX_DRAG_DISTANCE = 160;

    private View mTarget; // the target of the gesture
    OnRefreshListener mListener;
    private int mTouchSlop;

    private final NestedScrollingParentHelper mNestedScrollingParentHelper;
    private final NestedScrollingChildHelper mNestedScrollingChildHelper;
    private final int[] mParentScrollConsumed = new int[2];
    private final int[] mParentOffsetInWindow = new int[2];
    private int mLastMotionY;

    private boolean mNestedScrollInProgress;
    private boolean mIsBeingDragged;
    private boolean mIsOverAnimating = false; // 是否执行过渡动画中
    boolean mRefreshing = false; // 是否数据刷新过程中
    private boolean mIsPinContent = false; // 下拉时内容不动模式，原生SwipeRefreshLayout效果
    private int mRefreshSuccessShowDuration = 200; // 刷新成功后展示时间
    private int mRefreshFailureShowDuration = 200; // 刷新失败后展示时间
    private int mHeaderOffset = 0; // 支持头部偏移量
    private int mTotalDragDistance = -1;
    private int mTriggerRefreshDistance = -1; // 触发显示释放刷新的距离
    private int mRefreshingHeight = -1; // 正在刷新时显示的高度
    private VelocityTracker mVelocityTracker;
    private int mCurrentTargetOffsetTop = 0;
    private Runnable mFlingRunnable;
    OverScroller mScroller;
    private State mState = State.NONE;

    private ValueAnimator mOffsetAnimator;

    private int mActivePointerId = INVALID_POINTER;
    private final DecelerateInterpolator mDecelerateInterpolator;
    private static final int[] LAYOUT_ATTRS = new int[]{
            android.R.attr.enabled
    };

    View mHeaderView;

    boolean mNotify;

    private OnChildScrollUpCallback mChildScrollUpCallback;

    public enum State {
        NONE,
        PULL_TO_REFRESH,
        RELEASE_TO_REFRESH,
        REFRESH_RELEASED,
        REFRESHING,
        REFRESH_SUCCESS,
        REFRESH_FAILURE,
    }

    private Animator.AnimatorListener mRefreshListener = new Animator.AnimatorListener() {
        @Override
        public void onAnimationStart(Animator animation) {

        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mIsOverAnimating = false;
            if (mRefreshing) {
                changeState(State.REFRESHING);
                changeOffset();
                if (mNotify) {
                    if (mListener != null) {
                        mListener.onRefresh();
                    }
                }
            } else {
                reset();
            }
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            mIsOverAnimating = false;
        }

        @Override
        public void onAnimationRepeat(Animator animation) {

        }
    };

    void reset() {
        cancelAnimator();
        setTargetOffsetTopAndBottom(-mCurrentTargetOffsetTop);
        changeState(State.NONE);
        changeOffset();
        ((IPullRefreshHeader) mHeaderView).onReset(PullRefreshLayout.this);
        mCurrentTargetOffsetTop = 0;
    }

    private void changeState(State state) {
        if (state != mState) {
            mState = state;
            ((IPullRefreshHeader) mHeaderView).onStateChanged(PullRefreshLayout.this, mState);
        }
    }

    private void changeOffset() {
        if (mHeaderView != null) {
            ((IPullRefreshHeader) mHeaderView).onOffsetTopChanged(PullRefreshLayout.this, mCurrentTargetOffsetTop, (float) mCurrentTargetOffsetTop / mTotalDragDistance, mState);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            reset();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        reset();
    }

    /**
     * Simple constructor to use when creating a SwipeRefreshLayout from code.
     *
     * @param context
     */
    public PullRefreshLayout(@NonNull Context context) {
        this(context, null);
    }

    /**
     * Constructor that is called when inflating SwipeRefreshLayout from XML.
     *
     * @param context
     * @param attrs
     */
    public PullRefreshLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.PullRefreshLayout);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        setWillNotDraw(false);
        mDecelerateInterpolator = new DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR);

        final DisplayMetrics metrics = getResources().getDisplayMetrics();

        setChildrenDrawingOrderEnabled(true);

        dragRate = typedArray.getFloat(R.styleable.PullRefreshLayout_p_dragRate, .5f);

        mTotalDragDistance = (int) typedArray.getDimension(R.styleable.PullRefreshLayout_p_maxDragDistance, (int) (DEFAULT_MAX_DRAG_DISTANCE * metrics.density));
        mTriggerRefreshDistance = (int) typedArray.getDimension(R.styleable.PullRefreshLayout_p_triggerRefreshDistance, mTotalDragDistance * .6f);
        mRefreshSuccessShowDuration = typedArray.getInteger(R.styleable.PullRefreshLayout_p_refreshSuccessShowDuration, 200);
        mRefreshFailureShowDuration = typedArray.getInteger(R.styleable.PullRefreshLayout_p_refreshFailureShowDuration, 200);
        mRefreshingHeight = (int) typedArray.getDimension(R.styleable.PullRefreshLayout_p_refreshingHeight, -1);
        if (mTriggerRefreshDistance > mTotalDragDistance) {
            mTriggerRefreshDistance = mTotalDragDistance;
        }

        mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);

        mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);
        setNestedScrollingEnabled(true);

        final TypedArray a = context.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
        setEnabled(a.getBoolean(0, true));
        a.recycle();
    }

    @Override
    protected void onFinishInflate() {
        int childCount = getChildCount();
        if (childCount > 2)
            throw new IllegalStateException("only support two child");
        else if (childCount == 1) {
            mTarget = getChildAt(0);
        } else if (childCount == 2) {
            if (getChildAt(0) instanceof IPullRefreshHeader) {
                mHeaderView = (View) getChildAt(0);
            }
            mTarget = getChildAt(1);
        }
        if (mHeaderView != null)
            mHeaderView.bringToFront();
        super.onFinishInflate();
    }

    /**
     * Set the listener to be notified when a refresh is triggered via the swipe
     * gesture.
     */
    public void setOnRefreshListener(@Nullable OnRefreshListener listener) {
        mListener = listener;
    }

    /**
     * Notify the widget that refresh state has changed. Do not call this when
     * refresh is triggered by a swipe gesture.
     *
     * @param refreshing Whether or not the view should show refresh progress.
     * @param animating  Whether or not has animate
     */
    public void setRefreshing(boolean refreshing, boolean animating) {
        if (refreshing && !mRefreshing) {
            // show
            mRefreshing = true;
            mNotify = false;
            if (animating) {
                animateOffsetTo(mRefreshingHeight > 0 ? mRefreshingHeight : mHeaderView.getHeight(), 0, mRefreshListener);
            } else {
                animateOffsetWithDuration(mRefreshingHeight > 0 ? mRefreshingHeight : mHeaderView.getHeight(), 0, mRefreshListener);
            }
        } else if (!refreshing && mRefreshing) {
            mNotify = false;
            ensureTarget();
            mRefreshing = false;
            if (animating) {
                animateOffsetTo(0, 0, mRefreshListener);
            } else {
                animateOffsetWithDuration(0, 0, mRefreshListener);
            }
        }
    }

    public void setRefreshComplete(boolean success) {
        if (mRefreshing) {
            mNotify = false;
            ensureTarget();
            mRefreshing = false;
            changeState(success ? State.REFRESH_SUCCESS : State.REFRESH_FAILURE);
            changeOffset();
            removeCallbacks(showAction);
            postDelayed(showAction, success ? mRefreshSuccessShowDuration : mRefreshFailureShowDuration);
        }
    }

    public void setIsPinContent(boolean mIsPinContent) {
        this.mIsPinContent = mIsPinContent;
    }

    public void setRefreshSuccessShowDuration(int mRefreshSuccessShowDuration) {
        this.mRefreshSuccessShowDuration = mRefreshSuccessShowDuration;
    }

    public void setRefreshFailureShowDuration(int mRefreshFailureShowDuration) {
        this.mRefreshFailureShowDuration = mRefreshFailureShowDuration;
    }

    public void setTotalDragDistance(int mTotalDragDistance) {
        this.mTotalDragDistance = mTotalDragDistance;
    }

    public void setTriggerRefreshDistance(int mTriggerRefreshDistance) {
        this.mTriggerRefreshDistance = mTriggerRefreshDistance;
    }

    public void setRefreshingHeight(int mRefreshingHeight) {
        this.mRefreshingHeight = mRefreshingHeight;
    }

    public void setHeaderOffset(int mHeaderOffset) {
        this.mHeaderOffset = mHeaderOffset;
    }

    private Runnable showAction = new Runnable() {
        @Override
        public void run() {
            animateOffsetTo(0, 0, mRefreshListener);
        }
    };

    /**
     * @return Whether the SwipeRefreshWidget is actively showing refresh
     * progress.
     */
    public boolean isRefreshing() {
        return mRefreshing;
    }

    private void ensureTarget() {
        // Don't bother getting the parent height if the parent hasn't been laid
        // out yet.
        if (mTarget == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!child.equals(mHeaderView)) {
                    mTarget = child;
                    break;
                }
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        if (getChildCount() == 0) {
            return;
        }
        if (mTarget == null) {
            ensureTarget();
        }
        if (mTarget == null) {
            return;
        }
        final View child = mTarget;
        final int childLeft = getPaddingLeft();
        final int childTop = getPaddingTop() + (mIsPinContent ? 0 : mCurrentTargetOffsetTop);
        final int childWidth = width - getPaddingLeft() - getPaddingRight();
        final int childHeight = height - getPaddingTop() - getPaddingBottom() - (mIsPinContent ? 0 : mCurrentTargetOffsetTop);

        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);

        if (mHeaderView != null) {
            int headerWidth = mHeaderView.getMeasuredWidth();
            int headerHeight = mHeaderView.getMeasuredHeight();
            int headerTop = getPaddingTop() + mCurrentTargetOffsetTop - mHeaderView.getMeasuredHeight();
            mHeaderView.layout(childLeft,
                    headerTop + mHeaderOffset,
                    childLeft + headerWidth,
                    headerTop + headerHeight + mHeaderOffset);
        }
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mTarget == null) {
            ensureTarget();
        }
        if (mTarget == null) {
            return;
        }
        mTarget.measure(MeasureSpec.makeMeasureSpec(
                getMeasuredWidth() - getPaddingLeft() - getPaddingRight(),
                MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(
                getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY));
        if (mHeaderView != null) {
            final LayoutParams lp = mHeaderView.getLayoutParams();

            final int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec, 0, lp.width);
            final int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                    0, lp.height);
            mHeaderView.measure(childWidthMeasureSpec, childHeightMeasureSpec);
        }
    }

    /**
     * @return Whether it is possible for the child view of this layout to
     * scroll up. Override this if the child view is a custom view.
     * return false  代表不能下拉了
     * return true 还可以向下滑
     */
    public boolean canChildScrollUp() {
        if (mChildScrollUpCallback != null) {
            return mChildScrollUpCallback.canChildScrollUp(this, mTarget);
        }
        if (mTarget instanceof ListView) {
            return ListViewCompat.canScrollList((ListView) mTarget, -1);
        }
        return mTarget.canScrollVertically(-1);
    }

    /**
     * Set a callback to override {@link android.support.v4.widget.SwipeRefreshLayout#canChildScrollUp()} method. Non-null
     * callback will return the value provided by the callback and ignore all internal logic.
     *
     * @param callback Callback that should be called when canChildScrollUp() is called.
     */
    public void setOnChildScrollUpCallback(@Nullable OnChildScrollUpCallback callback) {
        mChildScrollUpCallback = callback;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        ensureTarget();

        final int action = ev.getActionMasked();
        int pointerIndex;

        if (!isEnabled() || canChildScrollUp() || mIsOverAnimating
                || mNestedScrollInProgress) {
            // Fail fast if we're not in a state where a swipe is possible
            return false;
        }

        // Shortcut since we're being dragged
        if (action == MotionEvent.ACTION_MOVE && mIsBeingDragged) {
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                mIsBeingDragged = false;
                final int y = (int) ev.getY();
                if (!canChildScrollUp()) {
                    mLastMotionY = y;
                    mActivePointerId = ev.getPointerId(0);
                    ensureVelocityTracker();
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    // If we don't have a valid id, the touch down wasn't on content.
                    break;
                }
                pointerIndex = ev.findPointerIndex(activePointerId);
                if (pointerIndex == -1) {
                    break;
                }

                final int y = (int) ev.getY(pointerIndex);
                final int yDiff = Math.abs(y - mLastMotionY);
                if (yDiff > mTouchSlop) {
                    mIsBeingDragged = true;
                    mLastMotionY = y;
                }
                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
        }

        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(ev);
        }

        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();

        if (!isEnabled() || canChildScrollUp() || mIsOverAnimating
                || mNestedScrollInProgress) {
            // Fail fast if we're not in a state where a swipe is possible
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                final int x = (int) ev.getX();
                final int y = (int) ev.getY();
                if (!canChildScrollUp()) {
                    mLastMotionY = y;
                    mActivePointerId = ev.getPointerId(0);
                    ensureVelocityTracker();
                } else {
                    return false;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                if (activePointerIndex == -1) {
                    return false;
                }

                final int y = (int) ev.getY(activePointerIndex);
                int dy = y - mLastMotionY;

                if (!mIsBeingDragged && Math.abs(dy) > mTouchSlop) {
                    mIsBeingDragged = true;
                    if (dy > 0) {
                        dy += mTouchSlop;
                    } else {
                        dy -= mTouchSlop;
                    }
                }

                if (mIsBeingDragged) {
                    mLastMotionY = y;
                    dy = calculateOffsetByDragRate(dy);
                    if (mCurrentTargetOffsetTop + dy < 0) {
                        dy = -mCurrentTargetOffsetTop;
                    }

                    if (mCurrentTargetOffsetTop + dy > mTotalDragDistance) {
                        dy = mTotalDragDistance - mCurrentTargetOffsetTop;
                    }
                    moveTarget(dy);
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int pointerIndex = ev.getActionIndex();
                if (pointerIndex < 0) {
                    Log.e(TAG,
                            "Got ACTION_POINTER_DOWN event but have an invalid action index.");
                    return false;
                }
                mActivePointerId = ev.getPointerId(pointerIndex);
                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP: {
                if (mVelocityTracker != null) {
                    mVelocityTracker.addMovement(ev);
                    mVelocityTracker.computeCurrentVelocity(1000);
                    float yvel = mVelocityTracker.getYVelocity(mActivePointerId);
                    if (mHeaderView != null) {
                        fling(-mHeaderView.getHeight(), 0, yvel);
                    }
                }
            }
            case MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                return false;
        }

        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(ev);
        }

        return true;
    }

    private void ensureVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    final boolean fling(int minOffset,
                        int maxOffset, float velocityY) {
        if (mFlingRunnable != null) {
            mHeaderView.removeCallbacks(mFlingRunnable);
            mFlingRunnable = null;
        }

        if (mScroller == null) {
            mScroller = new OverScroller(mHeaderView.getContext());
        }

        mScroller.fling(
                0, 0, // curr
                0, Math.round(velocityY), // velocity.
                0, 0, // x
                minOffset, maxOffset); // y

        if (mScroller.computeScrollOffset()) {
            mFlingRunnable = new FlingRunnable(mHeaderView);
            ViewCompat.postOnAnimation(mHeaderView, mFlingRunnable);
            return true;
        } else {
            onFlingFinished(mHeaderView);
            return false;
        }
    }

    private class FlingRunnable implements Runnable {
        private final View mLayout;

        FlingRunnable(View layout) {
            mLayout = layout;
        }

        @Override
        public void run() {
            if (mLayout != null && mScroller != null) {
                if (mScroller.computeScrollOffset()) {
                    setTargetOffsetTopAndBottom(mScroller.getCurrY());
                    // Post ourselves so that we run on the next animation
                    ViewCompat.postOnAnimation(mLayout, this);
                } else {
                    onFlingFinished(mLayout);
                }
            }
        }
    }

    private void onFlingFinished(View mLayout) {
        finishSpinner(mCurrentTargetOffsetTop);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean b) {
        // if this is a List < L or another view that doesn't support nested
        // scrolling, ignore this request so that the vertical scroll event
        // isn't stolen
        if ((android.os.Build.VERSION.SDK_INT < 21 && mTarget instanceof AbsListView)
                || (mTarget != null && !ViewCompat.isNestedScrollingEnabled(mTarget))) {
            // Nope.
        } else {
            super.requestDisallowInterceptTouchEvent(b);
        }
    }

    // NestedScrollingParent

    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        return onStartNestedScroll(child, target, nestedScrollAxes, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int nestedScrollAxes, int type) {
        boolean start = isEnabled() && (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
        if (start) {
            cancelAnimator();
        }
        return start;
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int axes) {
        onNestedScrollAccepted(child, target, axes, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes, int type) {
        // Reset the counter of how much leftover scroll needs to be consumed.
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
        // Dispatch up to the nested parent
        startNestedScroll(axes & ViewCompat.SCROLL_AXIS_VERTICAL, type);
        mNestedScrollInProgress = true;
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        onNestedPreScroll(target, dx, dy, consumed, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @Nullable int[] consumed, int type) {
        if (dy > 0 && mCurrentTargetOffsetTop > 0) {
            int offset = 0;
            if (dy > mCurrentTargetOffsetTop) {
                // 上滑并且滑动距离大于头部露出距离
                if (consumed != null) {
                    consumed[1] = dy - (int) mCurrentTargetOffsetTop;
                }
                offset = -mCurrentTargetOffsetTop;
            } else {
                offset -= dy;
                if (consumed != null) {
                    consumed[1] = dy;
                }
            }
            moveTarget(offset);
        }
        // Now let our nested parent consume the leftovers
        final int[] parentConsumed = mParentScrollConsumed;

        if (consumed != null && dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], parentConsumed, null, type)) {
            consumed[0] += parentConsumed[0];
            consumed[1] += parentConsumed[1];
        }
    }

    @Override
    public int getNestedScrollAxes() {
        return mNestedScrollingParentHelper.getNestedScrollAxes();
    }

    @Override
    public void onStopNestedScroll(View target) {
        onStopNestedScroll(target, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public void onStopNestedScroll(@NonNull View target, int type) {
        mNestedScrollingParentHelper.onStopNestedScroll(target, type);
        mNestedScrollInProgress = false;
        // Finish the spinner for nested scrolling if we ever consumed any
        // unconsumed nested scroll
        if (mCurrentTargetOffsetTop > 0) {
            finishSpinner(mCurrentTargetOffsetTop);
        }
        // Dispatch up our nested parent
        stopNestedScroll(type);
    }

    @Override
    public void onNestedScroll(final View target, final int dxConsumed, final int dyConsumed,
                               final int dxUnconsumed, final int dyUnconsumed) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {
        // Dispatch up to the nested parent first
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
                mParentOffsetInWindow, type);

        // This is a bit of a hack. Nested scrolling works from the bottom up, and as we are
        // sometimes between two nested scrolling views, we need a way to be able to know when any
        // nested scrolling parent has stopped handling events. We do that by using the
        // 'offset in window 'functionality to see if we have been moved from the event.
        // This is a decent indication of whether we should take over the event stream or not.
        int dy = dyUnconsumed + mParentOffsetInWindow[1];
        if (type == ViewCompat.TYPE_TOUCH) {
            // 处理下拉过程
            if (dy < 0 && !canChildScrollUp() && mCurrentTargetOffsetTop < mTotalDragDistance) { // 列表不能下拉了
                int offset = calculateOffsetByDragRate(-dy);
                if (mCurrentTargetOffsetTop + offset > mTotalDragDistance) {
                    offset = mTotalDragDistance - mCurrentTargetOffsetTop;
                }
                moveTarget(offset);
            }
        }
    }

    protected int calculateOffsetByDragRate(int dy) {

        float downResistance;

        if (dy < 0) {
            downResistance = dragRate;
            return (int) (dy * downResistance);
        } else {
            final float overscrollTop = mCurrentTargetOffsetTop;
            float originalDragPercent = overscrollTop / mTotalDragDistance;
            float dragPercent = Math.min(1f, Math.abs(originalDragPercent));
            downResistance = 1 - dragPercent;
        }

        return (int) Math.max(dy * downResistance, 1);
    }

    // NestedScrollingChild

    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mNestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mNestedScrollingChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return startNestedScroll(axes, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean startNestedScroll(int axes, int type) {
        return mNestedScrollingChildHelper.startNestedScroll(axes, type);
    }


    @Override
    public void stopNestedScroll() {
        stopNestedScroll(ViewCompat.TYPE_TOUCH);
    }


    @Override
    public void stopNestedScroll(int type) {
        mNestedScrollingChildHelper.stopNestedScroll(type);
    }


    @Override
    public boolean hasNestedScrollingParent() {
        return hasNestedScrollingParent(ViewCompat.TYPE_TOUCH);
    }


    @Override
    public boolean hasNestedScrollingParent(int type) {
        return mNestedScrollingChildHelper.hasNestedScrollingParent(type);
    }


    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                        int dyUnconsumed, int[] offsetInWindow) {
        return dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, @Nullable int[] offsetInWindow, int type) {
        return mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed, offsetInWindow, type);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, @Nullable int[] consumed, @Nullable int[] offsetInWindow, int type) {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(
                dx, dy, consumed, offsetInWindow, type);
    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX,
                                    float velocityY) {
        return dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY,
                                 boolean consumed) {
        return dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }

    // 移动偏移量 offset
    private void moveTarget(int offset) {
        if (!mRefreshing && mCurrentTargetOffsetTop == 0 && offset > 0) {
            if (mHeaderView != null) {
                ((IPullRefreshHeader) mHeaderView).onReady(this);
            }
        }
        setTargetOffsetTopAndBottom(offset);
    }

    private void finishSpinner(int overscrollTop) {
        if (overscrollTop > mTriggerRefreshDistance) {
            if (!mRefreshing) {
                mNotify = true;
                ensureTarget();
                mRefreshing = true;
                changeState(State.REFRESH_RELEASED);
                changeOffset();
                animateOffsetTo(mRefreshingHeight > 0 ? mRefreshingHeight : mHeaderView.getHeight(), 0, mRefreshListener);
            } else {
                animateOffsetTo(mRefreshingHeight > 0 ? mRefreshingHeight : mHeaderView.getHeight(), 0, null);
            }
        } else {
            if (!mRefreshing) {
                animateOffsetTo(0, 0, mRefreshListener);
            }
        }
    }

    private void cancelAnimator() {
        if (mOffsetAnimator != null && mOffsetAnimator.isRunning()) {
            mOffsetAnimator.cancel();
        }
    }

    private void animateOffsetTo(final int target, float velocity, Animator.AnimatorListener listener) {
        final int distance = Math.abs(target - mCurrentTargetOffsetTop);

        final int duration;
        velocity = Math.abs(velocity);
        if (velocity > 0) {
            duration = 3 * Math.round(1000 * (distance / velocity));
        } else {
            final float distanceRatio = (float) distance / mHeaderView.getHeight();
            duration = (int) ((distanceRatio + 1) * 150);
        }

        animateOffsetWithDuration(target, duration, listener);
    }


    private void animateOffsetWithDuration(int target, int duration, Animator.AnimatorListener listener) {
        mIsOverAnimating = true;
        if (mCurrentTargetOffsetTop == target) {
            if (mOffsetAnimator != null && mOffsetAnimator.isRunning()) {
                // 直接回调动画结束
                if (listener != null) {
                    listener.onAnimationEnd(mOffsetAnimator);
                }
                mOffsetAnimator.cancel();
            }
            return;
        }

        if (mOffsetAnimator == null) {
            mOffsetAnimator = new ValueAnimator();
            mOffsetAnimator.setInterpolator(mDecelerateInterpolator);
            mOffsetAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int value = (int) animation.getAnimatedValue();
                    moveTarget(value - mCurrentTargetOffsetTop);
                }
            });
        } else {
            mOffsetAnimator.cancel();
        }
        if (listener != null) {
            mOffsetAnimator.removeAllListeners();
            mOffsetAnimator.addListener(listener);
        }

        mOffsetAnimator.setDuration(Math.min(duration, MAX_OFFSET_ANIMATION_DURATION));
        mOffsetAnimator.setIntValues(mCurrentTargetOffsetTop, target);
        mOffsetAnimator.start();
    }

    void setTargetOffsetTopAndBottom(int offset) {
        mHeaderView.bringToFront();
        ViewCompat.offsetTopAndBottom(mHeaderView, offset);
        ViewCompat.offsetTopAndBottom(mTarget, offset);
        mCurrentTargetOffsetTop += offset;
        if (!mRefreshing) {
            State newState;
            if (mCurrentTargetOffsetTop < mTriggerRefreshDistance) {
                newState = State.PULL_TO_REFRESH;
            } else {
                newState = State.RELEASE_TO_REFRESH;
            }
            changeState(newState);
        }
        changeOffset();
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = ev.getActionIndex();
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    /**
     * Classes that wish to be notified when the swipe gesture correctly
     * triggers a refresh should implement this interface.
     */
    public interface OnRefreshListener {
        /**
         * Called when a swipe gesture triggers a refresh.
         */
        void onRefresh();
    }

    /**
     * Classes that wish to override {@link android.support.v4.widget.SwipeRefreshLayout#canChildScrollUp()} method
     * behavior should implement this interface.
     */
    public interface OnChildScrollUpCallback {
        /**
         * Callback that will be called when {@link android.support.v4.widget.SwipeRefreshLayout#canChildScrollUp()} method
         * is called to allow the implementer to override its behavior.
         *
         * @param parent SwipeRefreshLayout that this callback is overriding.
         * @param child  The child view of SwipeRefreshLayout.
         * @return Whether it is possible for the child view of parent layout to scroll up.
         */
        boolean canChildScrollUp(@NonNull PullRefreshLayout parent, @Nullable View child);
    }
}