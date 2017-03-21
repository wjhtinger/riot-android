package com.windsing.facedetection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.faceplusplus.api.FaceDetecter;

/**
 * Created by wangjha on 2017/2/28.
 */

public class FaceMask extends View {

    private Paint localPaint;
    private FaceDetecter.Face[] faceInfos;
    private RectF rect;

    public FaceMask(Context context, AttributeSet atti) {
        super(context, atti);
        rect = new RectF();
        localPaint = new Paint();
        localPaint.setColor(0xff248FAF);
        localPaint.setStrokeWidth(1);
        localPaint.setStyle(Paint.Style.STROKE);
    }

    public void setFaceInfo(FaceDetecter.Face[] faceInfos) {
        this.faceInfos = faceInfos;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (faceInfos == null)
            return;
        for (FaceDetecter.Face localFaceInfo : faceInfos) {
            rect.set(getWidth() * localFaceInfo.left, getHeight() * localFaceInfo.top,
                    getWidth() * localFaceInfo.right, getHeight() * localFaceInfo.bottom);
            canvas.drawRect(rect, localPaint);
        }
    }
}
