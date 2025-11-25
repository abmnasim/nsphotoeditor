package com.example.nsphotoeditor.features;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

public class FrameOperation {
    public Bitmap apply(Bitmap src, int frameIndex, Bitmap[] frames) {
        if (frameIndex <= 0 || frames == null) return src;

        Bitmap frame = frames[frameIndex];
        if (frame == null) return src;

        // create output bitmap
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(out);

        // Draw image first
        c.drawBitmap(src, 0, 0, null);

        // Draw frame full size
        Bitmap scaledFrame = Bitmap.createScaledBitmap(frame, src.getWidth(), src.getHeight(), true);

        c.drawBitmap(scaledFrame, 0, 0, new Paint(Paint.ANTI_ALIAS_FLAG));
        return out;
    }
}
