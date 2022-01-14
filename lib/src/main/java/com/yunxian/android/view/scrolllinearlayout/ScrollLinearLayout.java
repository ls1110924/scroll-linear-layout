package com.yunxian.android.view.scrolllinearlayout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AnimationUtils;
import android.widget.EdgeEffect;
import android.widget.LinearLayout;
import android.widget.OverScroller;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.InputDeviceCompat;
import androidx.core.view.NestedScrollingChild3;
import androidx.core.view.NestedScrollingChildHelper;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ScrollingView;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityRecordCompat;
import androidx.core.widget.EdgeEffectCompat;

import java.util.List;

/**
 * 支持滚动的线性布局，根据布局方向分别支持对应方向的滚动
 *
 * @author A Shuai
 * @email ls1110924@gmail.com
 * @date 2022/1/14 19:50
 */
public class ScrollLinearLayout extends LinearLayout implements NestedScrollingParent3,
        NestedScrollingChild3, ScrollingView {

    private static final int ANIMATED_SCROLL_GAP = 250;

    private static final float MAX_SCROLL_FACTOR = 0.5f;

    private static final String TAG = ScrollLinearLayout.class.getSimpleName();
    private static final int DEFAULT_SMOOTH_SCROLL_DURATION = 250;

    /**
     * Interface definition for a callback to be invoked when the scroll
     * X or Y positions of a view change.
     *
     * <p>This version of the interface works on all versions of Android, back to API v4.</p>
     *
     * @see #setOnScrollChangeListener(OnScrollChangeListener)
     */
    public interface OnScrollChangeListener {
        /**
         * Called when the scroll position of a view changes.
         *
         * @param v          The view whose scroll position has changed.
         * @param scrollX    Current horizontal scroll origin.
         * @param scrollY    Current vertical scroll origin.
         * @param oldScrollX Previous horizontal scroll origin.
         * @param oldScrollY Previous vertical scroll origin.
         */
        void onScrollChange(ScrollLinearLayout v, int scrollX, int scrollY,
                            int oldScrollX, int oldScrollY);
    }

    private long mLastScroll;

    private final Rect mTempRect = new Rect();
    private OverScroller mScroller;
    private EdgeEffect mEdgeGlowLeft;
    private EdgeEffect mEdgeGlowRight;
    private EdgeEffect mEdgeGlowTop;
    private EdgeEffect mEdgeGlowBottom;


    /**
     * Position of the last motion event.
     */
    private int mLastMotionX;
    /**
     * Position of the last motion event.
     */
    private int mLastMotionY;

    /**
     * True when the layout has changed but the traversal has not come through yet.
     * Ideally the view hierarchy would keep track of this for us.
     */
    private boolean mIsLayoutDirty = true;
    private boolean mIsLaidOut = false;

    /**
     * The child to give focus to in the event that a child has requested focus while the
     * layout is dirty. This prevents the scroll from being wrong if the child has not been
     * laid out before requesting focus.
     */
    private View mChildToScrollTo = null;

    /**
     * True if the user is currently dragging this ScrollView around. This is
     * not the same as 'is being flinged', which can be checked by
     * mScroller.isFinished() (flinging begins when the user lifts his finger).
     */
    private boolean mIsBeingDragged = false;

    /**
     * Determines speed during touch scrolling
     */
    private VelocityTracker mVelocityTracker;

    /**
     * 是否可滚动，默认不开启
     */
    private boolean mScrollable = false;
    /**
     * Whether arrow scrolling is animated.
     * 是否平滑滚动，默认开启
     */
    private boolean mSmoothScrollingEnabled = true;

    private int mTouchSlop;
    private int mMinimumVelocity;
    private int mMaximumVelocity;

    private int mOverscrollDistance;
    private int mOverflingDistance;

    private float mScrollFactor;

    /**
     * ID of the active pointer. This is used to retain consistency during
     * drags/flings if multiple pointers are used.
     */
    private int mActivePointerId = INVALID_POINTER;

    /**
     * Used during scrolling to retrieve the new offset within the window.
     */
    private final int[] mScrollOffset = new int[2];
    private final int[] mScrollConsumed = new int[2];
    private int mNestedXOffset;
    private int mNestedYOffset;

    private int mLastScrollerX;
    private int mLastScrollerY;

    /**
     * Sentinel value for no current active pointer.
     * Used by {@link #mActivePointerId}.
     */
    private static final int INVALID_POINTER = -1;

    private SavedState mSavedState;

    private static final AccessibilityDelegate ACCESSIBILITY_DELEGATE = new AccessibilityDelegate();

    private NestedScrollingParentHelper mParentHelper;
    private NestedScrollingChildHelper mChildHelper;

    private OnScrollChangeListener mOnScrollChangeListener;

    public ScrollLinearLayout(Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    public ScrollLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    public ScrollLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ScrollLinearLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    private void init(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ScrollLinearLayout, defStyleAttr, defStyleRes);
        mScrollable = typedArray.getBoolean(R.styleable.ScrollLinearLayout_scrollable, false);
        mSmoothScrollingEnabled = typedArray.getBoolean(R.styleable.ScrollLinearLayout_smoothScrollingEnabled, true);
        typedArray.recycle();

        initScrollView();

        mParentHelper = new NestedScrollingParentHelper(this);
        mChildHelper = new NestedScrollingChildHelper(this);

        // ...because why else would you be using this widget?
        setNestedScrollingEnabled(true);

        ViewCompat.setAccessibilityDelegate(this, ACCESSIBILITY_DELEGATE);
    }

    private static final SparseIntArray AXES_MAPPER = new SparseIntArray();

    static {
        AXES_MAPPER.put(HORIZONTAL, ViewCompat.SCROLL_AXIS_HORIZONTAL);
        AXES_MAPPER.put(VERTICAL, ViewCompat.SCROLL_AXIS_VERTICAL);
    }

    private int getScrollAxes() {
        return AXES_MAPPER.get(getOrientation(), ViewCompat.SCROLL_AXIS_NONE);
    }

    // NestedScrollingChild

    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return startNestedScroll(axes, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean startNestedScroll(int axes, int type) {
        return mScrollable && mChildHelper.startNestedScroll(axes, type);
    }

    @Override
    public void stopNestedScroll() {
        stopNestedScroll(ViewCompat.TYPE_TOUCH);
    }

    @Override
    public void stopNestedScroll(int type) {
        if (mScrollable) {
            mChildHelper.stopNestedScroll(type);
        }
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return hasNestedScrollingParent(ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean hasNestedScrollingParent(int type) {
        return mScrollable && mChildHelper.hasNestedScrollingParent(type);
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                        int dyUnconsumed, int[] offsetInWindow) {
        return mScrollable && mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                        int dyUnconsumed, int[] offsetInWindow, int type) {
        return mScrollable && mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type);
    }

    @Override
    public void dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed,
                                     @Nullable int[] offsetInWindow, int type, @NonNull int[] consumed) {
        if (mScrollable) {
            mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
                    offsetInWindow, type, consumed);
        }
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow, int type) {
        return mScrollable && mChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mScrollable && mChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mScrollable && mChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }

    // NestedScrollingParent

    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes) {
        return onStartNestedScroll(child, target, axes, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
        return mScrollable && (axes & getScrollAxes()) != 0;
    }

    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int nestedScrollAxes) {
        onNestedScrollAccepted(child, target, nestedScrollAxes, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes, int type) {
        if (mScrollable) {
            mParentHelper.onNestedScrollAccepted(child, target, axes, type);
            startNestedScroll(getScrollAxes(), type);
        }
    }

    @Override
    public void onStopNestedScroll(@NonNull View target) {
        onStopNestedScroll(target, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public void onStopNestedScroll(@NonNull View target, int type) {
        if (mScrollable) {
            mParentHelper.onStopNestedScroll(target, type);
            stopNestedScroll(type);
        }
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {

        onNestedScrollInternal(dxUnconsumed, dyUnconsumed, ViewCompat.TYPE_TOUCH, null);
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {
        onNestedScrollInternal(dxUnconsumed, dyUnconsumed, type, null);
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type, @NonNull int[] consumed) {
        onNestedScrollInternal(dxUnconsumed, dyUnconsumed, type, consumed);
    }

    private void onNestedScrollInternal(int dxUnconsumed, int dyUnconsumed, int type, @Nullable int[] consumed) {
        if (mScrollable) {
            if (getOrientation() == HORIZONTAL) {
                final int oldScrollX = getScrollX();
                scrollBy(dxUnconsumed, 0);
                final int myConsumed = getScrollX() - oldScrollX;
                if (consumed != null) {
                    consumed[0] += myConsumed;
                }
                final int myUnconsumed = dxUnconsumed - myConsumed;
                mChildHelper.dispatchNestedScroll(myConsumed, 0, myUnconsumed, 0, null, type, consumed);
            } else {
                final int oldScrollY = getScrollY();
                scrollBy(0, dyUnconsumed);
                final int myConsumed = getScrollY() - oldScrollY;
                if (consumed != null) {
                    consumed[1] += myConsumed;
                }
                final int myUnconsumed = dyUnconsumed - myConsumed;
                mChildHelper.dispatchNestedScroll(0, myConsumed, 0, myUnconsumed, null, type, consumed);
            }
        }
    }

    @Override
    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed) {
        onNestedPreScroll(target, dx, dy, consumed, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
        if (mScrollable) {
            dispatchNestedPreScroll(dx, dy, consumed, null, type);
        }
    }

    @Override
    public boolean onNestedFling(@NonNull View target, float velocityX, float velocityY, boolean consumed) {
        if (mScrollable && !consumed) {
            if (getOrientation() == HORIZONTAL) {
                flingXWithNestedDispatch((int) velocityX);
            } else {
                flingYWithNestedDispatch((int) velocityY);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY) {
        return mScrollable && dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public int getNestedScrollAxes() {
        if (mScrollable) {
            return mParentHelper.getNestedScrollAxes();
        } else {
            return ViewCompat.SCROLL_AXIS_NONE;
        }
    }

    // ScrollView import

    /**
     * 获取子视图内容宽度
     *
     * @return 内容宽度
     */
    public int getChildContentWidth() {
        int childCount = getChildCount();
        if (childCount == 0) {
            return 0;
        } else if (getOrientation() == VERTICAL) {
            return getWidth() - getPaddingLeft() - getPaddingRight();
        } else {
            View firstView = getChildAt(0);
            MarginLayoutParams firstLayoutParams = (MarginLayoutParams) firstView.getLayoutParams();
            View lastView = getChildAt(childCount - 1);
            MarginLayoutParams lastLayoutParams = (MarginLayoutParams) lastView.getLayoutParams();
            return lastView.getRight() - firstView.getLeft() + firstLayoutParams.leftMargin + lastLayoutParams.rightMargin;
        }
    }

    /**
     * 获取子视图内容高度
     *
     * @return 内容高度
     */
    public int getChildContentHeight() {
        int childCount = getChildCount();
        if (childCount == 0) {
            return 0;
        } else if (getOrientation() == HORIZONTAL) {
            return getHeight() - getPaddingTop() - getPaddingBottom();
        } else {
            View firstView = getChildAt(0);
            MarginLayoutParams firstLayoutParams = (MarginLayoutParams) firstView.getLayoutParams();
            View lastView = getChildAt(childCount - 1);
            MarginLayoutParams lastLayoutParams = (MarginLayoutParams) lastView.getLayoutParams();
            return lastView.getBottom() - firstView.getTop() + firstLayoutParams.topMargin + lastLayoutParams.bottomMargin;
        }
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return mScrollable;
    }

    @Override
    protected float getLeftFadingEdgeStrength() {
        if (getChildCount() == 0 || !mScrollable) {
            return 0.0f;
        }

        final int length = getHorizontalFadingEdgeLength();
        int mScrollX = getScrollX();
        if (mScrollX < length) {
            return mScrollX / (float) length;
        }

        return 1.0f;
    }

    @Override
    protected float getRightFadingEdgeStrength() {
        if (getChildCount() == 0 || !mScrollable) {
            return 0.0f;
        }

        final int length = getHorizontalFadingEdgeLength();
        final int rightEdge = getWidth() - getPaddingRight();
        final View lastChild = getChildAt(getChildCount() - 1);
        final MarginLayoutParams lastChildParams = (MarginLayoutParams) lastChild.getLayoutParams();
        final int span = lastChild.getRight() + lastChildParams.rightMargin - getScrollX() - rightEdge;
        if (span < length) {
            return span / (float) length;
        }

        return 1.0f;
    }

    @Override
    protected float getTopFadingEdgeStrength() {
        if (getChildCount() == 0 || !mScrollable) {
            return 0.0f;
        }

        final int length = getVerticalFadingEdgeLength();
        final int scrollY = getScrollY();
        if (scrollY < length) {
            return scrollY / (float) length;
        }

        return 1.0f;
    }

    @Override
    protected float getBottomFadingEdgeStrength() {
        if (getChildCount() == 0 || !mScrollable) {
            return 0.0f;
        }

        final int length = getVerticalFadingEdgeLength();
        final int bottomEdge = getHeight() - getPaddingBottom();
        final View lastChild = getChildAt(getChildCount() - 1);
        final MarginLayoutParams lastChildParams = (MarginLayoutParams) lastChild.getLayoutParams();
        final int span = lastChild.getBottom() + lastChildParams.bottomMargin - getScrollY() - bottomEdge;
        if (span < length) {
            return span / (float) length;
        }

        return 1.0f;
    }

    /**
     * @return The maximum amount this scroll view will scroll in response to
     * an arrow event.
     */
    public int getMaxScrollAmount() {
        return (int) (MAX_SCROLL_FACTOR * (getOrientation() == HORIZONTAL ? getWidth() : getHeight()));
    }

    private void initScrollView() {
        mScroller = new OverScroller(getContext());
        setFocusable(true);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setWillNotDraw(false);
        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mOverscrollDistance = configuration.getScaledOverscrollDistance();
        mOverflingDistance = configuration.getScaledOverflingDistance();
    }

    /**
     * Register a callback to be invoked when the scroll X or Y positions of
     * this view change.
     * <p>This version of the method works on all versions of Android, back to API v4.</p>
     *
     * @param l The listener to notify when the scroll X or Y position changes.
     * @see android.view.View#getScrollX()
     * @see android.view.View#getScrollY()
     */
    public void setOnScrollChangeListener(@Nullable OnScrollChangeListener l) {
        mOnScrollChangeListener = l;
    }

    /**
     * @return Returns true this ScrollView can be scrolled
     */
    private boolean canScroll() {
        if (mScrollable && getChildCount() > 0) {
            if (getOrientation() == HORIZONTAL) {
                return getWidth() - getPaddingLeft() - getPaddingRight() < getChildContentWidth();
            } else {
                return getHeight() - getPaddingTop() - getPaddingBottom() < getChildContentHeight();
            }
        }
        return false;
    }

    public boolean isScrollable() {
        return mScrollable;
    }

    public void setScrollable(boolean scrollable) {
        if (this.mScrollable != scrollable) {
            this.mScrollable = scrollable;
            if (this.mScrollable) {
                initOrResetVelocityTracker();
            } else {
                recycleVelocityTracker();
            }
            if (getChildCount() > 0) {
                // TODO 滚动到起点
                requestLayout();
            }
        }
    }

    /**
     * @return Whether arrow scrolling will animate its transition.
     */
    public boolean isSmoothScrollingEnabled() {
        return mSmoothScrollingEnabled;
    }

    /**
     * Set whether arrow scrolling will animate its transition.
     *
     * @param smoothScrollingEnabled whether arrow scrolling will animate its transition
     */
    public void setSmoothScrollingEnabled(boolean smoothScrollingEnabled) {
        mSmoothScrollingEnabled = smoothScrollingEnabled;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);

        if (mOnScrollChangeListener != null) {
            mOnScrollChangeListener.onScrollChange(this, l, t, oldl, oldt);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Let the focused view and/or our descendants get the key first
        return super.dispatchKeyEvent(event) || (mScrollable && executeKeyEvent(event));
    }

    /**
     * You can call this function yourself to have the scroll view perform
     * scrolling from a key event, just as if the event had been dispatched to
     * it by the view hierarchy.
     *
     * @param event The key event to execute.
     * @return Return true if the event was handled, else false.
     */
    public boolean executeKeyEvent(@NonNull KeyEvent event) {
        mTempRect.setEmpty();

        if (!canScroll()) {
            if (isFocused() && event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
                View currentFocused = findFocus();
                if (currentFocused == this) currentFocused = null;
                final int direction = getOrientation() == HORIZONTAL ? View.FOCUS_RIGHT : View.FOCUS_DOWN;
                View nextFocused = FocusFinder.getInstance().findNextFocus(this,
                        currentFocused, direction);
                return nextFocused != null
                        && nextFocused != this
                        && nextFocused.requestFocus(direction);
            }
            return false;
        }

        boolean handled = false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_LEFT);
                    } else {
                        handled = fullScroll(View.FOCUS_LEFT);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_RIGHT);
                    } else {
                        handled = fullScroll(View.FOCUS_RIGHT);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_UP);
                    } else {
                        handled = fullScroll(View.FOCUS_UP);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_DOWN);
                    } else {
                        handled = fullScroll(View.FOCUS_DOWN);
                    }
                    break;
                case KeyEvent.KEYCODE_SPACE:
                    if (getOrientation() == HORIZONTAL) {
                        pageScroll(event.isShiftPressed() ? View.FOCUS_LEFT : View.FOCUS_RIGHT);
                    } else {
                        pageScroll(event.isShiftPressed() ? View.FOCUS_UP : View.FOCUS_DOWN);
                    }
                    break;
            }
        }

        return handled;
    }

    private boolean inChild(int x, int y) {
        if (mScrollable && getChildCount() > 0) {
            final View firstChild = getChildAt(0);
            final View lastChild = getChildAt(getChildCount() - 1);
            if (getOrientation() == HORIZONTAL) {
                final int scrollX = getScrollX();
                return !(y < getPaddingTop()
                        || y >= getHeight() - getPaddingBottom()
                        || x < firstChild.getLeft() - scrollX
                        || x >= lastChild.getRight() - scrollX);
            } else {
                final int scrollY = getScrollY();
                return !(y < firstChild.getTop() - scrollY
                        || y >= lastChild.getBottom() - scrollY
                        || x < getPaddingLeft()
                        || x >= getWidth() - getPaddingRight());
            }
        }
        return false;
    }

    private void initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (mScrollable && disallowIntercept) {
            recycleVelocityTracker();
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!mScrollable) {
            return super.onInterceptTouchEvent(ev);
        }
        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onMotionEvent will be called and we do the actual
         * scrolling there.
         */

        /*
         * Shortcut the most recurring case: the user is in the dragging
         * state and he is moving his finger.  We want to intercept this
         * motion.
         */
        final int action = ev.getAction();
        if ((action == MotionEvent.ACTION_MOVE) && (mIsBeingDragged)) {
            return true;
        }

        // TODO
        if (getOrientation() == HORIZONTAL && super.onInterceptTouchEvent(ev)) {
            return true;
        }

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE: {
                /*
                 * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from his original down touch.
                 */

                /*
                 * Locally do absolute value. mLastMotionY is set to the y value
                 * of the down event.
                 */
                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    // If we don't have a valid id, the touch down wasn't on content.
                    break;
                }

                final int pointerIndex = ev.findPointerIndex(activePointerId);
                if (pointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=" + activePointerId
                            + " in onInterceptTouchEvent");
                    break;
                }

                if (getOrientation() == HORIZONTAL) {
                    final int x = (int) ev.getX(pointerIndex);
                    final int xDiff = Math.abs(x - mLastMotionX);
                    if (xDiff > mTouchSlop
                            && (getNestedScrollAxes() & ViewCompat.SCROLL_AXIS_HORIZONTAL) == 0) {
                        mIsBeingDragged = true;
                        mLastMotionX = x;
                        initVelocityTrackerIfNotExists();
                        mVelocityTracker.addMovement(ev);
                        mNestedXOffset = 0;
                        final ViewParent parent = getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                    }
                } else {
                    final int y = (int) ev.getY(pointerIndex);
                    final int yDiff = Math.abs(y - mLastMotionY);
                    if (yDiff > mTouchSlop
                            && (getNestedScrollAxes() & ViewCompat.SCROLL_AXIS_VERTICAL) == 0) {
                        mIsBeingDragged = true;
                        mLastMotionY = y;
                        initVelocityTrackerIfNotExists();
                        mVelocityTracker.addMovement(ev);
                        mNestedYOffset = 0;
                        final ViewParent parent = getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                    }
                }
                break;
            }

            case MotionEvent.ACTION_DOWN: {
                final int x = (int) ev.getX();
                final int y = (int) ev.getY();
                if (!inChild(x, y)) {
                    mIsBeingDragged = false;
                    recycleVelocityTracker();
                    break;
                }

                /*
                 * Remember location of down touch.
                 * ACTION_DOWN always refers to pointer index 0.
                 */
                boolean horizontal = getOrientation() == HORIZONTAL;
                if (horizontal) {
                    mLastMotionX = x;
                } else {
                    mLastMotionY = y;
                }
                mActivePointerId = ev.getPointerId(0);

                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(ev);
                /*
                 * If being flinged and user touches the screen, initiate drag;
                 * otherwise don't. mScroller.isFinished should be false when
                 * being flinged. We need to call computeScrollOffset() first so that
                 * isFinished() is correct.
                 */
                mScroller.computeScrollOffset();
                mIsBeingDragged = !mScroller.isFinished();
                startNestedScroll(horizontal ? ViewCompat.SCROLL_AXIS_HORIZONTAL : ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH);
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                /* Release the drag */
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                recycleVelocityTracker();
                int maxX = 0;
                int maxY = 0;
                if (getOrientation() == HORIZONTAL) {
                    maxX = getScrollRange();
                } else {
                    maxY = getScrollRange();
                }
                if (mScroller.springBack(getScrollX(), getScrollY(), 0, maxX, 0, maxY)) {
                    ViewCompat.postInvalidateOnAnimation(this);
                }
                stopNestedScroll(ViewCompat.TYPE_TOUCH);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }

        /*
         * The only time we want to intercept motion events is if we are in the
         * drag mode.
         */
        return mIsBeingDragged;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent ev) {
        if (!mScrollable) {
            return super.onTouchEvent(ev);
        }
        initVelocityTrackerIfNotExists();

        MotionEvent vtev = MotionEvent.obtain(ev);

        final int actionMasked = ev.getActionMasked();
        final boolean horizontal = getOrientation() == HORIZONTAL;

        if (actionMasked == MotionEvent.ACTION_DOWN) {
            mNestedXOffset = 0;
            mNestedYOffset = 0;
        }
        vtev.offsetLocation(mNestedXOffset, mNestedYOffset);

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN: {
                if (getChildCount() == 0) {
                    return false;
                }
                if ((mIsBeingDragged = !mScroller.isFinished())) {
                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                }

                /*
                 * If being flinged and user touches, stop the fling. isFinished
                 * will be false if being flinged.
                 */
                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                }

                // Remember where the motion event started
                if (horizontal) {
                    mLastMotionX = (int) ev.getX();
                } else {
                    mLastMotionY = (int) ev.getY();
                }
                mActivePointerId = ev.getPointerId(0);
                startNestedScroll(horizontal ? ViewCompat.SCROLL_AXIS_HORIZONTAL : ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                if (activePointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=" + mActivePointerId + " in onTouchEvent");
                    break;
                }

                final int x = (int) ev.getX(activePointerIndex);
                final int y = (int) ev.getY(activePointerIndex);
                int deltaX = 0, deltaY = 0;
                if (horizontal) {
                    deltaX = mLastMotionX - x;
                } else {
                    deltaY = mLastMotionY - y;
                }
                if (dispatchNestedPreScroll(deltaX, deltaY, mScrollConsumed, mScrollOffset, ViewCompat.TYPE_TOUCH)) {
                    if (horizontal) {
                        deltaX -= mScrollConsumed[0];
                        vtev.offsetLocation(mScrollOffset[0], 0);
                        mNestedXOffset += mScrollOffset[0];
                    } else {
                        deltaY -= mScrollConsumed[1];
                        vtev.offsetLocation(0, mScrollOffset[1]);
                        mNestedYOffset += mScrollOffset[1];
                    }
                }
                if (!mIsBeingDragged && Math.abs(deltaX + deltaY) > mTouchSlop) {
                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    mIsBeingDragged = true;
                    if (horizontal) {
                        if (deltaX > 0) {
                            deltaX -= mTouchSlop;
                        } else {
                            deltaX += mTouchSlop;
                        }
                    } else {
                        if (deltaY > 0) {
                            deltaY -= mTouchSlop;
                        } else {
                            deltaY += mTouchSlop;
                        }
                    }
                }
                if (mIsBeingDragged) {
                    if (horizontal) {
                        // Scroll to follow the motion event
                        mLastMotionX = x - mScrollOffset[0];

                        final int oldX = getScrollX();
                        final int range = getScrollRange();
                        final int overscrollMode = getOverScrollMode();
                        boolean canOverscroll = overscrollMode == View.OVER_SCROLL_ALWAYS
                                || (overscrollMode == View.OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0);

                        // Calling overScrollByCompat will call onOverScrolled, which
                        // calls onScrollChanged if applicable.
                        if (overScrollByCompat(deltaX, 0, getScrollX(), 0, range, 0, 0,
                                mOverscrollDistance, true) && !hasNestedScrollingParent(ViewCompat.TYPE_TOUCH)) {
                            // Break our velocity if we hit a scroll barrier.
                            mVelocityTracker.clear();
                        }

                        final int scrolledDeltaX = getScrollX() - oldX;
                        final int unconsumedX = deltaX - scrolledDeltaX;
                        if (dispatchNestedScroll(scrolledDeltaX, 0, unconsumedX, 0, mScrollOffset, ViewCompat.TYPE_TOUCH)) {
                            mLastMotionX -= mScrollOffset[0];
                            vtev.offsetLocation(mScrollOffset[0], 0);
                            mNestedXOffset += mScrollOffset[0];
                        } else if (canOverscroll) {
                            ensureGlows();
                            final int pulledToX = oldX + deltaX;
                            if (pulledToX < 0) {
                                EdgeEffectCompat.onPull(mEdgeGlowLeft, (float) deltaX / getWidth(),
                                        1.f - ev.getY(activePointerIndex) / getHeight());
                                if (!mEdgeGlowRight.isFinished()) {
                                    mEdgeGlowRight.onRelease();
                                }
                            } else if (pulledToX > range) {
                                EdgeEffectCompat.onPull(mEdgeGlowRight, (float) deltaX / getWidth(),
                                        ev.getY(activePointerIndex) / getHeight());
                                if (!mEdgeGlowLeft.isFinished()) {
                                    mEdgeGlowLeft.onRelease();
                                }
                            }
                            if (mEdgeGlowLeft != null
                                    && (!mEdgeGlowLeft.isFinished() || !mEdgeGlowRight.isFinished())) {
                                ViewCompat.postInvalidateOnAnimation(this);
                            }
                        }
                    } else {
                        // Scroll to follow the motion event
                        mLastMotionY = y - mScrollOffset[1];

                        final int oldY = getScrollY();
                        final int range = getScrollRange();
                        final int overscrollMode = getOverScrollMode();
                        boolean canOverscroll = overscrollMode == View.OVER_SCROLL_ALWAYS
                                || (overscrollMode == View.OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0);

                        // Calling overScrollByCompat will call onOverScrolled, which
                        // calls onScrollChanged if applicable.
                        if (overScrollByCompat(0, deltaY, 0, getScrollY(), 0, range, 0,
                                0, true) && !hasNestedScrollingParent(ViewCompat.TYPE_TOUCH)) {
                            // Break our velocity if we hit a scroll barrier.
                            mVelocityTracker.clear();
                        }

                        final int scrolledDeltaY = getScrollY() - oldY;
                        final int unconsumedY = deltaY - scrolledDeltaY;
                        if (dispatchNestedScroll(0, scrolledDeltaY, 0, unconsumedY, mScrollOffset,
                                ViewCompat.TYPE_TOUCH)) {
                            mLastMotionY -= mScrollOffset[1];
                            vtev.offsetLocation(0, mScrollOffset[1]);
                            mNestedYOffset += mScrollOffset[1];
                        } else if (canOverscroll) {
                            ensureGlows();
                            final int pulledToY = oldY + deltaY;
                            if (pulledToY < 0) {
                                EdgeEffectCompat.onPull(mEdgeGlowTop, (float) deltaY / getHeight(),
                                        ev.getX(activePointerIndex) / getWidth());
                                if (!mEdgeGlowBottom.isFinished()) {
                                    mEdgeGlowBottom.onRelease();
                                }
                            } else if (pulledToY > range) {
                                EdgeEffectCompat.onPull(mEdgeGlowBottom, (float) deltaY / getHeight(),
                                        1.f - ev.getX(activePointerIndex) / getWidth());
                                if (!mEdgeGlowTop.isFinished()) {
                                    mEdgeGlowTop.onRelease();
                                }
                            }
                            if (mEdgeGlowTop != null
                                    && (!mEdgeGlowTop.isFinished() || !mEdgeGlowBottom.isFinished())) {
                                ViewCompat.postInvalidateOnAnimation(this);
                            }
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                if (horizontal) {
                    int initialVelocity = (int) velocityTracker.getXVelocity(mActivePointerId);
                    if ((Math.abs(initialVelocity) > mMinimumVelocity)) {
                        if (!dispatchNestedPreFling(-initialVelocity, 0)) {
                            dispatchNestedFling(-initialVelocity, 0, true);
                            flingXWithNestedDispatch(-initialVelocity);
                        }
                    } else if (mScroller.springBack(getScrollX(), getScrollY(), 0, getScrollRange(), 0, 0)) {
                        ViewCompat.postInvalidateOnAnimation(this);
                    }
                } else {
                    int initialVelocity = (int) velocityTracker.getYVelocity(mActivePointerId);
                    if ((Math.abs(initialVelocity) > mMinimumVelocity)) {
                        if (!dispatchNestedPreFling(0, -initialVelocity)) {
                            dispatchNestedFling(0, -initialVelocity, true);
                            flingYWithNestedDispatch(-initialVelocity);
                        }
                    } else if (mScroller.springBack(getScrollX(), getScrollY(), 0, 0, 0, getScrollRange())) {
                        ViewCompat.postInvalidateOnAnimation(this);
                    }
                }
                mActivePointerId = INVALID_POINTER;
                endDrag();
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mIsBeingDragged && getChildCount() > 0) {
                    int maxX = 0, maxY = 0;
                    if (horizontal) {
                        maxX = getScrollRange();
                    } else {
                        maxY = getScrollRange();
                    }
                    if (mScroller.springBack(getScrollX(), getScrollY(), 0, maxX, 0, maxY)) {
                        ViewCompat.postInvalidateOnAnimation(this);
                    }
                }
                mActivePointerId = INVALID_POINTER;
                endDrag();
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = ev.getActionIndex();
                if (horizontal) {
                    mLastMotionX = (int) ev.getX(index);
                } else {
                    mLastMotionY = (int) ev.getY(index);
                }
                mActivePointerId = ev.getPointerId(index);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                if (horizontal) {
                    mLastMotionX = (int) ev.getX(ev.findPointerIndex(mActivePointerId));
                } else {
                    mLastMotionY = (int) ev.getY(ev.findPointerIndex(mActivePointerId));
                }
                break;
        }

        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(vtev);
        }
        vtev.recycle();
        return true;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = ev.getActionIndex();
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            if (getOrientation() == HORIZONTAL) {
                mLastMotionX = (int) ev.getX(newPointerIndex);
            } else {
                mLastMotionY = (int) ev.getY(newPointerIndex);
            }
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mScrollable && (event.getSource() & InputDeviceCompat.SOURCE_CLASS_POINTER) != 0
                && event.getAction() == MotionEvent.ACTION_SCROLL && !mIsBeingDragged) {
            if (getOrientation() == HORIZONTAL) {
                final float hscroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
                if (hscroll != 0) {
                    final int delta = (int) (hscroll * getScrollFactorCompat());
                    final int range = getScrollRange();
                    int oldScrollX = getScrollX();
                    int newScrollX = restrainIntRange(oldScrollX - delta, 0, range);
                    if (newScrollX != oldScrollX) {
                        super.scrollTo(newScrollX, getScrollY());
                        return true;
                    }
                }
            } else {
                final float vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (vscroll != 0) {
                    final int delta = (int) (vscroll * getScrollFactorCompat());
                    final int range = getScrollRange();
                    int oldScrollY = getScrollY();
                    int newScrollY = restrainIntRange(oldScrollY - delta, 0, range);
                    if (newScrollY != oldScrollY) {
                        super.scrollTo(getScrollX(), newScrollY);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private float getScrollFactorCompat() {
        if (mScrollFactor == 0) {
            TypedValue outValue = new TypedValue();
            final Context context = getContext();
            if (!context.getTheme().resolveAttribute(
                    android.R.attr.listPreferredItemHeight, outValue, true)) {
                throw new IllegalStateException(
                        "Expected theme to define listPreferredItemHeight.");
            }
            mScrollFactor = outValue.getDimension(context.getResources().getDisplayMetrics());
        }
        return mScrollFactor;
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY,
                                  boolean clampedX, boolean clampedY) {
        if (mScrollable) {
            super.scrollTo(scrollX, scrollY);
        }
    }

    boolean overScrollByCompat(int deltaX, int deltaY, int scrollX, int scrollY,
                               int scrollRangeX, int scrollRangeY, int maxOverScrollX, int maxOverScrollY,
                               boolean isTouchEvent) {
        final int overScrollMode = getOverScrollMode();
        final boolean canScrollHorizontal = computeHorizontalScrollRange() > computeHorizontalScrollExtent();
        final boolean canScrollVertical = computeVerticalScrollRange() > computeVerticalScrollExtent();
        final boolean overScrollHorizontal = overScrollMode == View.OVER_SCROLL_ALWAYS
                || (overScrollMode == View.OVER_SCROLL_IF_CONTENT_SCROLLS && canScrollHorizontal);
        final boolean overScrollVertical = overScrollMode == View.OVER_SCROLL_ALWAYS
                || (overScrollMode == View.OVER_SCROLL_IF_CONTENT_SCROLLS && canScrollVertical);

        int newScrollX = scrollX + deltaX;
        if (!overScrollHorizontal) {
            maxOverScrollX = 0;
        }

        int newScrollY = scrollY + deltaY;
        if (!overScrollVertical) {
            maxOverScrollY = 0;
        }

        // Clamp values if at the limits and record
        final int left = -maxOverScrollX;
        final int right = maxOverScrollX + scrollRangeX;
        final int top = -maxOverScrollY;
        final int bottom = maxOverScrollY + scrollRangeY;

        boolean clampedX = false;
        if (newScrollX > right) {
            newScrollX = right;
            clampedX = true;
        } else if (newScrollX < left) {
            newScrollX = left;
            clampedX = true;
        }

        boolean clampedY = false;
        if (newScrollY > bottom) {
            newScrollY = bottom;
            clampedY = true;
        } else if (newScrollY < top) {
            newScrollY = top;
            clampedY = true;
        }

        int maxX = 0, maxY = 0;
        if (getOrientation() == HORIZONTAL) {
            maxX = getScrollRange();
        } else {
            maxY = getScrollRange();
        }
        if ((clampedX || clampedY) && !hasNestedScrollingParent(ViewCompat.TYPE_NON_TOUCH)) {
            mScroller.springBack(newScrollX, newScrollY, 0, maxX, 0, maxY);
        }

        onOverScrolled(newScrollX, newScrollY, clampedX, clampedY);

        return clampedX || clampedY;
    }

    int getScrollRange() {
        int scrollRange = 0;
        if (mScrollable && getChildCount() > 0) {
            if (getOrientation() == HORIZONTAL) {
                scrollRange = Math.max(0, getChildContentWidth() - (getWidth() - getPaddingLeft() - getPaddingRight()));
            } else {
                scrollRange = Math.max(0, getChildContentHeight() - (getHeight() - getPaddingBottom() - getPaddingTop()));
            }
        }
        return scrollRange;
    }


    /**
     * <p>
     * Finds the next focusable component that fits in this View's bounds
     * (excluding fading edges) pretending that this View's left is located at
     * the parameter left.
     * </p>
     *
     * @param leftFocus          look for a candidate is the one at the left of the bounds
     *                           if leftFocus is true, or at the right of the bounds if leftFocus
     *                           is false
     * @param left               the left offset of the bounds in which a focusable must be
     *                           found (the fading edge is assumed to start at this position)
     * @param preferredFocusable the View that has highest priority and will be
     *                           returned if it is within my bounds (null is valid)
     * @return the next focusable component in the bounds or null if none can be found
     */
    private View findFocusableViewInMyBounds(final boolean leftFocus,
                                             final int left, View preferredFocusable) {
        /*
         * The fading edge's transparent side should be considered for focus
         * since it's mostly visible, so we divide the actual fading edge length
         * by 2.
         */
        final int fadingEdgeLength = getHorizontalFadingEdgeLength() / 2;
        final int leftWithoutFadingEdge = left + fadingEdgeLength;
        final int rightWithoutFadingEdge = left + getWidth() - fadingEdgeLength;

        if ((preferredFocusable != null)
                && (preferredFocusable.getLeft() < rightWithoutFadingEdge)
                && (preferredFocusable.getRight() > leftWithoutFadingEdge)) {
            return preferredFocusable;
        }

        return findFocusableViewInBounds(leftFocus, leftWithoutFadingEdge, rightWithoutFadingEdge);
    }

    /**
     * <p>
     * Finds the next focusable component that fits in the specified bounds.
     * </p>
     *
     * @param startFocus look for a candidate is the one at the top of the bounds
     *                   if topFocus is true, or at the bottom of the bounds if topFocus is
     *                   false
     * @param start      the top offset of the bounds in which a focusable must be
     *                   found
     * @param end        the bottom offset of the bounds in which a focusable must
     *                   be found
     * @return the next focusable component in the bounds or null if none can
     * be found
     */
    private View findFocusableViewInBounds(boolean startFocus, int start, int end) {
        final boolean horizontal = getOrientation() == HORIZONTAL;
        List<View> focusables = getFocusables(View.FOCUS_FORWARD);
        View focusCandidate = null;

        /*
         * A fully contained focusable is one where its top is below the bound's
         * top, and its bottom is above the bound's bottom. A partially
         * contained focusable is one where some part of it is within the
         * bounds, but it also has some part that is not within bounds.  A fully contained
         * focusable is preferred to a partially contained focusable.
         */
        boolean foundFullyContainedFocusable = false;

        for (int i = 0, count = focusables.size(); i < count; i++) {
            View view = focusables.get(i);
            int viewLeft = view.getLeft();
            int viewRight = view.getRight();
            int viewTop = view.getTop();
            int viewBottom = view.getBottom();

            if (horizontal && start < viewRight && viewLeft < end) {
                /*
                 * the focusable is in the target area, it is a candidate for
                 * focusing
                 */
                final boolean viewIsFullyContained = (start < viewLeft) && (viewRight < end);

                if (focusCandidate == null) {
                    /* No candidate, take this one */
                    focusCandidate = view;
                    foundFullyContainedFocusable = viewIsFullyContained;
                } else {
                    final boolean viewIsCloserToBoundary =
                            (startFocus && viewLeft < focusCandidate.getLeft()) ||
                                    (!startFocus && viewRight > focusCandidate.getRight());

                    if (foundFullyContainedFocusable) {
                        if (viewIsFullyContained && viewIsCloserToBoundary) {
                            /*
                             * We're dealing with only fully contained views, so
                             * it has to be closer to the boundary to beat our
                             * candidate
                             */
                            focusCandidate = view;
                        }
                    } else {
                        if (viewIsFullyContained) {
                            /* Any fully contained view beats a partially contained view */
                            focusCandidate = view;
                            foundFullyContainedFocusable = true;
                        } else if (viewIsCloserToBoundary) {
                            /*
                             * Partially contained view beats another partially
                             * contained view if it's closer
                             */
                            focusCandidate = view;
                        }
                    }
                }
            } else if (!horizontal && start < viewBottom && viewTop < end) {
                /*
                 * the focusable is in the target area, it is a candidate for
                 * focusing
                 */
                final boolean viewIsFullyContained = (start < viewTop) && (viewBottom < end);

                if (focusCandidate == null) {
                    /* No candidate, take this one */
                    focusCandidate = view;
                    foundFullyContainedFocusable = viewIsFullyContained;
                } else {
                    final boolean viewIsCloserToBoundary =
                            (startFocus && viewTop < focusCandidate.getTop())
                                    || (!startFocus && viewBottom > focusCandidate.getBottom());

                    if (foundFullyContainedFocusable) {
                        if (viewIsFullyContained && viewIsCloserToBoundary) {
                            /*
                             * We're dealing with only fully contained views, so
                             * it has to be closer to the boundary to beat our
                             * candidate
                             */
                            focusCandidate = view;
                        }
                    } else {
                        if (viewIsFullyContained) {
                            /* Any fully contained view beats a partially contained view */
                            focusCandidate = view;
                            foundFullyContainedFocusable = true;
                        } else if (viewIsCloserToBoundary) {
                            /*
                             * Partially contained view beats another partially
                             * contained view if it's closer
                             */
                            focusCandidate = view;
                        }
                    }
                }
            }
        }

        return focusCandidate;
    }

    /**
     * <p>Handles scrolling in response to a "page up/down" shortcut press. This
     * method will scroll the view by one page up or down and give the focus
     * to the topmost/bottommost component in the new visible area. If no
     * component is a good candidate for focus, this scrollview reclaims the
     * focus.</p>
     *
     * @param direction the scroll direction: {@link android.view.View#FOCUS_UP}
     *                  to go one page up or
     *                  {@link android.view.View#FOCUS_DOWN} to go one page down
     * @return true if the key event is consumed by this method, false otherwise
     */
    public boolean pageScroll(int direction) {
        final boolean horizontal = getOrientation() == HORIZONTAL;
        if (horizontal && (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN)) {
            return false;
        } else if (!horizontal && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)) {
            return false;
        }

        int width = getWidth();
        int height = getHeight();

        if (direction == View.FOCUS_RIGHT) {
            mTempRect.left = getScrollX() + width;
            int count = getChildCount();
            if (count > 0) {
                View view = getChildAt(count - 1);
                MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
                int right = view.getRight() + lp.rightMargin + getPaddingRight();
                if (mTempRect.left + width > right) {
                    mTempRect.left = right - width;
                }
            }
        } else if (direction == View.FOCUS_LEFT) {
            mTempRect.left = getScrollX() - width;
            if (mTempRect.left < 0) {
                mTempRect.left = 0;
            }
        } else if (direction == View.FOCUS_DOWN) {
            mTempRect.top = getScrollY() + height;
            int count = getChildCount();
            if (count > 0) {
                View view = getChildAt(count - 1);
                MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
                int bottom = view.getBottom() + lp.bottomMargin + getPaddingBottom();
                if (mTempRect.top + height > bottom) {
                    mTempRect.top = bottom - height;
                }
            }
        } else {
            mTempRect.top = getScrollY() - height;
            if (mTempRect.top < 0) {
                mTempRect.top = 0;
            }
        }

        if (horizontal) {
            mTempRect.right = mTempRect.left + width;
            return scrollAndFocus(direction, mTempRect.left, mTempRect.right);
        } else {
            mTempRect.bottom = mTempRect.top + height;
            return scrollAndFocus(direction, mTempRect.top, mTempRect.bottom);
        }
    }

    /**
     * <p>Handles scrolling in response to a "home/end" shortcut press. This
     * method will scroll the view to the top or bottom and give the focus
     * to the topmost/bottommost component in the new visible area. If no
     * component is a good candidate for focus, this scrollview reclaims the
     * focus.</p>
     *
     * @param direction the scroll direction: {@link android.view.View#FOCUS_UP}
     *                  to go the top of the view or
     *                  {@link android.view.View#FOCUS_DOWN} to go the bottom
     * @return true if the key event is consumed by this method, false otherwise
     */
    public boolean fullScroll(int direction) {
        final boolean horizontal = getOrientation() == HORIZONTAL;
        if (horizontal && (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN)) {
            return false;
        } else if (!horizontal && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)) {
            return false;
        }

        int width = getWidth();
        int height = getHeight();

        mTempRect.left = 0;
        mTempRect.right = width;
        mTempRect.top = 0;
        mTempRect.bottom = height;

        if (direction == View.FOCUS_DOWN) {
            int count = getChildCount();
            if (count > 0) {
                View view = getChildAt(count - 1);
                MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
                mTempRect.bottom = view.getBottom() + lp.bottomMargin + getPaddingBottom();
                mTempRect.top = mTempRect.bottom - height;
            }
        } else if (direction == View.FOCUS_RIGHT) {
            int count = getChildCount();
            if (count > 0) {
                View view = getChildAt(count - 1);
                MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
                mTempRect.right = view.getRight() + lp.rightMargin + getPaddingRight();
                mTempRect.left = mTempRect.right - width;
            }
        }

        if (horizontal) {
            return scrollAndFocus(direction, mTempRect.left, mTempRect.right);
        } else {
            return scrollAndFocus(direction, mTempRect.top, mTempRect.bottom);
        }
    }

    /**
     * <p>Scrolls the view to make the area defined by <code>top</code> and
     * <code>bottom</code> visible. This method attempts to give the focus
     * to a component visible in this area. If no component can be focused in
     * the new visible area, the focus is reclaimed by this ScrollView.</p>
     *
     * @param direction the scroll direction: {@link android.view.View#FOCUS_UP}
     *                  to go upward, {@link android.view.View#FOCUS_DOWN} to downward
     * @param start     the top offset of the new area to be made visible
     * @param end       the bottom offset of the new area to be made visible
     * @return true if the key event is consumed by this method, false otherwise
     */
    private boolean scrollAndFocus(int direction, int start, int end) {
        final boolean horizontal = getOrientation() == HORIZONTAL;

        boolean handled = true;

        int containerStart = horizontal ? getScrollX() : getScrollY();
        int containerEnd = containerStart + (horizontal ? getWidth() : getHeight());
        boolean up = (horizontal && direction == View.FOCUS_LEFT) || (!horizontal && direction == View.FOCUS_UP);

        View newFocused = findFocusableViewInBounds(up, start, end);
        if (newFocused == null) {
            newFocused = this;
        }

        if (start >= containerStart && end <= containerEnd) {
            handled = false;
        } else {
            int delta = up ? (start - containerStart) : (end - containerEnd);
            if (horizontal) {
                doScroll(delta, 0);
            } else {
                doScroll(0, delta);
            }
        }

        if (newFocused != findFocus()) newFocused.requestFocus(direction);

        return handled;
    }

    /**
     * Handle scrolling in response to an up or down arrow click.
     *
     * @param direction The direction corresponding to the arrow key that was
     *                  pressed
     * @return True if we consumed the event, false otherwise
     */
    public boolean arrowScroll(int direction) {
        final boolean horizontal = getOrientation() == HORIZONTAL;

        View currentFocused = findFocus();
        if (currentFocused == this) currentFocused = null;

        View nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, direction);

        final int maxJump = getMaxScrollAmount();

        if (nextFocused != null && isWithinDeltaOfScreen(nextFocused, maxJump, getHeight(), getWidth())) {
            nextFocused.getDrawingRect(mTempRect);
            offsetDescendantRectToMyCoords(nextFocused, mTempRect);
            int scrollDeltaX = 0, scrollDeltaY = 0;
            if (horizontal) {
                scrollDeltaX = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
            } else {
                scrollDeltaY = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
            }
            doScroll(scrollDeltaX, scrollDeltaY);
            nextFocused.requestFocus(direction);
        } else {
            // no new focus
            int scrollDelta = maxJump;

            if (horizontal) {
                if (direction == View.FOCUS_LEFT && getScrollX() < scrollDelta) {
                    scrollDelta = getScrollX();
                } else if (direction == View.FOCUS_RIGHT && getChildCount() > 0) {
                    View lastView = getChildAt(getChildCount() - 1);
                    MarginLayoutParams lp = (MarginLayoutParams) lastView.getLayoutParams();
                    int daRight = lastView.getRight() + lp.rightMargin;
                    int screenRight = getScrollX() + getWidth() - getPaddingRight();
                    scrollDelta = Math.min(daRight - screenRight, maxJump);
                }
                if (scrollDelta == 0) {
                    return false;
                }
                doScroll(direction == View.FOCUS_RIGHT ? scrollDelta : -scrollDelta, 0);
            } else {
                if (direction == View.FOCUS_UP && getScrollY() < scrollDelta) {
                    scrollDelta = getScrollY();
                } else if (direction == View.FOCUS_DOWN && getChildCount() > 0) {
                    View lastView = getChildAt(getChildCount() - 1);
                    MarginLayoutParams lp = (MarginLayoutParams) lastView.getLayoutParams();
                    int daBottom = lastView.getBottom() + lp.bottomMargin;
                    int screenBottom = getScrollY() + getHeight() - getPaddingBottom();
                    scrollDelta = Math.min(daBottom - screenBottom, maxJump);
                }
                if (scrollDelta == 0) {
                    return false;
                }
                doScroll(0, direction == View.FOCUS_DOWN ? scrollDelta : -scrollDelta);
            }
        }

        if (currentFocused != null && currentFocused.isFocused() && isOffScreen(currentFocused)) {
            // previously focused item still has focus and is off screen, give
            // it up (take it back to ourselves)
            // (also, need to temporarily force FOCUS_BEFORE_DESCENDANTS so we are
            // sure to
            // get it)
            final int descendantFocusability = getDescendantFocusability();  // save
            setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            requestFocus();
            setDescendantFocusability(descendantFocusability);  // restore
        }
        return true;
    }

    /**
     * @return whether the descendant of this scroll view is scrolled off
     * screen.
     */
    private boolean isOffScreen(View descendant) {
        return !isWithinDeltaOfScreen(descendant, 0, getHeight(), getWidth());
    }

    /**
     * @return whether the descendant of this scroll view is within delta
     * pixels of being on the screen.
     */
    private boolean isWithinDeltaOfScreen(View descendant, int delta, int height, int width) {
        descendant.getDrawingRect(mTempRect);
        offsetDescendantRectToMyCoords(descendant, mTempRect);

        if (getOrientation() == HORIZONTAL) {
            return (mTempRect.right + delta) >= getScrollX()
                    && (mTempRect.left - delta) <= (getScrollX() + width);
        } else {
            return (mTempRect.bottom + delta) >= getScrollY()
                    && (mTempRect.top - delta) <= (getScrollY() + height);
        }

    }

    /**
     * Smooth scroll by a Y delta
     *
     * @param deltaY the number of pixels to scroll by on the Y axis
     */
    private void doScroll(int deltaX, int deltaY) {
        if (deltaX != 0 || deltaY != 0) {
            if (mSmoothScrollingEnabled) {
                smoothScrollBy(deltaX, deltaY);
            } else {
                scrollBy(deltaX, deltaY);
            }
        }
    }

    /**
     * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
     *
     * @param dx the number of pixels to scroll by on the X axis
     * @param dy the number of pixels to scroll by on the Y axis
     */
    public final void smoothScrollBy(int dx, int dy) {
        smoothScrollBy(dx, dy, DEFAULT_SMOOTH_SCROLL_DURATION, false);
    }

    /**
     * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
     *
     * @param dx               the number of pixels to scroll by on the X axis
     * @param dy               the number of pixels to scroll by on the Y axis
     * @param scrollDurationMs the duration of the smooth scroll operation in milliseconds
     */
    public final void smoothScrollBy(int dx, int dy, int scrollDurationMs) {
        smoothScrollBy(dx, dy, scrollDurationMs, false);
    }


    /**
     * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
     *
     * @param dx                  the number of pixels to scroll by on the X axis
     * @param dy                  the number of pixels to scroll by on the Y axis
     * @param scrollDurationMs    the duration of the smooth scroll operation in milliseconds
     * @param withNestedScrolling whether to include nested scrolling operations.
     */
    private void smoothScrollBy(int dx, int dy, int scrollDurationMs, boolean withNestedScrolling) {
        if (getChildCount() == 0) {
            // Nothing to do.
            return;
        }
        long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
        if (duration > ANIMATED_SCROLL_GAP) {
            if (getOrientation() == HORIZONTAL) {
                int childrenWidth = getChildContentWidth();
                int parentWidthSpace = getWidth() - getPaddingRight() - getPaddingLeft();
                final int scrollX = getScrollX();
                final int maxX = Math.max(0, childrenWidth - parentWidthSpace);
                dx = Math.max(0, Math.min(scrollX + dx, maxX)) - scrollX;
                mScroller.startScroll(scrollX, getScrollY(), dx, 0, scrollDurationMs);
            } else {
                int childrenHeight = getChildContentHeight();
                int parentHeightSpace = getHeight() - getPaddingTop() - getPaddingBottom();
                final int scrollY = getScrollY();
                final int maxY = Math.max(0, childrenHeight - parentHeightSpace);
                dy = Math.max(0, Math.min(scrollY + dy, maxY)) - scrollY;
                mScroller.startScroll(getScrollX(), scrollY, 0, dy, scrollDurationMs);
            }

            runAnimatedScroll(withNestedScrolling);
        } else {
            if (!mScroller.isFinished()) {
                abortAnimatedScroll();
            }
            scrollBy(dx, dy);
        }
        mLastScroll = AnimationUtils.currentAnimationTimeMillis();
    }

    /**
     * Like {@link #scrollTo}, but scroll smoothly instead of immediately.
     *
     * @param x the position where to scroll on the X axis
     * @param y the position where to scroll on the Y axis
     */
    public final void smoothScrollTo(int x, int y) {
        smoothScrollTo(x, y, DEFAULT_SMOOTH_SCROLL_DURATION, false);
    }

    /**
     * Like {@link #scrollTo}, but scroll smoothly instead of immediately.
     *
     * @param x                the position where to scroll on the X axis
     * @param y                the position where to scroll on the Y axis
     * @param scrollDurationMs the duration of the smooth scroll operation in milliseconds
     */
    public final void smoothScrollTo(int x, int y, int scrollDurationMs) {
        smoothScrollTo(x, y, scrollDurationMs, false);
    }

    /**
     * Like {@link #scrollTo}, but scroll smoothly instead of immediately.
     *
     * @param x                   the position where to scroll on the X axis
     * @param y                   the position where to scroll on the Y axis
     * @param withNestedScrolling whether to include nested scrolling operations.
     */
    // This should be considered private, it is package private to avoid a synthetic ancestor.
    void smoothScrollTo(int x, int y, boolean withNestedScrolling) {
        smoothScrollTo(x, y, DEFAULT_SMOOTH_SCROLL_DURATION, withNestedScrolling);
    }

    /**
     * Like {@link #scrollTo}, but scroll smoothly instead of immediately.
     *
     * @param x                   the position where to scroll on the X axis
     * @param y                   the position where to scroll on the Y axis
     * @param scrollDurationMs    the duration of the smooth scroll operation in milliseconds
     * @param withNestedScrolling whether to include nested scrolling operations.
     */
    // This should be considered private, it is package private to avoid a synthetic ancestor.
    void smoothScrollTo(int x, int y, int scrollDurationMs, boolean withNestedScrolling) {
        smoothScrollBy(x - getScrollX(), y - getScrollY(), scrollDurationMs, withNestedScrolling);
    }

    /**
     * <p>The scroll range of a scroll view is the overall height of all of its
     * children.</p>
     */
    @Override
    public int computeVerticalScrollRange() {
        if (!mScrollable || getOrientation() == HORIZONTAL) {
            return super.computeVerticalScrollRange();
        }
        final int count = getChildCount();
        final int contentHeight = getHeight() - getPaddingBottom() - getPaddingTop();
        if (count == 0) {
            return contentHeight;
        }

        View lastChild = getChildAt(count - 1);
        MarginLayoutParams lp = (MarginLayoutParams) lastChild.getLayoutParams();
        int scrollRange = lastChild.getBottom() + lp.bottomMargin;
        final int scrollY = getScrollY();
        final int overscrollBottom = Math.max(0, scrollRange - contentHeight);
        if (scrollY < 0) {
            scrollRange -= scrollY;
        } else if (scrollY > overscrollBottom) {
            scrollRange += scrollY - overscrollBottom;
        }

        return scrollRange;
    }

    @Override
    public int computeVerticalScrollOffset() {
        if (!mScrollable || getOrientation() == HORIZONTAL) {
            return super.computeVerticalScrollOffset();
        }
        return Math.max(0, super.computeVerticalScrollOffset());
    }

    @Override
    public int computeVerticalScrollExtent() {
        return super.computeVerticalScrollExtent();
    }

    @Override
    public int computeHorizontalScrollRange() {
        if (!mScrollable || getOrientation() == VERTICAL) {
            return super.computeVerticalScrollRange();
        }
        final int count = getChildCount();
        final int contentWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        if (count == 0) {
            return contentWidth;
        }

        View lastChild = getChildAt(count - 1);
        MarginLayoutParams lp = (MarginLayoutParams) lastChild.getLayoutParams();
        int scrollRange = lastChild.getRight() + lp.rightMargin;
        final int scrollX = getScrollX();
        final int overscrollRight = Math.max(0, scrollRange - contentWidth);
        if (scrollX < 0) {
            scrollRange -= scrollX;
        } else if (scrollX > overscrollRight) {
            scrollRange += scrollX - overscrollRight;
        }

        return scrollRange;
    }

    @Override
    public int computeHorizontalScrollOffset() {
        if (!mScrollable || getOrientation() == VERTICAL) {
            return super.computeHorizontalScrollOffset();
        }
        return Math.max(0, super.computeHorizontalScrollOffset());
    }

    @Override
    public int computeHorizontalScrollExtent() {
        return super.computeHorizontalScrollExtent();
    }

    @Override
    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        if (!mScrollable) {
            super.measureChild(child, parentWidthMeasureSpec, parentHeightMeasureSpec);
            return;
        }
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (getOrientation() == HORIZONTAL) {
            // 因为水平滚动，修改父容器的测量模式为不指定，便于子视图可以任意扩展宽度
            parentWidthMeasureSpec = modifyMeasureSpecMode(parentWidthMeasureSpec, MeasureSpec.UNSPECIFIED);
            // 因为测量bug，修正垂直方向上的尺寸模式
            if (lp.height == ViewGroup.LayoutParams.MATCH_PARENT && MeasureSpec.getMode(parentHeightMeasureSpec) == MeasureSpec.AT_MOST) {
                parentHeightMeasureSpec = modifyMeasureSpecMode(parentHeightMeasureSpec, MeasureSpec.EXACTLY);
            }
        } else {
            if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT && MeasureSpec.getMode(parentWidthMeasureSpec) == MeasureSpec.AT_MOST) {
                parentWidthMeasureSpec = modifyMeasureSpecMode(parentWidthMeasureSpec, MeasureSpec.EXACTLY);
            }
            // 因为垂直滚动，所以高度模式不指定，便于子视图任意扩展高度
            parentHeightMeasureSpec = modifyMeasureSpecMode(parentHeightMeasureSpec, MeasureSpec.UNSPECIFIED);
        }

        int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                getPaddingLeft() + getPaddingRight(), lp.width);
        int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                getPaddingTop() + getPaddingBottom(), lp.height);

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
                                           int parentHeightMeasureSpec, int heightUsed) {
        if (!mScrollable) {
            super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed);
            return;
        }
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        if (getOrientation() == HORIZONTAL) {
            // 因为水平滚动，修改父容器的测量模式为不指定，便于子视图可以任意扩展宽度
            parentWidthMeasureSpec = modifyMeasureSpecMode(parentWidthMeasureSpec, MeasureSpec.UNSPECIFIED);
            // 因为测量bug，修正垂直方向上的尺寸模式
            if (lp.height == ViewGroup.LayoutParams.MATCH_PARENT && MeasureSpec.getMode(parentHeightMeasureSpec) == MeasureSpec.AT_MOST) {
                parentHeightMeasureSpec = modifyMeasureSpecMode(parentHeightMeasureSpec, MeasureSpec.EXACTLY);
            }
        } else {
            if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT && MeasureSpec.getMode(parentWidthMeasureSpec) == MeasureSpec.AT_MOST) {
                parentWidthMeasureSpec = modifyMeasureSpecMode(parentWidthMeasureSpec, MeasureSpec.EXACTLY);
            }
            // 因为垂直滚动，所以高度模式不指定，便于子视图任意扩展高度
            parentHeightMeasureSpec = modifyMeasureSpecMode(parentHeightMeasureSpec, MeasureSpec.UNSPECIFIED);
        }

        int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin
                        + widthUsed, lp.width);
        int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin
                        + heightUsed, lp.height);

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    private static int modifyMeasureSpecMode(int spec, int mode) {
        return MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(spec), mode);
    }

    @Override
    public void computeScroll() {
        if (!mScrollable || mScroller.isFinished()) {
            return;
        }
        mScroller.computeScrollOffset();
        final boolean horizontal = getOrientation() == HORIZONTAL;
        final int x = mScroller.getCurrX();
        final int y = mScroller.getCurrY();
        int unconsumedX = 0, unconsumedY = 0;
        if (horizontal) {
            unconsumedX = x - mLastScrollerX;
            mLastScrollerX = x;
            // Nested Scrolling Pre Pass
            mScrollConsumed[0] = 0;
        } else {
            unconsumedY = y - mLastScrollerY;
            mLastScrollerY = y;
            // Nested Scrolling Pre Pass
            mScrollConsumed[1] = 0;
        }

        // Nested Scrolling Pre Pass
        dispatchNestedPreScroll(unconsumedX, unconsumedY, mScrollConsumed, null, ViewCompat.TYPE_NON_TOUCH);
        unconsumedX -= mScrollConsumed[0];
        unconsumedY -= mScrollConsumed[1];

        final int range = getScrollRange();

        if (horizontal && unconsumedX != 0) {
            final int oldScrollX = getScrollX();

            overScrollByCompat(unconsumedX, 0, oldScrollX, getScrollY(), range, 0, 0, 0, false);
            final int scrolledXByMe = getScrollX() - oldScrollX;
            unconsumedX -= scrolledXByMe;

            // Nested Scrolling Post Pass
            mScrollConsumed[0] = 0;
            dispatchNestedScroll(scrolledXByMe, 0, unconsumedX, 0, mScrollOffset, ViewCompat.TYPE_NON_TOUCH, mScrollConsumed);
            unconsumedX -= mScrollConsumed[0];

            if (unconsumedX != 0) {
                final int mode = getOverScrollMode();
                final boolean canOverscroll = mode == OVER_SCROLL_ALWAYS
                        || (mode == OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0);
                if (canOverscroll) {
                    ensureGlows();
                    if (unconsumedX < 0) {
                        if (mEdgeGlowLeft.isFinished()) {
                            mEdgeGlowLeft.onAbsorb((int) mScroller.getCurrVelocity());
                        }
                    } else {
                        if (mEdgeGlowRight.isFinished()) {
                            mEdgeGlowRight.onAbsorb((int) mScroller.getCurrVelocity());
                        }
                    }
                }
                abortAnimatedScroll();
            }

        } else if (!horizontal && unconsumedY != 0) {
            // Internal Scroll
            final int oldScrollY = getScrollY();
            overScrollByCompat(0, unconsumedY, getScrollX(), oldScrollY, 0, range, 0, 0, false);
            final int scrolledYByMe = getScrollY() - oldScrollY;
            unconsumedY -= scrolledYByMe;

            // Nested Scrolling Post Pass
            mScrollConsumed[1] = 0;
            dispatchNestedScroll(0, scrolledYByMe, 0, unconsumedY, mScrollOffset, ViewCompat.TYPE_NON_TOUCH, mScrollConsumed);
            unconsumedY -= mScrollConsumed[1];

            if (unconsumedY != 0) {
                final int mode = getOverScrollMode();
                final boolean canOverscroll = mode == OVER_SCROLL_ALWAYS
                        || (mode == OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0);
                if (canOverscroll) {
                    ensureGlows();
                    if (unconsumedY < 0) {
                        if (mEdgeGlowTop.isFinished()) {
                            mEdgeGlowTop.onAbsorb((int) mScroller.getCurrVelocity());
                        }
                    } else {
                        if (mEdgeGlowBottom.isFinished()) {
                            mEdgeGlowBottom.onAbsorb((int) mScroller.getCurrVelocity());
                        }
                    }
                }
                abortAnimatedScroll();
            }
        }

        if (!mScroller.isFinished()) {
            ViewCompat.postInvalidateOnAnimation(this);
        } else {
            stopNestedScroll(ViewCompat.TYPE_NON_TOUCH);
        }
    }

    private void runAnimatedScroll(boolean participateInNestedScrolling) {
        if (participateInNestedScrolling) {
            startNestedScroll(getScrollAxes(), ViewCompat.TYPE_NON_TOUCH);
        } else {
            stopNestedScroll(ViewCompat.TYPE_NON_TOUCH);
        }
        if (getOrientation() == HORIZONTAL) {
            mLastScrollerX = getScrollX();
        } else {
            mLastScrollerY = getScrollY();
        }
        ViewCompat.postInvalidateOnAnimation(this);
    }

    private void abortAnimatedScroll() {
        mScroller.abortAnimation();
        stopNestedScroll(ViewCompat.TYPE_NON_TOUCH);
    }

    /**
     * Scrolls the view to the given child.
     *
     * @param child the View to scroll to
     */
    private void scrollToChild(View child) {
        child.getDrawingRect(mTempRect);

        /* Offset from child's local coordinates to ScrollView coordinates */
        offsetDescendantRectToMyCoords(child, mTempRect);

        int scrollDeltaX = 0, scrollDeltaY = 0;
        if (getOrientation() == HORIZONTAL) {
            scrollDeltaX = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
        } else {
            scrollDeltaY = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
        }

        if (scrollDeltaX != 0 || scrollDeltaY != 0) {
            scrollBy(scrollDeltaX, scrollDeltaY);
        }
    }

    /**
     * If rect is off screen, scroll just enough to get it (or at least the
     * first screen size chunk of it) on screen.
     *
     * @param rect      The rectangle.
     * @param immediate True to scroll immediately without animation
     * @return true if scrolling was performed
     */
    private boolean scrollToChildRect(Rect rect, boolean immediate) {
        int scrollDeltaX = 0, scrollDeltaY = 0;
        if (getOrientation() == HORIZONTAL) {
            scrollDeltaX = computeScrollDeltaToGetChildRectOnScreen(rect);
        } else {
            scrollDeltaY = computeScrollDeltaToGetChildRectOnScreen(rect);
        }
        final boolean scroll = scrollDeltaX != 0 || scrollDeltaY != 0;
        if (scroll) {
            if (immediate) {
                scrollBy(scrollDeltaX, scrollDeltaY);
            } else {
                smoothScrollBy(scrollDeltaX, scrollDeltaY);
            }
        }
        return scroll;
    }

    /**
     * Compute the amount to scroll in the Y direction in order to get
     * a rectangle completely on the screen (or, if taller than the screen,
     * at least the first screen size chunk of it).
     *
     * @param rect The rect.
     * @return The scroll delta.
     */
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        if (getChildCount() == 0) return 0;

        if (getOrientation() == HORIZONTAL) {
            int width = getWidth();
            int screenLeft = getScrollX();
            int screenRight = screenLeft + width;
            final int actualScreenRight = screenRight;

            int fadingEdge = getHorizontalFadingEdgeLength();

            // leave room for left fading edge as long as rect isn't at very left
            if (rect.left > 0) {
                screenLeft += fadingEdge;
            }

            // leave room for right fading edge as long as rect isn't at very right
            if (rect.right < getChildContentWidth()) {
                screenRight -= fadingEdge;
            }

            int scrollXDelta = 0;

            if (rect.right > screenRight && rect.left > screenLeft) {
                // need to move right to get it in view: move right just enough so
                // that the entire rectangle is in view (or at least the first
                // screen size chunk).

                if (rect.width() > width) {
                    // just enough to get screen size chunk on
                    scrollXDelta += (rect.left - screenLeft);
                } else {
                    // get entire rect at right of screen
                    scrollXDelta += (rect.right - screenRight);
                }

                // make sure we aren't scrolling beyond the end of our content
                View lastChild = getChildAt(getChildCount() - 1);
                MarginLayoutParams lp = (MarginLayoutParams) lastChild.getLayoutParams();
                int right = lastChild.getRight() + lp.rightMargin;
                int distanceToRight = right - actualScreenRight;
                scrollXDelta = Math.min(scrollXDelta, distanceToRight);

            } else if (rect.left < screenLeft && rect.right < screenRight) {
                // need to move right to get it in view: move right just enough so that
                // entire rectangle is in view (or at least the first screen
                // size chunk of it).

                if (rect.width() > width) {
                    // screen size chunk
                    scrollXDelta -= (screenRight - rect.right);
                } else {
                    // entire rect at left
                    scrollXDelta -= (screenLeft - rect.left);
                }

                // make sure we aren't scrolling any further than the left our content
                scrollXDelta = Math.max(scrollXDelta, -getScrollX());
            }
            return scrollXDelta;
        } else {
            int height = getHeight();
            int screenTop = getScrollY();
            int screenBottom = screenTop + height;
            final int actualScreenBottom = screenBottom;

            int fadingEdge = getVerticalFadingEdgeLength();

            // leave room for top fading edge as long as rect isn't at very top
            if (rect.top > 0) {
                screenTop += fadingEdge;
            }

            // leave room for bottom fading edge as long as rect isn't at very bottom
            if (rect.bottom < getChildContentHeight()) {
                screenBottom -= fadingEdge;
            }

            int scrollYDelta = 0;

            if (rect.bottom > screenBottom && rect.top > screenTop) {
                // need to move down to get it in view: move down just enough so
                // that the entire rectangle is in view (or at least the first
                // screen size chunk).

                if (rect.height() > height) {
                    // just enough to get screen size chunk on
                    scrollYDelta += (rect.top - screenTop);
                } else {
                    // get entire rect at bottom of screen
                    scrollYDelta += (rect.bottom - screenBottom);
                }

                // make sure we aren't scrolling beyond the end of our content
                View lastChild = getChildAt(getChildCount() - 1);
                MarginLayoutParams lp = (MarginLayoutParams) lastChild.getLayoutParams();
                int bottom = lastChild.getBottom() + lp.bottomMargin;
                int distanceToBottom = bottom - actualScreenBottom;
                scrollYDelta = Math.min(scrollYDelta, distanceToBottom);

            } else if (rect.top < screenTop && rect.bottom < screenBottom) {
                // need to move up to get it in view: move up just enough so that
                // entire rectangle is in view (or at least the first screen
                // size chunk of it).

                if (rect.height() > height) {
                    // screen size chunk
                    scrollYDelta -= (screenBottom - rect.bottom);
                } else {
                    // entire rect at top
                    scrollYDelta -= (screenTop - rect.top);
                }

                // make sure we aren't scrolling any further than the top our content
                scrollYDelta = Math.max(scrollYDelta, -getScrollY());
            }
            return scrollYDelta;
        }
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        if (mScrollable) {
            if (!mIsLayoutDirty) {
                scrollToChild(focused);
            } else {
                // The child may not be laid out yet, we can't compute the scroll yet
                mChildToScrollTo = focused;
            }
        }
        super.requestChildFocus(child, focused);
    }


    /**
     * When looking for focus in children of a scroll view, need to be a little
     * more careful not to give focus to something that is scrolled off screen.
     * <p>
     * This is more expensive than the default {@link android.view.ViewGroup}
     * implementation, otherwise this behavior might have been made the default.
     */
    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        if (!mScrollable) {
            return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
        }
        final boolean horizontal = getOrientation() == HORIZONTAL;
        // convert from forward / backward notation to up / down / left / right
        // (ugh).
        if (direction == View.FOCUS_FORWARD) {
            direction = horizontal ? View.FOCUS_RIGHT : View.FOCUS_DOWN;
        } else if (direction == View.FOCUS_BACKWARD) {
            direction = horizontal ? View.FOCUS_LEFT : View.FOCUS_UP;
        }

        final View nextFocus = previouslyFocusedRect == null
                ? FocusFinder.getInstance().findNextFocus(this, null, direction)
                : FocusFinder.getInstance().findNextFocusFromRect(this, previouslyFocusedRect, direction);

        if (nextFocus == null) {
            return false;
        }

        if (isOffScreen(nextFocus)) {
            return false;
        }

        return nextFocus.requestFocus(direction, previouslyFocusedRect);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        if (mScrollable) {
            // offset into coordinate space of this scroll view
            rectangle.offset(child.getLeft() - child.getScrollX(),
                    child.getTop() - child.getScrollY());

            return scrollToChildRect(rectangle, immediate);
        } else {
            return super.requestChildRectangleOnScreen(child, rectangle, immediate);
        }
    }

    @Override
    public void requestLayout() {
        if (mScrollable) {
            mIsLayoutDirty = true;
        }
        super.requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mScrollable) {
            mIsLayoutDirty = false;
            // Give a child focus if it needs it
            if (mChildToScrollTo != null && isViewDescendantOf(mChildToScrollTo, this)) {
                scrollToChild(mChildToScrollTo);
            }
            mChildToScrollTo = null;

            int newScrollX = getScrollX();
            int newScrollY = getScrollY();

            if (!mIsLaidOut) {
                final boolean horizontal = getOrientation() == HORIZONTAL;
                if (mSavedState != null) {
                    if (horizontal) {
                        newScrollX = mSavedState.scrollPositionX;
                    } else {
                        newScrollY = mSavedState.scrollPositionY;
                    }
                    mSavedState = null;
                } // mScrollY default value is "0"

                // Don't forget to clamp
                if (horizontal) {
                    int childrenWidth = getChildContentWidth();
                    int parentWidth = r - l - getPaddingLeft() - getPaddingRight();
                    newScrollX = clamp(newScrollX, parentWidth, childrenWidth);
                } else {
                    int childrenHeight = getChildContentHeight();
                    int parentHeight = b - t - getPaddingBottom() - getPaddingTop();
                    newScrollY = clamp(newScrollY, parentHeight, childrenHeight);
                }
            }

            // Calling this with the present values causes it to re-claim them
            scrollTo(newScrollX, newScrollY);
            mIsLaidOut = true;
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mScrollable) {
            mIsLaidOut = false;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        View currentFocused = findFocus();
        if (mScrollable && currentFocused != null && currentFocused != this) {
            // If the currently-focused view was visible on the screen when the
            // screen was at the old height, then scroll the screen to make that
            // view visible with the new screen height.
            final boolean horizontal = getOrientation() == HORIZONTAL;
            int delta = horizontal ? getRight() - getLeft() : 0;
            if (isWithinDeltaOfScreen(currentFocused, delta, oldh, getWidth())) {
                currentFocused.getDrawingRect(mTempRect);
                offsetDescendantRectToMyCoords(currentFocused, mTempRect);
                int scrollDeltaX = 0, scrollDeltaY = 0;
                if (horizontal) {
                    scrollDeltaX = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
                } else {
                    scrollDeltaY = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
                }
                doScroll(scrollDeltaX, scrollDeltaY);
            }
        }
    }

    /**
     * Return true if child is a descendant of parent, (or equal to the parent).
     */
    private static boolean isViewDescendantOf(View child, View parent) {
        if (child == parent) {
            return true;
        }

        final ViewParent theParent = child.getParent();
        return (theParent instanceof ViewGroup) && isViewDescendantOf((View) theParent, parent);
    }

    /**
     * Fling the scroll view
     *
     * @param velocityX The initial velocity in the Y direction. Positive
     *                  numbers mean that the finger/cursor is moving down the screen,
     *                  which means we want to scroll towards the top.
     */
    public void flingX(int velocityX) {
        if (getChildCount() > 0) {
            mScroller.fling(getScrollX(), getScrollY(), // start
                    velocityX, 0, // velocities
                    Integer.MIN_VALUE, Integer.MAX_VALUE, // x
                    0, 0, // y
                    0, 0); // overscroll

            // 兼容HorizontalScrollView的滚动逻辑
//            int width = getWidth() - getPaddingRight() - getPaddingLeft();
//            int right = getChildContentWidth();
//            mScroller.fling(getScrollX(), getScrollY(),
//                    velocityX, 0,
//                    0, Math.max(0, right - width),
//                    0, 0,
//                    width / 2, 0);

            runAnimatedScroll(true);
        }
    }

    public void flingY(int velocityY) {
        if (getChildCount() > 0) {
            mScroller.fling(getScrollX(), getScrollY(), // start
                    0, velocityY, // velocities
                    0, 0, // x
                    Integer.MIN_VALUE, Integer.MAX_VALUE, // y
                    0, 0); // overscroll
            runAnimatedScroll(true);
        }
    }

    private void flingXWithNestedDispatch(int velocityX) {
        final int scrollX = getScrollX();
        final boolean canFling = (scrollX > 0 || velocityX > 0)
                && (scrollX < getScrollRange() || velocityX < 0);
        if (!dispatchNestedPreFling(velocityX, 0)) {
            dispatchNestedFling(velocityX, 0, canFling);
            flingX(velocityX);
        }
    }

    private void flingYWithNestedDispatch(int velocityY) {
        final int scrollY = getScrollY();
        final boolean canFling = (scrollY > 0 || velocityY > 0)
                && (scrollY < getScrollRange() || velocityY < 0);
        if (!dispatchNestedPreFling(0, velocityY)) {
            dispatchNestedFling(0, velocityY, canFling);
            flingY(velocityY);
        }
    }

    private void endDrag() {
        mIsBeingDragged = false;

        recycleVelocityTracker();
        stopNestedScroll(ViewCompat.TYPE_TOUCH);

        if (mEdgeGlowLeft != null) {
            mEdgeGlowLeft.onRelease();
            mEdgeGlowRight.onRelease();
        }
        if (mEdgeGlowTop != null) {
            mEdgeGlowTop.onRelease();
            mEdgeGlowBottom.onRelease();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>This version also clamps the scrolling to the bounds of our child.
     */
    @Override
    public void scrollTo(int x, int y) {
        // we rely on the fact the View.scrollBy calls scrollTo.
        if (mScrollable && getChildCount() > 0) {
            x = clamp(x, getWidth() - getPaddingRight() - getPaddingLeft(), getChildContentWidth());
            y = clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(), getChildContentHeight());
            if (x != getScrollX() || y != getScrollY()) {
                super.scrollTo(x, y);
            }
        }
    }

    private void ensureGlows() {
        if (getOverScrollMode() != View.OVER_SCROLL_NEVER) {
            boolean horizontal = getOrientation() == HORIZONTAL;
            if (horizontal && mEdgeGlowLeft == null) {
                Context context = getContext();
                mEdgeGlowLeft = new EdgeEffect(context);
                mEdgeGlowRight = new EdgeEffect(context);
            } else if (!horizontal && mEdgeGlowTop == null) {
                Context context = getContext();
                mEdgeGlowTop = new EdgeEffect(context);
                mEdgeGlowBottom = new EdgeEffect(context);
            }
        } else {
            mEdgeGlowLeft = null;
            mEdgeGlowRight = null;
            mEdgeGlowTop = null;
            mEdgeGlowBottom = null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mScrollable && (mEdgeGlowTop != null || mEdgeGlowLeft != null)) {
            final int scrollX = getScrollX();
            if (mEdgeGlowLeft != null && !mEdgeGlowLeft.isFinished()) {
                final int restoreCount = canvas.save();
                final int height = getHeight() - getPaddingTop() - getPaddingBottom();

                canvas.rotate(270);
                canvas.translate(-height + getPaddingTop(), Math.min(0, scrollX));
                mEdgeGlowLeft.setSize(height, getWidth());
                if (mEdgeGlowLeft.draw(canvas)) {
                    ViewCompat.postInvalidateOnAnimation(this);
                }
                canvas.restoreToCount(restoreCount);
            }
            if (mEdgeGlowRight != null && !mEdgeGlowRight.isFinished()) {
                final int restoreCount = canvas.save();
                final int width = getWidth();
                final int height = getHeight() - getPaddingTop() - getPaddingBottom();

                canvas.rotate(90);
                canvas.translate(-getPaddingTop(), -(Math.max(getScrollRange(), scrollX) + width));
                mEdgeGlowRight.setSize(height, width);
                if (mEdgeGlowRight.draw(canvas)) {
                    ViewCompat.postInvalidateOnAnimation(this);
                }
                canvas.restoreToCount(restoreCount);
            }

            final int scrollY = getScrollY();
            if (mEdgeGlowTop != null && !mEdgeGlowTop.isFinished()) {
                final int restoreCount = canvas.save();
                int width = getWidth();
                int height = getHeight();
                int xTranslation = 0;
                int yTranslation = Math.min(0, scrollY);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || getClipToPadding()) {
                    width -= getPaddingLeft() + getPaddingRight();
                    xTranslation += getPaddingLeft();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && getClipToPadding()) {
                    height -= getPaddingTop() + getPaddingBottom();
                    yTranslation += getPaddingTop();
                }
                canvas.translate(xTranslation, yTranslation);
                mEdgeGlowTop.setSize(width, height);
                if (mEdgeGlowTop.draw(canvas)) {
                    ViewCompat.postInvalidateOnAnimation(this);
                }
                canvas.restoreToCount(restoreCount);
            }
            if (mEdgeGlowBottom != null && !mEdgeGlowBottom.isFinished()) {
                final int restoreCount = canvas.save();
                int width = getWidth();
                int height = getHeight();
                int xTranslation = 0;
                int yTranslation = Math.max(getScrollRange(), scrollY) + height;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || getClipToPadding()) {
                    width -= getPaddingLeft() + getPaddingRight();
                    xTranslation += getPaddingLeft();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && getClipToPadding()) {
                    height -= getPaddingTop() + getPaddingBottom();
                    yTranslation -= getPaddingBottom();
                }
                canvas.translate(xTranslation - width, yTranslation);
                canvas.rotate(180, width, 0);
                mEdgeGlowBottom.setSize(width, height);
                if (mEdgeGlowBottom.draw(canvas)) {
                    ViewCompat.postInvalidateOnAnimation(this);
                }
                canvas.restoreToCount(restoreCount);
            }
        }
    }

    private static int clamp(int n, int my, int child) {
        if (my >= child || n < 0) {
            /* my >= child is this case:
             *                    |--------------- me ---------------|
             *     |------ child ------|
             * or
             *     |--------------- me ---------------|
             *            |------ child ------|
             * or
             *     |--------------- me ---------------|
             *                                  |------ child ------|
             *
             * n < 0 is this case:
             *     |------ me ------|
             *                    |-------- child --------|
             *     |-- mScrollX --|
             */
            return 0;
        }
        if ((my + n) > child) {
            /* this case:
             *                    |------ me ------|
             *     |------ child ------|
             *     |-- mScrollX --|
             */
            return child - my;
        }
        return n;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        mSavedState = ss;
        setOrientation(mSavedState.orientation);
        setScrollable(mSavedState.scrollable);
        requestLayout();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.orientation = getOrientation();
        ss.scrollable = isScrollable();
        ss.scrollPositionX = getScrollX();
        ss.scrollPositionY = getScrollY();
        return ss;
    }

    static class SavedState extends BaseSavedState {

        public int orientation;
        public boolean scrollable;
        public int scrollPositionX;
        public int scrollPositionY;

        SavedState(Parcelable superState) {
            super(superState);
        }

        SavedState(Parcel source) {
            super(source);
            orientation = source.readInt();
            scrollable = source.readInt() == 1;
            scrollPositionX = source.readInt();
            scrollPositionY = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(orientation);
            dest.writeInt(scrollable ? 1 : 0);
            dest.writeInt(scrollPositionX);
            dest.writeInt(scrollPositionY);
        }

        @Override
        public String toString() {
            return "ScrollLinearLayout.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " scrollPosition=" + scrollPositionX + "}";
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    static class AccessibilityDelegate extends AccessibilityDelegateCompat {
        @Override
        public boolean performAccessibilityAction(View host, int action, Bundle arguments) {
            if (super.performAccessibilityAction(host, action, arguments)) {
                return true;
            }
            final ScrollLinearLayout nsvHost = (ScrollLinearLayout) host;
            if (!nsvHost.isEnabled()) {
                return false;
            }
            switch (action) {
                case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD:
                case android.R.id.accessibilityActionScrollDown: {
                    if (nsvHost.getOrientation() == HORIZONTAL) {
                        final int viewportWidth = nsvHost.getWidth() - nsvHost.getPaddingLeft() - nsvHost.getPaddingRight();
                        final int targetScrollX = Math.min(nsvHost.getScrollX() + viewportWidth, nsvHost.getScrollRange());
                        if (targetScrollX != nsvHost.getScrollX()) {
                            nsvHost.smoothScrollTo(targetScrollX, 0, true);
                            return true;
                        }
                    } else {
                        final int viewportHeight = nsvHost.getHeight() - nsvHost.getPaddingBottom() - nsvHost.getPaddingTop();
                        final int targetScrollY = Math.min(nsvHost.getScrollY() + viewportHeight, nsvHost.getScrollRange());
                        if (targetScrollY != nsvHost.getScrollY()) {
                            nsvHost.smoothScrollTo(0, targetScrollY, true);
                            return true;
                        }
                    }
                }
                return false;
                case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD:
                case android.R.id.accessibilityActionScrollUp: {
                    if (nsvHost.getOrientation() == HORIZONTAL) {
                        final int viewportWidth = nsvHost.getWidth() - nsvHost.getPaddingLeft() - nsvHost.getPaddingRight();
                        final int targetScrollX = Math.max(nsvHost.getScrollX() - viewportWidth, 0);
                        if (targetScrollX != nsvHost.getScrollX()) {
                            nsvHost.smoothScrollTo(targetScrollX, 0, true);
                            return true;
                        }
                    } else {
                        final int viewportHeight = nsvHost.getHeight() - nsvHost.getPaddingBottom() - nsvHost.getPaddingTop();
                        final int targetScrollY = Math.max(nsvHost.getScrollY() - viewportHeight, 0);
                        if (targetScrollY != nsvHost.getScrollY()) {
                            nsvHost.smoothScrollTo(0, targetScrollY, true);
                            return true;
                        }
                    }
                }
                return false;
            }
            return false;
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            final ScrollLinearLayout nsvHost = (ScrollLinearLayout) host;
            info.setClassName(ScrollView.class.getName());
            if (nsvHost.isEnabled()) {
                final int scrollRange = nsvHost.getScrollRange();
                if (scrollRange > 0) {
                    info.setScrollable(nsvHost.isScrollable());
                    boolean horizontal = nsvHost.getOrientation() == HORIZONTAL;
                    boolean neeCompat = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
                    if (horizontal && nsvHost.getScrollX() > 0) {
                        if (neeCompat) {
                            info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
                        }
                        info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_BACKWARD);
                        info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_UP);

                    }
                    if (horizontal && nsvHost.getScrollX() < scrollRange) {
                        if (neeCompat) {
                            info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
                        }
                        info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_FORWARD);
                        info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_DOWN);
                    }

                    if (!horizontal && nsvHost.getScrollY() > 0) {
                        if (neeCompat) {
                            info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
                        }
                        info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_BACKWARD);
                        info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_UP);

                    }
                    if (!horizontal && nsvHost.getScrollY() < scrollRange) {
                        if (neeCompat) {
                            info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
                        }
                        info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_FORWARD);
                        info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_DOWN);
                    }
                }
            }
        }

        @Override
        public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(host, event);
            final ScrollLinearLayout nsvHost = (ScrollLinearLayout) host;
            event.setClassName(ScrollView.class.getName());
            final boolean scrollable = nsvHost.isScrollable() && nsvHost.getScrollRange() > 0;
            event.setScrollable(scrollable);
            event.setScrollX(nsvHost.getScrollX());
            event.setScrollY(nsvHost.getScrollY());
            if (nsvHost.getOrientation() == HORIZONTAL) {
                AccessibilityRecordCompat.setMaxScrollX(event, nsvHost.getScrollRange());
                AccessibilityRecordCompat.setMaxScrollY(event, nsvHost.getScrollY());
            } else {
                AccessibilityRecordCompat.setMaxScrollX(event, nsvHost.getScrollX());
                AccessibilityRecordCompat.setMaxScrollY(event, nsvHost.getScrollRange());
            }

        }
    }

    private static int restrainIntRange(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

}
