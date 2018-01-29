package org.bobstuff.boardview.boardview;

import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.Interpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * This is based on the DragItemTouchHelper from RecyclerView library, modified a bit to work the
 * way I wanted.
 *
 * Created by bob on 03/01/18.
 */

public class DragItemTouchHelper
        implements RecyclerView.OnChildAttachStateChangeListener {
    /**
     * CustomItemTouchHelper is in idle state. At this state, either there is no related motion event by
     * the user or latest motion events have not yet triggered a swipe or drag.
     */
    public static final int ACTION_STATE_IDLE = 0;
    /**
     * A View is currently being dragged.
     */
    public static final int ACTION_STATE_DRAG = 1;

    static final boolean DEBUG = false;
    static final String TAG = "DragItemTouchHelper";

    RecyclerView mRecyclerView;
    /**
     * Current mode.
     */
    int mActionState = ACTION_STATE_IDLE;
    /**
     * The reference coordinates for the action start. For drag & drop, this is the time long
     * press is completed vs for swipe, this is the initial touch point.
     */
    float mInitialTouchX;
    float mInitialTouchY;
    /**
     * The diff between the last event and initial touch.
     */
    float mDx;
    float mDy;
    /**
     * The coordinates of the selected view at the time it is selected. We record these values
     * when action starts so that we can consistently position it even if LayoutManager moves the
     * View.
     */
    float mSelectedStartX;

    float mSelectedStartY;
    /**
     * Currently selected view holder
     */
    public RecyclerView.ViewHolder mSelected = null;
    /**
     * When user started to drag scroll. Reset when we don't scroll
     */
    private long mDragScrollStartTimeInMs;
    //re-used list for selecting a swap target
    private List<RecyclerView.ViewHolder> mSwapTargets;
    //re used for for sorting swap targets
    private List<Integer> mDistances;
    /**
     * Temporary rect instance that is used when we need to lookup Item decorations.
     */
    private Rect mTmpRect;
    /**
     * Developer callback which controls the behavior of CustomItemTouchHelper.
     */
    DragItemTouchHelper.Callback mCallback;
    /**
     * When user drags a view to the edge, we start scrolling the LayoutManager as long as View
     * is partially out of bounds.
     */
    final Runnable mScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (mSelected != null && scrollIfNecessary()) {
                if (mSelected != null) { //it might be lost during scrolling
                    moveIfNecessary(mSelected);
                }
                mRecyclerView.removeCallbacks(mScrollRunnable);
                ViewCompat.postOnAnimation(mRecyclerView, this);
            }
        }
    };

    public DragItemTouchHelper(Callback callback) {
        mCallback = callback;
    }

    public void attachToRecyclerView(@Nullable RecyclerView recyclerView) {
        if (mRecyclerView == recyclerView) {
            return; // nothing to do
        }
        if (mRecyclerView != null) {
            destroyCallbacks();
        }
        mRecyclerView = recyclerView;
        if (mRecyclerView != null) {
            setupCallbacks();
        }
    }

    @Override
    public void onChildViewAttachedToWindow(View view) {
        //no-op
    }

    @Override
    public void onChildViewDetachedFromWindow(View view) {
        final RecyclerView.ViewHolder holder = mRecyclerView.getChildViewHolder(view);
        if (holder == null) {
            return;
        }
        if (mSelected != null && holder == mSelected) {
            select(null, ACTION_STATE_IDLE);
        }
    }

    private void setupCallbacks() {
        mRecyclerView.addOnChildAttachStateChangeListener(this);
    }

    private void destroyCallbacks() {
        mRecyclerView.removeOnChildAttachStateChangeListener(this);
        mRecyclerView.removeCallbacks(mScrollRunnable);
    }

    public void startDrag(RecyclerView.ViewHolder viewHolder, float x, float y) {
        if (viewHolder.itemView.getParent() != mRecyclerView) {
            Log.e(TAG, "Start drag has been called with a view holder which is not a child of "
                    + "the RecyclerView which is controlled by this CustomItemTouchHelper.");
            return;
        }
        mInitialTouchX = x;
        mInitialTouchY = y;
        mDx = mDy = 0f;
        select(viewHolder, ACTION_STATE_DRAG);
    }

    public void startDragManual(RecyclerView.ViewHolder viewHolder) {
        if (viewHolder.itemView.getParent() != mRecyclerView) {
            Log.e(TAG, "Start drag has been called with a view holder which is not a child of "
                    + "the RecyclerView which is controlled by this CustomItemTouchHelper.");
            return;
        }
        viewHolder.itemView.setVisibility(View.INVISIBLE);
        mDx = mDy = 0f;
        select(viewHolder, ACTION_STATE_DRAG);
    }

    public void endDrag() {
        if (mSelected != null) {
            mSelected.itemView.setVisibility(View.VISIBLE);
        }
        select(null, ACTION_STATE_IDLE);
    }

    public void drag(float x, float y) {
        if (mRecyclerView == null) {
            return;
        }
        updateDxDy(x, y);
        moveIfNecessary(mSelected);
        mRecyclerView.removeCallbacks(mScrollRunnable);
        mScrollRunnable.run();
        mRecyclerView.invalidate();
    }

    private void updateDxDy(float x, float y) {
        mDx = x - mInitialTouchX;
        mDy = y - mInitialTouchY;
    }

    /**
     * Starts dragging or swiping the given View. Call with null if you want to clear it.
     *
     * @param selected    The ViewHolder to drag or swipe. Can be null if you want to cancel the
     *                    current action
     * @param actionState The type of action
     */
    void select(RecyclerView.ViewHolder selected, int actionState) {
        if (selected == mSelected && actionState == mActionState) {
            return;
        }
        mDragScrollStartTimeInMs = Long.MIN_VALUE;
        mActionState = actionState;

        if (mSelected != null) {
            mSelected = null;
        }
        if (selected != null) {
            mSelectedStartX = selected.itemView.getLeft();
            mSelectedStartY = selected.itemView.getTop();
            mSelected = selected;

            if (actionState == ACTION_STATE_DRAG) {
                mSelected.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        }
        final ViewParent rvParent = mRecyclerView.getParent();
        if (rvParent != null) {
            rvParent.requestDisallowInterceptTouchEvent(mSelected != null);
        }
        mRecyclerView.invalidate();
    }

    /**
     * Checks if we should swap w/ another view holder.
     */
    void moveIfNecessary(RecyclerView.ViewHolder viewHolder) {
        if (mRecyclerView.isLayoutRequested() || mActionState != ACTION_STATE_DRAG) {
            return;
        }
        final int x = (int)mDx;
        final int y = (int)mDy;

        List<RecyclerView.ViewHolder> swapTargets = findSwapTargets(viewHolder);
        if (swapTargets.size() == 0) {
            return;
        }
        // may swap.
        RecyclerView.ViewHolder target = mCallback.chooseDropTarget(viewHolder, swapTargets, x, y);
        if (target == null) {
            mSwapTargets.clear();
            mDistances.clear();
            return;
        }
        final int toPosition = target.getAdapterPosition();
        final int fromPosition = viewHolder.getAdapterPosition();
        if (mCallback.onMove(mRecyclerView, viewHolder, target)) {
            // keep target visible
            mCallback.onMoved(mRecyclerView, viewHolder, fromPosition,
                    target, toPosition, x, y);
        }
    }

    private List<RecyclerView.ViewHolder> findSwapTargets(RecyclerView.ViewHolder viewHolder) {
        if (mSwapTargets == null) {
            mSwapTargets = new ArrayList<>();
            mDistances = new ArrayList<>();
        } else {
            mSwapTargets.clear();
            mDistances.clear();
        }
        final int margin = mCallback.getBoundingBoxMargin();
        final int left = Math.round(mDx) - margin;
        final int top = Math.round(mDy) - margin;
        final int right = left + viewHolder.itemView.getWidth() + 2 * margin;
        final int bottom = top + viewHolder.itemView.getHeight() + 2 * margin;
        final int centerX = (left + right) / 2;
        final int centerY = (top + bottom) / 2;
        final RecyclerView.LayoutManager lm = mRecyclerView.getLayoutManager();
        final int childCount = lm.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View other = lm.getChildAt(i);
            if (other == viewHolder.itemView) {
                continue;
            }
            if (other.getBottom() < top || other.getTop() > bottom
                    || other.getRight() < left || other.getLeft() > right) {
                continue;
            }
            final RecyclerView.ViewHolder otherVh = mRecyclerView.getChildViewHolder(other);
            if (mCallback.canDropOver(mRecyclerView, mSelected, otherVh)) {
                // find the index to add
                final int dx = Math.abs(centerX - (other.getLeft() + other.getRight()) / 2);
                final int dy = Math.abs(centerY - (other.getTop() + other.getBottom()) / 2);
                final int dist = dx * dx + dy * dy;

                int pos = 0;
                final int cnt = mSwapTargets.size();
                for (int j = 0; j < cnt; j++) {
                    if (dist > mDistances.get(j)) {
                        pos++;
                    } else {
                        break;
                    }
                }
                mSwapTargets.add(pos, otherVh);
                mDistances.add(pos, dist);
            }
        }
        return mSwapTargets;
    }

    /**
     * If user drags the view to the edge, trigger a scroll if necessary.
     */
    boolean scrollIfNecessary() {
        if (mSelected == null) {
            mDragScrollStartTimeInMs = Long.MIN_VALUE;
            return false;
        }
        final long now = System.currentTimeMillis();
        final long scrollDuration = mDragScrollStartTimeInMs
                == Long.MIN_VALUE ? 0 : now - mDragScrollStartTimeInMs;
        RecyclerView.LayoutManager lm = mRecyclerView.getLayoutManager();
        if (mTmpRect == null) {
            mTmpRect = new Rect();
        }
        int scrollX = 0;
        int scrollY = 0;
        lm.calculateItemDecorationsForChild(mSelected.itemView, mTmpRect);
        if (lm.canScrollHorizontally()) {
            int curX = (int) (mDx);
            final int leftDiff = curX - mTmpRect.left - mRecyclerView.getPaddingLeft();
            if (mDx < 0 && leftDiff < 0) {
                scrollX = leftDiff;
            } else if (mDx > 0) {
                final int rightDiff =
                        curX + mSelected.itemView.getWidth() + mTmpRect.right
                                - (mRecyclerView.getWidth() - mRecyclerView.getPaddingRight());
                if (rightDiff > 0) {
                    scrollX = rightDiff;
                }
            }
        }
        if (lm.canScrollVertically()) {
            int curY = (int) (mSelectedStartY + mDy);
            final int topDiff = curY - mTmpRect.top - mRecyclerView.getPaddingTop();
            if (mDy < 0 && topDiff < 0) {
                scrollY = topDiff;
            } else if (mDy > 0) {
                final int bottomDiff = curY + mSelected.itemView.getHeight() + mTmpRect.bottom
                        - (mRecyclerView.getHeight() - mRecyclerView.getPaddingBottom());
                if (bottomDiff > 0) {
                    scrollY = bottomDiff;
                }
            }
        }
        if (scrollX != 0) {
            scrollX = mCallback.interpolateOutOfBoundsScroll(mRecyclerView,
                    mSelected.itemView.getWidth(), scrollX,
                    mRecyclerView.getWidth(), scrollDuration);
        }
        if (scrollY != 0) {
            scrollY = mCallback.interpolateOutOfBoundsScroll(mRecyclerView,
                    mSelected.itemView.getHeight(), scrollY,
                    mRecyclerView.getHeight(), scrollDuration);
        }
        if (scrollX != 0 || scrollY != 0) {
            if (mDragScrollStartTimeInMs == Long.MIN_VALUE) {
                mDragScrollStartTimeInMs = now;
            }
            mRecyclerView.scrollBy(scrollX, scrollY);
            return true;
        }
        mDragScrollStartTimeInMs = Long.MIN_VALUE;
        return false;
    }

    @SuppressWarnings("UnusedParameters")
    public abstract static class Callback {
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

        /**
         * Drag scroll speed keeps accelerating until this many milliseconds before being capped.
         */
        private static final long DRAG_SCROLL_ACCELERATION_LIMIT_TIME_MS = 3000;

        private int mCachedMaxScrollSpeed = -1;

        /**
         * Return true if the current ViewHolder can be dropped over the the target ViewHolder.
         * <p>
         * This method is used when selecting drop target for the dragged View. After Views are
         * eliminated either via bounds check or via this method, resulting set of views will be
         * passed to {@link #chooseDropTarget(RecyclerView.ViewHolder, java.util.List, int, int)}.
         * <p>
         * Default implementation returns true.
         *
         * @param recyclerView The RecyclerView to which CustomItemTouchHelper is attached to.
         * @param current      The ViewHolder that user is dragging.
         * @param target       The ViewHolder which is below the dragged ViewHolder.
         * @return True if the dragged ViewHolder can be replaced with the target ViewHolder, false
         * otherwise.
         */
        public boolean canDropOver(RecyclerView recyclerView, RecyclerView.ViewHolder current,
                                   RecyclerView.ViewHolder target) {
            return true;
        }

        /**
         * Called when CustomItemTouchHelper wants to move the dragged item from its old position to
         * the new position.
         * <p>
         * If this method returns true, CustomItemTouchHelper assumes {@code viewHolder} has been moved
         * to the adapter position of {@code target} ViewHolder
         * ({@link RecyclerView.ViewHolder#getAdapterPosition()
         * ViewHolder#getAdapterPosition()}).
         * <p>
         * If you don't support drag & drop, this method will never be called.
         *
         * @param recyclerView The RecyclerView to which CustomItemTouchHelper is attached to.
         * @param viewHolder   The ViewHolder which is being dragged by the user.
         * @param target       The ViewHolder over which the currently active item is being
         *                     dragged.
         * @return True if the {@code viewHolder} has been moved to the adapter position of
         * {@code target}.
         * @see #onMoved(RecyclerView, RecyclerView.ViewHolder, int, RecyclerView.ViewHolder, int, int, int)
         */
        public abstract boolean onMove(RecyclerView recyclerView,
                                       RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target);

        /**
         * When finding views under a dragged view, by default, CustomItemTouchHelper searches for views
         * that overlap with the dragged View. By overriding this method, you can extend or shrink
         * the search box.
         *
         * @return The extra margin to be added to the hit box of the dragged View.
         */
        public int getBoundingBoxMargin() {
            return 0;
        }

        /**
         * Called by CustomItemTouchHelper to select a drop target from the list of ViewHolders that
         * are under the dragged View.
         * <p>
         * Default implementation filters the View with which dragged item have changed position
         * in the drag direction. For instance, if the view is dragged UP, it compares the
         * <code>view.getTop()</code> of the two views before and after drag started. If that value
         * is different, the target view passes the filter.
         * <p>
         * Among these Views which pass the test, the one closest to the dragged view is chosen.
         * <p>
         * This method is called on the main thread every time user moves the View. If you want to
         * override it, make sure it does not do any expensive operations.
         *
         * @param selected    The ViewHolder being dragged by the user.
         * @param dropTargets The list of ViewHolder that are under the dragged View and
         *                    candidate as a drop.
         * @param curX        The updated left value of the dragged View after drag translations
         *                    are applied. This value does not include margins added by
         *                    {@link RecyclerView.ItemDecoration}s.
         * @param curY        The updated top value of the dragged View after drag translations
         *                    are applied. This value does not include margins added by
         *                    {@link RecyclerView.ItemDecoration}s.
         * @return A ViewHolder to whose position the dragged ViewHolder should be
         * moved to.
         */
        public RecyclerView.ViewHolder chooseDropTarget(RecyclerView.ViewHolder selected,
                                                        List<RecyclerView.ViewHolder> dropTargets, int curX, int curY) {
            int right = curX + selected.itemView.getWidth();
            int bottom = curY + selected.itemView.getHeight();
            RecyclerView.ViewHolder winner = null;
            int winnerScore = -1;
            final int dx = curX - selected.itemView.getLeft();
            final int dy = curY - selected.itemView.getTop();
            final int targetsSize = dropTargets.size();
            for (int i = 0; i < targetsSize; i++) {
                final RecyclerView.ViewHolder target = dropTargets.get(i);
                if (dx > 0) {
                    int diff = target.itemView.getRight() - right;
                    if (diff < 0 && target.itemView.getRight() > selected.itemView.getRight()) {
                        final int score = Math.abs(diff);
                        if (score > winnerScore) {
                            winnerScore = score;
                            winner = target;
                        }
                    }
                }
                if (dx < 0) {
                    int diff = target.itemView.getLeft() - curX;
                    if (diff > 0 && target.itemView.getLeft() < selected.itemView.getLeft()) {
                        final int score = Math.abs(diff);
                        if (score > winnerScore) {
                            winnerScore = score;
                            winner = target;
                        }
                    }
                }
                if (dy < 0) {
                    int diff = target.itemView.getTop() - curY;
                    if (diff > 0 && target.itemView.getTop() < selected.itemView.getTop()) {
                        final int score = Math.abs(diff);
                        if (score > winnerScore) {
                            winnerScore = score;
                            winner = target;
                        }
                    }
                }

                if (dy > 0) {
                    int diff = target.itemView.getBottom() - bottom;
                    if (diff < 0 && target.itemView.getBottom() > selected.itemView.getBottom()) {
                        final int score = Math.abs(diff);
                        if (score > winnerScore) {
                            winnerScore = score;
                            winner = target;
                        }
                    }
                }
            }
            return winner;
        }

        private int getMaxDragScroll(RecyclerView recyclerView) {
            if (mCachedMaxScrollSpeed == -1) {
                mCachedMaxScrollSpeed = recyclerView.getResources().getDimensionPixelSize(
                        android.support.v7.recyclerview.R.dimen.item_touch_helper_max_drag_scroll_per_frame);
            }
            return mCachedMaxScrollSpeed;
        }

        /**
         * Called when {@link #onMove(RecyclerView, RecyclerView.ViewHolder, RecyclerView.ViewHolder)} returns true.
         * <p>
         * CustomItemTouchHelper does not create an extra Bitmap or View while dragging, instead, it
         * modifies the existing View. Because of this reason, it is important that the View is
         * still part of the layout after it is moved. This may not work as intended when swapped
         * Views are close to RecyclerView bounds or there are gaps between them (e.g. other Views
         * which were not eligible for dropping over).
         * <p>
         * This method is responsible to give necessary hint to the LayoutManager so that it will
         * keep the View in visible area. For example, for LinearLayoutManager, this is as simple
         * as calling {@link LinearLayoutManager#scrollToPositionWithOffset(int, int)}.
         *
         * Default implementation calls {@link RecyclerView#scrollToPosition(int)} if the View's
         * new position is likely to be out of bounds.
         * <p>
         * It is important to ensure the ViewHolder will stay visible as otherwise, it might be
         * removed by the LayoutManager if the move causes the View to go out of bounds. In that
         * case, drag will end prematurely.
         *
         * @param recyclerView The RecyclerView controlled by the CustomItemTouchHelper.
         * @param viewHolder   The ViewHolder under user's control.
         * @param fromPos      The previous adapter position of the dragged item (before it was
         *                     moved).
         * @param target       The ViewHolder on which the currently active item has been dropped.
         * @param toPos        The new adapter position of the dragged item.
         * @param x            The updated left value of the dragged View after drag translations
         *                     are applied. This value does not include margins added by
         *                     {@link RecyclerView.ItemDecoration}s.
         * @param y            The updated top value of the dragged View after drag translations
         *                     are applied. This value does not include margins added by
         *                     {@link RecyclerView.ItemDecoration}s.
         */
        public void onMoved(final RecyclerView recyclerView,
                            final RecyclerView.ViewHolder viewHolder, int fromPos, final RecyclerView.ViewHolder target, int toPos, int x,
                            int y) {
            final RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            if (layoutManager instanceof ItemTouchHelper.ViewDropHandler) {
                ((ItemTouchHelper.ViewDropHandler) layoutManager).prepareForDrop(viewHolder.itemView,
                        target.itemView, x, y);
                return;
            }

            // if layout manager cannot handle it, do some guesswork
            if (layoutManager.canScrollHorizontally()) {
                final int minLeft = layoutManager.getDecoratedLeft(target.itemView);
                if (minLeft <= recyclerView.getPaddingLeft()) {
                    recyclerView.scrollToPosition(toPos);
                }
                final int maxRight = layoutManager.getDecoratedRight(target.itemView);
                if (maxRight >= recyclerView.getWidth() - recyclerView.getPaddingRight()) {
                    recyclerView.scrollToPosition(toPos);
                }
            }

            if (layoutManager.canScrollVertically()) {
                final int minTop = layoutManager.getDecoratedTop(target.itemView);
                if (minTop <= recyclerView.getPaddingTop()) {
                    recyclerView.scrollToPosition(toPos);
                }
                final int maxBottom = layoutManager.getDecoratedBottom(target.itemView);
                if (maxBottom >= recyclerView.getHeight() - recyclerView.getPaddingBottom()) {
                    recyclerView.scrollToPosition(toPos);
                }
            }
        }

        /**
         * Called by the CustomItemTouchHelper when user is dragging a view out of bounds.
         * <p>
         * You can override this method to decide how much RecyclerView should scroll in response
         * to this action. Default implementation calculates a value based on the amount of View
         * out of bounds and the time it spent there. The longer user keeps the View out of bounds,
         * the faster the list will scroll. Similarly, the larger portion of the View is out of
         * bounds, the faster the RecyclerView will scroll.
         *
         * @param recyclerView        The RecyclerView instance to which CustomItemTouchHelper is
         *                            attached to.
         * @param viewSize            The total size of the View in scroll direction, excluding
         *                            item decorations.
         * @param viewSizeOutOfBounds The total size of the View that is out of bounds. This value
         *                            is negative if the View is dragged towards left or top edge.
         * @param totalSize           The total size of RecyclerView in the scroll direction.
         * @param msSinceStartScroll  The time passed since View is kept out of bounds.
         * @return The amount that RecyclerView should scroll. Keep in mind that this value will
         * be passed to {@link RecyclerView#scrollBy(int, int)} method.
         */
        public int interpolateOutOfBoundsScroll(RecyclerView recyclerView,
                                                int viewSize, int viewSizeOutOfBounds,
                                                int totalSize, long msSinceStartScroll) {
            final int maxScroll = getMaxDragScroll(recyclerView);
            final int absOutOfBounds = Math.abs(viewSizeOutOfBounds);
            final int direction = (int) Math.signum(viewSizeOutOfBounds);
            // might be negative if other direction
            float outOfBoundsRatio = Math.min(1f, 1f * absOutOfBounds / viewSize);
            final int cappedScroll = (int) (direction * maxScroll
                    * sDragViewScrollCapInterpolator.getInterpolation(outOfBoundsRatio));
            final float timeRatio;
            if (msSinceStartScroll > DRAG_SCROLL_ACCELERATION_LIMIT_TIME_MS) {
                timeRatio = 1f;
            } else {
                timeRatio = (float) msSinceStartScroll / DRAG_SCROLL_ACCELERATION_LIMIT_TIME_MS;
            }
            final int value = (int) (cappedScroll * sDragScrollInterpolator
                    .getInterpolation(timeRatio));
            if (value == 0) {
                return viewSizeOutOfBounds > 0 ? 1 : -1;
            }
            return value;
        }
    }
}