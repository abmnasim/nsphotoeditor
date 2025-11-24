package com.example.nsphotoeditor.features;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import com.example.nsphotoeditor.callbacks.ImageOperation;

public class SaturationOperation implements ImageOperation {
    @Override
    public Bitmap apply(Bitmap src, float value) {
        if (value == 1f) return src;
        Bitmap bmp = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(value);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        c.drawBitmap(src, 0, 0, p);
        src.recycle();
        return bmp;
    }
}
