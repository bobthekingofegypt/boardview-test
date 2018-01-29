package org.bobstuff.boardview.boardview;

import android.graphics.Rect;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.animation.Interpolator;

/**
 * Created by bob on 15/01/18.
 */

public class AutoScroller {
    public interface ScrollCallback {
        void onScrolled(int accumulatedScroll);
    }
    private RecyclerView mRecyclerView;
    private int mDx;
    private int mDy;
    private long mDragScrollStartTimeInMs;
    private boolean mTracking;
    private int mCachedMaxScrollSpeed = -1;
    private int scrollZone;
    private int mPercentageScrollZone;
    private ScrollCallback scrollCallback;

    final Runnable mScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (scrollIfNecessary()) {
                scrollCallback.onScrolled(scrollAccumulated);
                mRecyclerView.removeCallbacks(mScrollRunnable);
                ViewCompat.postOnAnimation(mRecyclerView, this);
            }
        }
    };

    public AutoScroller(RecyclerView recyclerView, int percentageScrollZone, ScrollCallback callback) {
        this.mRecyclerView = recyclerView;
        this.mPercentageScrollZone = percentageScrollZone;
        this.scrollCallback = callback;
    }

    public void startTracking() {
        Log.d("TEST", "Start tracking");
        this.scrollZone = (int)(mRecyclerView.getWidth() * (mPercentageScrollZone/100.0));
        mDragScrollStartTimeInMs = Long.MIN_VALUE;
        mTracking = true;
        //mScrollRunnable.run();
    }

    public void stopTracking() {
        mDragScrollStartTimeInMs = Long.MIN_VALUE;
        mTracking = false;
    }

    public void updateDrag(float x, float y) {
        mDx = (int)x;
        mDy = (int)y;
        mScrollRunnable.run();
    }

    private static final long DRAG_SCROLL_ACCELERATION_LIMIT_TIME_MS = 3000;

    private static final Interpolator sDragScrollInterpolator = new Interpolator() {
        @Override
        public float getInterpolation(float t) {
            return t * t * t * t * t;
        }
    };

    private static final Interpolator sDragViewScrollCapInterpolator = new Interpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    private int getMaxDragScroll(RecyclerView recyclerView) {
        if (mCachedMaxScrollSpeed == -1) {
            mCachedMaxScrollSpeed = recyclerView.getResources().getDimensionPixelSize(
                    android.support.v7.recyclerview.R.dimen.item_touch_helper_max_drag_scroll_per_frame)/6;
        }
        return mCachedMaxScrollSpeed;
    }

    public int interpolateOutOfBoundsScroll(RecyclerView recyclerView,
                                            int scrollZone,
                                            int scrollOverlap,
                                            long msSinceStartScroll) {
        final int maxScroll = getMaxDragScroll(recyclerView);
        //final int absOutOfBounds = Math.abs(viewSizeOutOfBounds);
        //final int direction = (int) Math.signum(viewSizeOutOfBounds);
        // might be negative if other direction
        //float outOfBoundsRatio = Math.min(1f, 1f * absOutOfBounds / viewSize);
        float outOfBoundsRatio = Math.min(1f, 1f * scrollOverlap / scrollZone);
        Log.d("TEST", "outofboundsratio: " + outOfBoundsRatio);
        final int cappedScroll = (int) (maxScroll
                * sDragViewScrollCapInterpolator.getInterpolation(outOfBoundsRatio));
        final float timeRatio;
        if (msSinceStartScroll > DRAG_SCROLL_ACCELERATION_LIMIT_TIME_MS) {
            timeRatio = 1f;
        } else {
            timeRatio = (float) msSinceStartScroll / DRAG_SCROLL_ACCELERATION_LIMIT_TIME_MS;
        }
        final int value = (int) (cappedScroll * sDragScrollInterpolator
                .getInterpolation(timeRatio));

        //Log.d("TEST", "maxscroll: " + maxScroll + "; cappedScroll: " + cappedScroll + "; timeRatio: " + timeRatio);
        if (value == 0) {
            return 1;
        }
        //if (value == 0) {
        //    return viewSizeOutOfBounds > 0 ? 1 : -1;
        //}
        return value;
    }

    private int scrollAccumulated;

    boolean scrollIfNecessary() {
        //Log.d("TEST", "scrollifnecessary");
        if (!mTracking) {
            mDragScrollStartTimeInMs = Long.MIN_VALUE;
            return false;
        }
        final long now = System.currentTimeMillis();
        final long scrollDuration = mDragScrollStartTimeInMs
                == Long.MIN_VALUE ? 0 : now - mDragScrollStartTimeInMs;
        int horizontalScrollOffset = mRecyclerView.computeHorizontalScrollOffset();
        int scrollX = 0;
        int scrollY = 0;
        RecyclerView.LayoutManager lm = mRecyclerView.getLayoutManager();
        int curX = (int) (mDx - horizontalScrollOffset);
        int width = mRecyclerView.getWidth();

        //Log.d("BOB", "mDx: " + mDx + "; canScroll: " + mRecyclerView.canScrollHorizontally(1));
        if (mDx < scrollZone && mRecyclerView.canScrollHorizontally(-1)) {
            scrollX = -interpolateOutOfBoundsScroll(mRecyclerView, scrollZone, mDx, scrollDuration);
        } else if (mDx > (width - scrollZone) && mRecyclerView.canScrollHorizontally(1)) {
            scrollX = interpolateOutOfBoundsScroll(mRecyclerView, scrollZone, mDx - (width - scrollZone), scrollDuration);
            //Log.d("TEST", "scrollX: " + scrollX);
        }

        if (scrollX != 0 || scrollY != 0) {
            if (mDragScrollStartTimeInMs == Long.MIN_VALUE) {
                mDragScrollStartTimeInMs = now;
            }
            scrollAccumulated += scrollX;
            mRecyclerView.scrollBy(scrollX, scrollY);
            return true;
        }
        mDragScrollStartTimeInMs = Long.MIN_VALUE;
        scrollAccumulated = 0;
        return false;
    }
}
