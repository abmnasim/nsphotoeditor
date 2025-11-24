package com.example.nsphotoeditor.features;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import com.example.nsphotoeditor.callbacks.ImageOperation;

public class WarmthOperation implements ImageOperation {
    @Override
    public Bitmap apply(Bitmap src, float value) {
        if (value == 0f) return src;
        // simple liner RGB scale: warm -> increase R, reduce B; cool -> reduce R, increase B
        float rScale = 1f + 0.3f * value;
        float bScale = 1f - 0.3f * value;
        Bitmap bmp = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint();
        ColorMatrix cm = new ColorMatrix(new float[]{
                rScale, 0, 0, 0, 0,
                0, 1, 0, 0, 0,
                0, 0, bScale, 0, 0,
                0, 0, 0, 1, 0
        });
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        c.drawBitmap(src, 0, 0, p);
        src.recycle();
        return bmp;
    }
}
