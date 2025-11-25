package com.example.nsphotoeditor.features;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import com.example.nsphotoeditor.callbacks.ImageOperation;

public class FlipOperation implements ImageOperation {
    @Override
    public Bitmap apply(Bitmap src, float value) {
        boolean horizontal = value == 1f;
        Matrix m = new Matrix();
         m.preScale(horizontal ? -1f : 1f, 1f);
        Bitmap out = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        src.recycle();
        return out;
    }
}
