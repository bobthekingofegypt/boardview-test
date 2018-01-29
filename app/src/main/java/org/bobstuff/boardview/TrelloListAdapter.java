package org.bobstuff.boardview;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.bobstuff.boardview.boardview.BoardView;
import org.bobstuff.boardview.boardview.ListAdapter;
import org.bobstuff.boardview.boardview.LongTouchHandler;
import org.bobstuff.boardview.boardview.model.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by bob on 17/01/18.
 */

public class TrelloListAdapter extends ListAdapter<TrelloListAdapter.TrelloCardViewHolder> {
    public static class TrelloCardViewHolder extends ListAdapter.CardViewHolder {
        public final TextView mTitle;

        public TrelloCardViewHolder(View view) {
            super(view);

            mTitle = view.findViewById(R.id.title);
        }
    }

    private List<Card> mCards;
    private BoardView mBoardView;
    private TrelloBoardAdapter.CardEventListener mCardEventListener;
    private boolean mAddingView;
    private Context mContext;

    public TrelloListAdapter(Context context, TrelloBoardAdapter.CardEventListener cardEventListener) {
        mContext = context;
        mCards = new ArrayList<>();
        this.mCardEventListener = cardEventListener;
    }

    public void setCards(List<Card> cards) {
        this.mCards.clear();
        this.mCards.addAll(cards);
        this.notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mCards.size();
    }

    @Override
    public void onBindViewHolder(final TrelloListAdapter.TrelloCardViewHolder viewHolder, int position) {
        Card card = mCards.get(position);
        viewHolder.mTitle.setText(card.getTitle());

        viewHolder.itemView.setOnTouchListener(new LongTouchHandler(mContext, new LongTouchHandler.OnLongTouchListener() {
            @Override
            public boolean onLongTouch(MotionEvent e) {
                mCardEventListener.onCardSelectedForDrag(viewHolder, e.getX(), e.getY());
                return false;
            }
        }));
        /*
        if (position == insertPosition) {
            viewHolder.itemView.setVisibility(View.INVISIBLE);
            insertPosition = -1;
        }
        */
    }

    public Card delete(int position) {
        Card card = mCards.remove(position);
        notifyItemRemoved(position);
        return card;
    }

    public void add(int position, Card card) {
        mCards.add(position, card);
        mAddingView = true;
        notifyItemInserted(position);
    }

    @Override
    public TrelloListAdapter.TrelloCardViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).
                inflate(R.layout.item_card, viewGroup, false);
        return new TrelloListAdapter.TrelloCardViewHolder(view);
    }

    public boolean onItemMove(int fromPosition, int toPosition) {
        Collections.swap(mCards, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        return true;
    }

    @Override
    public void onViewAttachedToWindow(TrelloCardViewHolder viewHolder) {
        if (mAddingView) {
            mAddingView = false;
            mCardEventListener.onCardMovedDuringDrag(viewHolder);
        }
    }
}
