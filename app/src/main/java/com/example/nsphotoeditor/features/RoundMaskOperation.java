package com.example.nsphotoeditor.features;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;

import com.example.nsphotoeditor.callbacks.ImageOperation;

public class RoundMaskOperation implements ImageOperation {
    @Override
    public Bitmap apply(Bitmap src, float value) {
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        BitmapShader shader = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        p.setShader(shader);
        float radius = Math.min(w, h) / value;
        c.drawCircle(w/value, h/value, radius, p);
        src.recycle();
        return out;
    }
}
