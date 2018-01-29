package org.bobstuff.boardview.boardview;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * LongTouchHandler, based on the open source code available in the simple gesture detector.
 * This class is required because we need to know the x,y coordinates of the long press and
 * that isn't available using built in long press detector.
 *
 * Created by bob on 29/01/17.
 */

public class LongTouchHandler implements View.OnTouchListener {
    public interface OnLongTouchListener {
        /**
         * Notified when a long touch occurs.
         *
         * @param e The motion event that occurred during the long touch.
         * @return true if the event is consumed, else false
         */
        boolean onLongTouch(MotionEvent e);
    }

    private static final int LONG_PRESS = 1;
    private final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private final Handler mHandler;
    private boolean mInLongPress;
    private float mDownFocusX;
    private float mDownFocusY;
    private int mTouchSlopSquare;
    private MotionEvent mCurrentDownEvent;
    private OnLongTouchListener mLongTouchListener;

    public LongTouchHandler(Context context, OnLongTouchListener longTouchListener) {
        this.mHandler = new GestureHandler();
        this.mLongTouchListener = longTouchListener;

        final ViewConfiguration configuration = ViewConfiguration.get(context);
        int touchSlop = configuration.getScaledTouchSlop();
        mTouchSlopSquare = touchSlop * touchSlop;
    }

    @Override
    public boolean onTouch(View v, MotionEvent ev) {
        float sumX = 0, sumY = 0;
        final int count = ev.getPointerCount();
        for (int i = 0; i < count; i++) {
            sumX += ev.getX(i);
            sumY += ev.getY(i);
        }
        final int div = count;
        final float focusX = sumX / div;
        final float focusY = sumY / div;

        final int action = ev.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mInLongPress = false;
                mDownFocusX = focusX;
                mDownFocusY = focusY;

                if (mCurrentDownEvent != null) {
                    mCurrentDownEvent.recycle();
                }
                mCurrentDownEvent = MotionEvent.obtain(ev);

                mHandler.removeMessages(LONG_PRESS);
                mHandler.sendEmptyMessageAtTime(LONG_PRESS,
                        mCurrentDownEvent.getDownTime() + LONGPRESS_TIMEOUT);
                break;
            case MotionEvent.ACTION_MOVE:
                final int deltaX = (int) (focusX - mDownFocusX);
                final int deltaY = (int) (focusY - mDownFocusY);
                int distance = (deltaX * deltaX) + (deltaY * deltaY);
                int slopSquare = mTouchSlopSquare;
                if (distance > slopSquare) {
                    mHandler.removeMessages(LONG_PRESS);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mInLongPress) {
                    mInLongPress = false;
                }
                mHandler.removeMessages(LONG_PRESS);
                break;
            case MotionEvent.ACTION_CANCEL:
                mInLongPress = false;
                mHandler.removeMessages(LONG_PRESS);
                break;
        }

        return true;
    }

    private void dispatchLongPress() {
        mInLongPress = true;
        mLongTouchListener.onLongTouch(mCurrentDownEvent);
    }

    class GestureHandler extends Handler {
        GestureHandler() {
            super();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case LONG_PRESS:
                    dispatchLongPress();
                    break;
                default:
                    throw new RuntimeException("Unknown message " + msg); //never
            }
        }
    }
}
