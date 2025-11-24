package com.example.nsphotoeditor.features;

import android.graphics.Bitmap;

import com.example.nsphotoeditor.callbacks.ImageOperation;

public class PixelateOperation implements ImageOperation {
    @Override
    public Bitmap apply(Bitmap src, float value) {
        int blockSize = Math.max(0, Math.round(value));
        if (blockSize <= 1) return src;
        int w = src.getWidth();
        int h = src.getHeight();

        int smallW = Math.max(1, w/blockSize);
        int smallH = Math.max(1, h/blockSize);

        Bitmap small = Bitmap.createScaledBitmap(src, smallW, smallH, false);
        Bitmap pixel = Bitmap.createScaledBitmap(small, w, h, false);
        src.recycle();
        small.recycle();
        return pixel;
    }
}
