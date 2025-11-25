package com.example.nsphotoeditor.features;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import com.example.nsphotoeditor.callbacks.ImageOperation;

public class VignetteOperation implements ImageOperation {
    @Override
    public Bitmap apply(Bitmap src, float value) {
        if (value <= 0f) return src;
        float intensity = value * 100;
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap bmp = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(bmp);
        float radius = Math.max(w, h) * 0.7f;
        int[] colors = {0x00000000, 0x00000000, 0xFF000000};
        float[] stops = {0f, 1f - value, 1f};

        RadialGradient gradient = new RadialGradient(w / 2f, h / 2f, radius, colors, stops, Shader.TileMode.CLAMP);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setShader(gradient);
//        paint.setAlpha((int)(255 * value * 0.9f));
        c.drawRect(0, 0, w, h, paint);

        return bmp;
    }
}
