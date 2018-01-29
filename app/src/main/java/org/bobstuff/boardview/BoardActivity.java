package org.bobstuff.boardview;

import android.animation.Animator;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import org.bobstuff.boardview.boardview.BoardView;
import org.bobstuff.boardview.boardview.BobBoardAdapter;
import org.bobstuff.boardview.boardview.BobBoardViewListener;
import org.bobstuff.boardview.boardview.model.Board;
import org.bobstuff.boardview.boardview.model.BoardList;
import org.bobstuff.boardview.boardview.model.Card;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class BoardActivity extends AppCompatActivity implements BobBoardViewListener {
    private TrelloBoardAdapter mTrelloBoardAdapter;
    private Toolbar toolbar;
    private TextView te3;
    private BoardView mBoardView;
    private Board board;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_board);

        board = createTestData();

        mTrelloBoardAdapter = new TrelloBoardAdapter(getBaseContext(), getResources().getDimensionPixelSize(R.dimen.trello_item_list_width));
        mTrelloBoardAdapter.setLists(board.getLists());

        mBoardView = findViewById(R.id.board_view);
        mBoardView.setBoardAdapter(mTrelloBoardAdapter);
        mBoardView.setBoardViewListener(this);
        CustomDividers.ListItemDecoration dividerItemDecoration = new CustomDividers.ListItemDecoration(40);
        mBoardView.getListRecyclerView().addItemDecoration(dividerItemDecoration);

        toolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#00407f")));
        toolbar.setTitleTextColor(Color.parseColor("#ffffff"));
        setStatusBarColor();

        te3 = (TextView) findViewById(R.id.stupid_text);
        te3.setVisibility(View.INVISIBLE);
        te3.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent event) {
                final int action = event.getAction();

                // Handles each of the expected events
                switch(action) {
                    case DragEvent.ACTION_DRAG_ENTERED:
                        Log.d("TEST", "ENTERED TOP BAR");
                        toolbar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#00ff00")));
                        break;
                    case DragEvent.ACTION_DRAG_EXITED:
                        Log.d("TEST", "EXITED TOP BAR");
                        toolbar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#00407f")));
                        break;
                }

                return true;
            }
        });
    }

    public void onDragStarted(BoardView.DragType dragType) {
        if (dragType == BoardView.DragType.LIST) {
            ActionBar mActionBar = getSupportActionBar();
            mActionBar.setDisplayShowHomeEnabled(false);
            mActionBar.setDisplayShowTitleEnabled(false);
            // previously invisible view
            // get the center for the clipping circle
            int cx = te3.getWidth() / 2;
            int cy = te3.getHeight() / 2;
            // get the final radius for the clipping circle
            float finalRadius = (float) Math.hypot(cx, cy);
            // create the animator for this view (the start radius is zero)
            Animator anim =
                    ViewAnimationUtils.createCircularReveal(te3, cx, cy, 0, finalRadius);
            // make the view visible and start the animation
            te3.setVisibility(View.VISIBLE);
            anim.start();

            mTrelloBoardAdapter.triggerScaleDownAnimationForListDrag();
        }
    }

    public void onDragEnded(BoardView.DragType dragType) {
        if (dragType == BoardView.DragType.LIST) {
            int cx = te3.getWidth() / 2;
            int cy = te3.getHeight() / 2;
            // get the final radius for the clipping circle
            float finalRadius = (float) Math.hypot(cx, cy);
            Animator anim =
                    ViewAnimationUtils.createCircularReveal(te3, cx, cy, 0, finalRadius);
            anim.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    te3.setVisibility(View.INVISIBLE);
                    ActionBar mActionBar = getSupportActionBar();
                    mActionBar.setDisplayShowHomeEnabled(true);
                    mActionBar.setDisplayShowTitleEnabled(true);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });
            // make the view visible and start the animation
            anim.start();

            mTrelloBoardAdapter.triggerScaleUpAnimationForListDrag();
        }
    }

    public void onListMove(int fromPosition, int toPosition) {
        Log.d("TEST", "List moved");
        mTrelloBoardAdapter.onItemMove(fromPosition, toPosition);
        Collections.swap(board.getLists(), fromPosition, toPosition);
    }

    public void onCardMove(BobBoardAdapter.ListViewHolder listViewHolder, int listIndex, int fromPosition, int toPosition) {
        Log.d("TEST", "card moved");
        ((TrelloBoardAdapter.TrelloListViewHolder)listViewHolder).mTrelloListAdapter.onItemMove(fromPosition, toPosition);

        Collections.swap(board.getLists().get(listIndex).getCards(), fromPosition, toPosition);
    }

    public void onCardMoveList(BobBoardAdapter.ListViewHolder fromViewHolder,
                               BobBoardAdapter.ListViewHolder toViewHolder,
                               int fromIndex, int toIndex) {
        Log.d("TEST", "Card moved - fromIndex: " + fromIndex + "; toIndex:" + toIndex + "; fromViewHolder: " + fromViewHolder + "; toViewHolder: " + toViewHolder);
        Card card = ((TrelloBoardAdapter.TrelloListViewHolder)fromViewHolder).dismissDraggedCard(fromIndex);
        ((TrelloBoardAdapter.TrelloListViewHolder)toViewHolder).add(toIndex, card);

        Card card2 = board.getLists().get(fromViewHolder.getAdapterPosition()).getCards().remove(fromIndex);
        board.getLists().get(toViewHolder.getAdapterPosition()).getCards().add(toIndex, card2);
    }

    public View.DragShadowBuilder dragShadowBuilder(RecyclerView.ViewHolder viewHolder, float x, float y) {
        if (mBoardView.getCurrentDragType() == BoardView.DragType.LIST) {
            return new ListShadowBuilder(viewHolder.itemView, x/0.7f, y);
        }
        return new CardShadowBuilder(viewHolder.itemView, x, y);
    }

    public Board createTestData() {
        Card cardOne = new Card("Infinite Scrolling on main collection view, Infinite Scrolling on main collection view, Infinite Scrolling on main collection view");
        Card cardTwo = new Card("Check synchronous image load of separate thread isn't bad");
        Card cardThree = new Card("Independent controller logic");
        Card cardFour = new Card("Allow sorting by dragging and dropping of cards");
        Card cardFive = new Card("Sent delta change for hand and counts to opponents");
        Card cardSix = new Card("double tap to play should be disabled when it is not your turn to play");
        Card cardSeven = new Card("Add room support");

        BoardList listOne = new BoardList("todo", new ArrayList<>(Arrays.asList(cardOne, cardTwo)));
        BoardList listTwo = new BoardList("in progress", new ArrayList<>(Arrays.asList(cardThree, cardFour, cardFive)));
        BoardList listThree = new BoardList("blocked");
        BoardList listFour = new BoardList("testing");
        BoardList listFive = new BoardList("test failed", new ArrayList<>(Arrays.asList(cardSix)));
        BoardList listSix = new BoardList("complete", new ArrayList<>(Arrays.asList(cardSeven)));

        return new Board(new ArrayList<>(Arrays.asList(listOne, listTwo, listThree, listFour, listFive, listSix)));
    }

    public void setStatusBarColor(){
        Window window = this.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(getDarkerShadeColor(Color.parseColor("#00407f")));
        }
    }

    /**
     * https://blog.fossasia.org/creating-different-shades-of-a-color-in-phimpme-android/
     * @param c
     * @return
     */
    public int getDarkerShadeColor(int c){
        float[] hsv = new float[3];
        int color = c;
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.80f;
        color = Color.HSVToColor(hsv);
        return color;
    }

    public void onStartDrag() {

    }
}
