package org.bobstuff.boardview.boardview;

import android.support.v7.widget.RecyclerView;
import android.view.View;

/**
 * Created by bob on 17/01/18.
 */

public abstract class ListAdapter <T extends ListAdapter.CardViewHolder>
        extends RecyclerView.Adapter<T> {
    public abstract static class CardViewHolder extends RecyclerView.ViewHolder {
        public CardViewHolder(View view) {
            super(view);
        }
    }

}
