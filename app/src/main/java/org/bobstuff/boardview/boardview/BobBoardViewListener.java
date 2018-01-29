package org.bobstuff.boardview.boardview;

import android.support.v7.widget.RecyclerView;
import android.view.View;

/**
 * Created by bob on 17/01/18.
 */

public interface BobBoardViewListener {
    void onDragStarted(BoardView.DragType dragType);
    void onDragEnded(BoardView.DragType dragType);
    void onListMove(int fromPosition, int toPosition);
    void onCardMove(BobBoardAdapter.ListViewHolder listViewHolder, int listIndex, int fromPosition, int toPosition);
    void onCardMoveList(BobBoardAdapter.ListViewHolder fromViewHolder, BobBoardAdapter.ListViewHolder toViewHolder,
                        int fromIndex, int toIndex);
    View.DragShadowBuilder dragShadowBuilder(RecyclerView.ViewHolder viewHolder, float x, float y);
}
