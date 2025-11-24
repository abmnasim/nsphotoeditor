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
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap bmp = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(bmp);
        float radius = Math.max(w, h) * 0.7f;
        RadialGradient gradient = new RadialGradient(w / 2f, h / 2f, radius, new int[]{0x00000000, 0x00000000, 0xFF000000}, new float[]{0f, Math.max(0.6f, 1f - value), 1f}, Shader.TileMode.CLAMP);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setShader(gradient);
        paint.setAlpha((int)(255 * value * 0.9f));
        c.drawRect(0, 0, w, h, paint);

        return bmp;
    }
}
