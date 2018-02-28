package com.example.sunyao.audiovisualizer.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * @author sunyao
 * @Description:
 * @date 2018/1/18 下午2:44
 */
public class AudioWaveView extends View {

    private double[] mAudioData;
    private Paint mPaint;
    private int mViewHeight;
    private int mViewWidth;

    public AudioWaveView(Context context) {
        this(context, null);
        initPaint();
    }

    public AudioWaveView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        initPaint();
    }

    public AudioWaveView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initPaint();
    }

    public void setAudioData(double[] mAudioData) {
        this.mAudioData = mAudioData;
    }

    private void initPaint() {
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.parseColor("#00897b"));
        mPaint.setStrokeWidth(1.0f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mViewHeight = h;
        mViewWidth = w;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawWave(canvas);
    }

    private void drawWave(Canvas canvas) {
        if (null != mAudioData) {
            float stepSize = (float) ((double) mViewWidth / mAudioData.length);
            stepSize = 0.006f;
            Log.d("Wave View: ", "audioDataLen : " + mAudioData.length + "   " + stepSize);
            for (int i = 20; i < mAudioData.length; i += 20) {
                if (i % 20 == 0) {
                    canvas.drawLine((i - 20) * stepSize,
                            (mViewHeight / 2 - (float) (mAudioData[i - 20] * mViewHeight / 2)),
                            i * stepSize,
                            (mViewHeight / 2 - (float) (mAudioData[i] * mViewHeight / 2)),
                            mPaint);
                }
            }
        }
    }
}
