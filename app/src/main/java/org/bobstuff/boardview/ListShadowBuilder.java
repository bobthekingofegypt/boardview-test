package org.bobstuff.boardview;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;

/**
 * Created by bob on 20/12/17.
 */

public class ListShadowBuilder extends View.DragShadowBuilder {
    private final float x;
    private final float y;
    private View view;

    public ListShadowBuilder(View v, float x, float y) {
        super(v);
        this.view = v;
        this.x = x;
        this.y = y;
    }

    @Override
    public void onProvideShadowMetrics (Point size, Point touch) {
        float positionPercentage = this.x / view.getWidth();

        size.set((int)(view.getWidth()*0.7) + 30, view.getHeight());
        touch.set((int)((view.getWidth()*0.7)*positionPercentage), (int)y);
    }

    @Override
    public void onDrawShadow(Canvas canvas) {
        final View view = getView();
        if (view != null) {
            canvas.save(Canvas.ALL_SAVE_FLAG);
            canvas.scale(0.7f, 0.7f);
            canvas.translate(30, 0);
            canvas.rotate(3, 0,0);
            super.onDrawShadow(canvas);
            canvas.restore();
        }
    }
}
