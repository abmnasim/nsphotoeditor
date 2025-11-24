package com.example.nsphotoeditor.features;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import com.example.nsphotoeditor.callbacks.ImageOperation;

public class RotationOperation implements ImageOperation {
    @Override
    public Bitmap apply(Bitmap src, float value) {
        if (value % 360f == 0f) return src;

        Matrix m = new Matrix();
        m.postRotate(value);
        Bitmap out = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        src.recycle();
        return out;
    }
}
