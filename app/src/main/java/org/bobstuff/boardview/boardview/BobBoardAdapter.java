package org.bobstuff.boardview.boardview;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.bobstuff.boardview.R;
import org.bobstuff.boardview.boardview.model.BoardList;

import java.util.List;

/**
 * Created by bob on 17/01/18.
 */

public abstract class BobBoardAdapter<T extends BobBoardAdapter.ListViewHolder>
        extends RecyclerView.Adapter<T> {
    public abstract static class ListViewHolder extends RecyclerView.ViewHolder {
        protected DragItemTouchHelper mDragItemTouchHelper;

        public ListViewHolder(View view) {
            super(view);
        }

        public void setDragItemTouchHelper(DragItemTouchHelper dragItemTouchHelper) {
            this.mDragItemTouchHelper = dragItemTouchHelper;
        }

        public DragItemTouchHelper getDragItemTouchHelper() {
            return mDragItemTouchHelper;
        }

        public abstract RecyclerView getRecyclerView();
    }

    public void onAttachedToBoardView(BoardView boardView) {
    }

    public void onDetachedFromBoardView(BoardView boardView) {
    }
}
