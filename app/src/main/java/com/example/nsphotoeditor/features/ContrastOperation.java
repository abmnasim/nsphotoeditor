package com.example.nsphotoeditor.features;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import com.example.nsphotoeditor.callbacks.ImageOperation;

public class ContrastOperation implements ImageOperation {
    @Override
    public Bitmap apply(Bitmap src, float contrast) {
        if (contrast == 1f) return src;

        Bitmap bmp = src.copy(src.getConfig() != null ? src.getConfig() : Bitmap.Config.ARGB_8888, true);
        float offset =  128f * (1f - contrast);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix(new float[] {
                contrast, 0, 0, 0, offset,
                0, contrast, 0, 0, offset,
                0, 0, contrast, 0, offset,
                0, 0, 0, 1, 0
        });
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        Canvas c = new Canvas(bmp);
        c.drawBitmap(bmp, 0, 0, paint);
        src.recycle();
        return bmp;
    }

}
