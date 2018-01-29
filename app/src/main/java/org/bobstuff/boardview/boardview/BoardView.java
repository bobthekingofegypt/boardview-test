package org.bobstuff.boardview.boardview;

import android.content.ClipData;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.LinearSnapHelper;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SnapHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.bobstuff.boardview.boardview.model.Board;

import java.util.Collections;

/**
 * Created by bob on 29/12/17.
 */

public class BoardView extends FrameLayout {
    private RecyclerView mListRecyclerView;
    private DragItemTouchHelper mItemTouchHelper;
    private AutoScroller mAutoScroller;
    private BobBoardViewListener mBoardViewListener;
    private BobBoardAdapter<? extends BobBoardAdapter.ListViewHolder> mBoardAdapater;
    private DragType mCurrentDragType = DragType.NONE;
    private SnapHelper mSnapHelper;
    /**
     * Store the last updated drag point, we use this so when we autoscroll the main list view we
     * can retrigger the drag logic, this happens when you drag a card near the edge of the screen.
     * Drag point is relative to the viewport not the actual backing scroll view, so it can just be
     * re-used in future calculations.
     */
    private PointF mLastDragPoint = new PointF();

    private BobBoardAdapter.ListViewHolder mPreviousViewHolder;
    private int previousIndex = -1;

    public BoardView(@NonNull Context context) {
        super(context);
        init();
    }

    public BoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DragType getCurrentDragType() {
        return mCurrentDragType;
    }

    public RecyclerView getListRecyclerView() {
        return mListRecyclerView;
    }

    public void setBoardViewListener(BobBoardViewListener boardViewListener) {
        this.mBoardViewListener = boardViewListener;
    }

    public void setBoardAdapter(BobBoardAdapter<? extends BobBoardAdapter.ListViewHolder> boardAdapter) {
        if (this.mBoardAdapater != null && this.mBoardAdapater != boardAdapter) {
            mBoardAdapater.onDetachedFromBoardView(this);
        }
        this.mBoardAdapater = boardAdapter;
        mListRecyclerView.setAdapter(boardAdapter);
        mBoardAdapater.onAttachedToBoardView(this);
    }

    private void init() {
        mListRecyclerView = new RecyclerView(getContext());
        mListRecyclerView.setHasFixedSize(true);
        mListRecyclerView.setLayoutParams(
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayoutManager llm = new LinearLayoutManager(getContext(),
                LinearLayoutManager.HORIZONTAL, false);
        mListRecyclerView.setLayoutManager(llm);

        mItemTouchHelper = new DragItemTouchHelper(
                new SimpleItemTouchHelperCallback());

        mAutoScroller = new AutoScroller(mListRecyclerView, 10,
                new AutoScroller.ScrollCallback() {
            @Override
            public void onScrolled(int accumulatedScroll) {
                onUpdateDrag(mLastDragPoint.x, mLastDragPoint.y);
            }
        });

        mListRecyclerView.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View view, DragEvent event) {
                final int action = event.getAction();
                float x = event.getX();
                float y = event.getY();

                switch(action) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return true;
                    case DragEvent.ACTION_DRAG_ENTERED:
                        return true;
                    case DragEvent.ACTION_DRAG_LOCATION:
                        mLastDragPoint.set(x, y);
                        onUpdateDrag(x, y);
                        if (mCurrentDragType == DragType.CARD) {
                            mAutoScroller.updateDrag(x, y);
                        }
                        return false;
                    case DragEvent.ACTION_DRAG_EXITED:
                        return true;
                    case DragEvent.ACTION_DROP:
                        return true;
                    case DragEvent.ACTION_DRAG_ENDED:
                        if (mCurrentDragType == DragType.CARD) {
                            View listItemOnTopOf = mListRecyclerView.findChildViewUnder(mLastDragPoint.x, mLastDragPoint.y);
                            int listIndex = mListRecyclerView.getChildAdapterPosition(listItemOnTopOf);
                            mListRecyclerView.smoothScrollToPosition(listIndex);
                        }
                        stopDrag();
                        return true;
                    default:
                        Log.e("DragDrop Example","Unknown action type received by OnDragListener.");
                        break;
                }

                return false;
            }
        });

        mSnapHelper = new LinearSnapHelper();
        mSnapHelper.attachToRecyclerView(mListRecyclerView);

        this.addView(mListRecyclerView);
    }

    private void onUpdateDrag(float x, float y) {
        if (mCurrentDragType == DragType.LIST) {
            mItemTouchHelper.drag(x, y);
            return;
        }

        View listItemOnTopOf = mListRecyclerView.findChildViewUnder(x, y);
        //view will be null if we are on some item decoration, under a list or similar, do nothing
        //when that is the case
        if (listItemOnTopOf == null) {
            return;
        }

        int listIndex = mListRecyclerView.getChildAdapterPosition(listItemOnTopOf);
        BobBoardAdapter.ListViewHolder holder =
                (BobBoardAdapter.ListViewHolder)mListRecyclerView.getChildViewHolder(listItemOnTopOf);
        RecyclerView cardRecyclerView = holder.getRecyclerView();
        Rect offsetViewBounds = new Rect();
        cardRecyclerView.getDrawingRect(offsetViewBounds);
        ((ViewGroup)holder.itemView).offsetDescendantRectToMyCoords(cardRecyclerView, offsetViewBounds);

        //we need to deal with any chrome around the list, so find out the recycler views offset from
        //the side
        int relativeTop = offsetViewBounds.top;
        int relativeLeft = offsetViewBounds.left;

        if (previousIndex != -1 && previousIndex != listIndex) {
            View onTopOf = cardRecyclerView.findChildViewUnder(relativeLeft, y - relativeTop);
            int toIndex = cardRecyclerView.getChildAdapterPosition(onTopOf);
            if (toIndex == -1) {
                toIndex = 0;
            }

            int fromIndex = mItemTouchHelper.mSelected.getAdapterPosition();

            if (mBoardViewListener != null) {
                mPreviousViewHolder.setIsRecyclable(false);
                holder.setIsRecyclable(false);
                mBoardViewListener.onCardMoveList(mPreviousViewHolder, holder, fromIndex, toIndex);
                mPreviousViewHolder.setIsRecyclable(true);
                holder.setIsRecyclable(true);
            }
            mItemTouchHelper.attachToRecyclerView(null);
        } else {
            mItemTouchHelper.drag(relativeLeft, y - relativeTop);
        }

        previousIndex = listIndex;
        mPreviousViewHolder.setIsRecyclable(true);
        mPreviousViewHolder = holder;
        mPreviousViewHolder.setIsRecyclable(false);
    }

    public void startListDrag(BobBoardAdapter.ListViewHolder viewHolder, float x, float y) {
        mSnapHelper.attachToRecyclerView(null);
        mCurrentDragType = DragType.LIST;
        mItemTouchHelper.attachToRecyclerView(mListRecyclerView);
        mItemTouchHelper.startDrag(viewHolder, x, y);

        mPreviousViewHolder = viewHolder;
        mPreviousViewHolder.setIsRecyclable(false);

        if (mBoardViewListener != null) {
            mBoardViewListener.onDragStarted(DragType.LIST);

        }

        ClipData data = ClipData.newPlainText("", "");
        DragShadowBuilder shadowBuilder = mBoardViewListener.dragShadowBuilder(viewHolder, x, y);
        viewHolder.itemView.startDrag(data, shadowBuilder, mCurrentDragType, 0);
        viewHolder.itemView.setVisibility(View.INVISIBLE);
    }

    public void startCardDrag(BobBoardAdapter.ListViewHolder listViewHolder,
                              ListAdapter.CardViewHolder cardViewHolder,
                              float x, float y) {
        mCurrentDragType = DragType.CARD;
        mSnapHelper.attachToRecyclerView(null);
        mItemTouchHelper.attachToRecyclerView(listViewHolder.getRecyclerView());
        mItemTouchHelper.startDrag(cardViewHolder, x, y);
        mPreviousViewHolder = listViewHolder;
        mPreviousViewHolder.setIsRecyclable(false);
        mAutoScroller.startTracking();

        if (mBoardViewListener != null) {
            mBoardViewListener.onDragStarted(DragType.CARD);
        }

        ClipData data = ClipData.newPlainText("", "");
        DragShadowBuilder shadowBuilder = mBoardViewListener.dragShadowBuilder(cardViewHolder, x, y);
        cardViewHolder.itemView.startDrag(data, shadowBuilder, mCurrentDragType, 0);
        cardViewHolder.itemView.setVisibility(View.INVISIBLE);
    }

    public void switchCardDrag(RecyclerView recyclerView,
                              ListAdapter.CardViewHolder cardViewHolder) {
        mItemTouchHelper.attachToRecyclerView(recyclerView);
        mItemTouchHelper.startDragManual(cardViewHolder);
    }

    private void stopDrag() {
        if (mBoardViewListener != null) {
            mBoardViewListener.onDragEnded(mCurrentDragType);
        }

        mItemTouchHelper.endDrag();
        mItemTouchHelper.attachToRecyclerView(null);
        mPreviousViewHolder.setIsRecyclable(true);
        mPreviousViewHolder = null;
        previousIndex = -1;
        mCurrentDragType = DragType.NONE;
        mAutoScroller.stopTracking();

        mSnapHelper.attachToRecyclerView(mListRecyclerView);
    }

    private boolean onItemMove(int fromPosition, int toPosition) {
        if (mCurrentDragType == DragType.CARD) {
            if (mBoardViewListener != null) {
                mBoardViewListener.onCardMove(mPreviousViewHolder, mListRecyclerView.getChildAdapterPosition(mPreviousViewHolder.itemView), fromPosition, toPosition);
            }
        } else if (mCurrentDragType == DragType.LIST) {
            if (mBoardViewListener != null) {
                mBoardViewListener.onListMove(fromPosition, toPosition);
            }
        }
        return true;
    }

    public enum DragType {
        NONE, LIST, CARD
    }

    public class SimpleItemTouchHelperCallback extends DragItemTouchHelper.Callback {
        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            onItemMove(source.getAdapterPosition(), target.getAdapterPosition());
            return true;
        }
    }
}
