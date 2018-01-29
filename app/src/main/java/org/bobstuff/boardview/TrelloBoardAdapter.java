package org.bobstuff.boardview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.bobstuff.boardview.boardview.BoardView;
import org.bobstuff.boardview.boardview.BobBoardAdapter;
import org.bobstuff.boardview.boardview.ListAdapter;
import org.bobstuff.boardview.boardview.LongTouchHandler;
import org.bobstuff.boardview.boardview.model.BoardList;
import org.bobstuff.boardview.boardview.model.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Created by bob on 17/01/18.
 */

public class TrelloBoardAdapter extends BobBoardAdapter<TrelloBoardAdapter.TrelloListViewHolder> {
    public  interface CardEventListener {
        void onCardSelectedForDrag(ListAdapter.CardViewHolder viewHolder, float x, float y);
        void onCardMovedDuringDrag(ListAdapter.CardViewHolder viewHolder);
    }

    public class TrelloListViewHolder extends BobBoardAdapter.ListViewHolder {
        public TrelloListAdapter mTrelloListAdapter;
        public final TextView mTitle;
        public final CardView mCardView;
        public final RecyclerView mListRecyclerView;

        public TrelloListViewHolder(final View view) {
            super(view);

            mTitle = view.findViewById(R.id.title);

            mCardView = view.findViewById(R.id.cardview);
            mCardView.setPivotX(0);
            mCardView.setPivotY(0);

            mTrelloListAdapter = new TrelloListAdapter(mContext, new CardEventListener() {
                @Override
                public void onCardSelectedForDrag(ListAdapter.CardViewHolder viewHolder, float x, float y) {
                    mBoardView.startCardDrag(TrelloListViewHolder.this, viewHolder, x, y);
                }

                @Override
                public void onCardMovedDuringDrag(ListAdapter.CardViewHolder viewHolder) {
                    mBoardView.switchCardDrag(mListRecyclerView, viewHolder);
                }
            });

            mListRecyclerView = view.findViewById(R.id.card_recycler);
            mListRecyclerView.setLayoutManager(
                    new LinearLayoutManager(mListRecyclerView.getContext(), LinearLayoutManager.VERTICAL, false));
            mListRecyclerView.setAdapter(mTrelloListAdapter);
            mListRecyclerView.addItemDecoration(new CustomDividers.CardItemDecoration(4));
        }

        @Override
        public RecyclerView getRecyclerView() {
            return mListRecyclerView;
        }

        public Card dismissDraggedCard(int index) {
            return mTrelloListAdapter.delete(index);
        }

        public void add(int index, Card card) {
            mTrelloListAdapter.add(index, card);
        }
    }

    private static final float SCALE_FACTOR = 0.7f;
    private List<BoardList> mLists;
    private BoardView mBoardView;
    private RecyclerView mBoardViewRecyclerView;
    private Set<TrelloListViewHolder> mAnimatingViewHolders;
    private boolean mListDraggingActivated;
    private boolean mCurrentlyScaled;
    private boolean mCurrentlyAnimating;
    private int width;
    private int scaledWidth;
    private Context mContext;


    public TrelloBoardAdapter(Context context, int width) {
        mContext = context;
        mLists = new ArrayList<>();
        mAnimatingViewHolders = new CopyOnWriteArraySet<>();
        this.width = width;
        this.scaledWidth = (int)(width * SCALE_FACTOR);
    }

    public void setLists(List<BoardList> lists) {
        this.mLists.clear();
        this.mLists.addAll(lists);
        this.notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mLists.size();
    }

    @Override
    public void onBindViewHolder(final TrelloListViewHolder viewHolder, int position) {
        BoardList boardList = mLists.get(position);
        String title = boardList.getTitle();
        viewHolder.mTitle.setText(title);

        viewHolder.itemView.setOnTouchListener(new LongTouchHandler(mContext, new LongTouchHandler.OnLongTouchListener() {
            @Override
            public boolean onLongTouch(MotionEvent e) {
                Log.d("TEST", "e.getX(): " + e.getX());
                mBoardView.startListDrag(viewHolder, e.getX() * SCALE_FACTOR, e.getY());
                return false;
            }
        }));
        viewHolder.mTrelloListAdapter.setCards(boardList.getCards());

        final ViewGroup.LayoutParams lp = viewHolder.itemView.getLayoutParams();
        final View child = viewHolder.mCardView;
        float ratio = 1.0f;
        int currentWidth = width;

        if (mListDraggingActivated) {
            currentWidth = scaledWidth;
            ratio = scaledWidth / (float) width;
        }

        lp.width = currentWidth;
        viewHolder.itemView.setLayoutParams(lp);
        child.setScaleY(ratio);
        child.setScaleX(ratio);
    }

    @Override
    public TrelloListViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).
                inflate(R.layout.item_list, viewGroup, false);
        return new TrelloListViewHolder(view);
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        mBoardViewRecyclerView = recyclerView;
    }

    @Override
    public void onAttachedToBoardView(BoardView boardView) {
        super.onAttachedToBoardView(boardView);
        this.mBoardView = boardView;
    }

    @Override
    public void onDetachedFromBoardView(BoardView boardView) {
        super.onDetachedFromBoardView(boardView);
        this.mBoardView = null;
    }

    public void triggerScaleDownAnimationForListDrag() {
        if (mListDraggingActivated) {
            return; //this shouldn't happen but hey ho it might
        }

        mListDraggingActivated = true;
        mCurrentlyAnimating = true;
        for (int childCount = mBoardViewRecyclerView.getChildCount(), i = 0; i < childCount; ++i) {
            TrelloListViewHolder viewHolder = (TrelloListViewHolder)
                    mBoardViewRecyclerView.getChildViewHolder(mBoardViewRecyclerView.getChildAt(i));
            mAnimatingViewHolders.add(viewHolder);
        }

        final ValueAnimator animator = ValueAnimator.ofInt(width, scaledWidth);
        animator.addUpdateListener(new LayoutWidthUpdateListener());
        animator.addListener(new ViewHolderAnimatorListener());
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mCurrentlyScaled = true;
                mCurrentlyAnimating = false;
                mAnimatingViewHolders.clear();
            }
        });
        animator.start();
    }

    public void triggerScaleUpAnimationForListDrag() {
        mCurrentlyAnimating = true;
        for (int childCount = mBoardViewRecyclerView.getChildCount(), i = 0; i < childCount; ++i) {
            TrelloListViewHolder viewHolder = (TrelloListViewHolder)
                    mBoardViewRecyclerView.getChildViewHolder(mBoardViewRecyclerView.getChildAt(i));
            mAnimatingViewHolders.add(viewHolder);
        }

        final ValueAnimator animator = ValueAnimator.ofInt(scaledWidth, width);
        animator.addUpdateListener(new LayoutWidthUpdateListener());
        animator.addListener(new ViewHolderAnimatorListener());
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mCurrentlyAnimating = false;
                mListDraggingActivated = false;
                mCurrentlyScaled = false;
                mAnimatingViewHolders.clear();
            }
        });
        animator.start();
    }

    @Override
    public void onViewAttachedToWindow(TrelloListViewHolder viewHolder) {
        if (mCurrentlyAnimating) {
            viewHolder.setIsRecyclable(false);
            mAnimatingViewHolders.add(viewHolder);
        } else if (mCurrentlyScaled) {
            final View child = viewHolder.mCardView;
            int currentWidth = scaledWidth;
            float ratio = scaledWidth / (float) width;

            final ViewGroup.LayoutParams lp = viewHolder.itemView.getLayoutParams();
            lp.width = currentWidth;
            viewHolder.itemView.setLayoutParams(lp);
            child.setScaleY(ratio);
            child.setScaleX(ratio);
        } else {
            final View child = viewHolder.mCardView;
            float ratio = 1.0f;
            int currentWidth = width;

            final ViewGroup.LayoutParams lp = viewHolder.itemView.getLayoutParams();
            lp.width = currentWidth;
            viewHolder.itemView.setLayoutParams(lp);
            child.setScaleY(1.0f);
            child.setScaleX(1.0f);
        }
    }

    public boolean onItemMove(int fromPosition, int toPosition) {
        Collections.swap(mLists, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        return true;
    }

    private class LayoutWidthUpdateListener implements ValueAnimator.AnimatorUpdateListener {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            int animationValue = (int) animation.getAnimatedValue();
            float scale = (animationValue / (float) width);
            for (TrelloListViewHolder vh : mAnimatingViewHolders) {
                final ViewGroup.LayoutParams lp = vh.itemView.getLayoutParams();
                lp.width = animationValue;
                vh.itemView.setLayoutParams(lp);

                View child = vh.mCardView;
                child.setScaleY(scale);
                child.setScaleX(scale);
            }
        }
    }

    class ViewHolderAnimatorListener extends AnimatorListenerAdapter {
        @Override
        public void onAnimationStart(Animator animation) {
            for (RecyclerView.ViewHolder vh : mAnimatingViewHolders) {
                vh.setIsRecyclable(false);
            }
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            reset();
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            reset();
        }

        private void reset() {
            for (RecyclerView.ViewHolder vh : mAnimatingViewHolders) {
                vh.setIsRecyclable(true);
            }
        }
    }
}
