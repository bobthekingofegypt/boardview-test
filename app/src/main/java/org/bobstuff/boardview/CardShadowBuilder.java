package org.bobstuff.boardview;

import android.graphics.Canvas;
import android.graphics.Point;
import android.view.View;

/**
 * Created by bob on 20/12/17.
 */

public class CardShadowBuilder extends View.DragShadowBuilder {
    private final float x;
    private final float y;
    private View view;

    public CardShadowBuilder(View v, float x, float y) {
        super(v);
        this.view = v;
        this.x = x;
        this.y = y;
    }

    @Override
    public void onProvideShadowMetrics (Point size, Point touch) {
        size.set(view.getWidth() + 20, view.getHeight() + 30);
        touch.set((int)x, (int)y);
    }

    @Override
    public void onDrawShadow(Canvas canvas) {
        final View view = getView();
        if (view != null) {
            canvas.save(Canvas.ALL_SAVE_FLAG);
            canvas.translate(10, 10);
            canvas.rotate(1, 0,0);
            super.onDrawShadow(canvas);
            canvas.restore();
        }
    }
}
