package com.example.nsphotoeditor.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;

import java.io.IOException;

public class BitmapUtils {
    public static Bitmap loadSampledBitmap(Context ctx, Uri uri, int maxW, int maxH) throws IOException {
        Bitmap full = MediaStore.Images.Media.getBitmap(ctx.getContentResolver(), uri);
        int w = full.getWidth();
        int h = full.getHeight();
        float scale = Math.min((float) maxW / w, (float) maxH / h);
        if (scale >= 1f) return full;
        int nw = Math.max(1, Math.round(w * scale));
        int nh = Math.max(1, Math.round(h * scale));

        Bitmap scaled = Bitmap.createScaledBitmap(full, nw, nh, true);
        full.recycle();
        return scaled;
    }

    private static Bitmap viewToBitmap(View v) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, v.getWidth()), Math.max(1, v.getHeight()), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        v.draw(c);
        return b;
    }

    public static Bitmap mergeBitmapWithOverlays (Bitmap base, View container, View imageView) {
        // container: parent FrameLayout: imageView: the ImageView showing the bitmap
        Bitmap out = base.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(out);

        // We need to map child view positions (in container coordinates) into base bitmap coordinates.
        // We'll assume imageView scaleType fitCenter: compute scale and offsets:
        float ivW = imageView.getWidth();
        float ivH = imageView.getHeight();
        float bW = base.getWidth();
        float bH = base.getHeight();
        float scale = Math.min(ivW / bW, ivH / bH);
        float offsetX = (ivW - bW * scale) / 2f;
        float offsetY = (ivH - bH * scale) / 2f;

        for (int i = 0; i < ((android.view.ViewGroup)container).getChildCount(); i++) {
            View child = ((android.view.ViewGroup)container).getChildAt(i);
            if (child == imageView)
                continue;

            if (child.getVisibility() != View.VISIBLE) continue;

            Bitmap childBmp = viewToBitmap(child);

            // child position relative to imageView
            float cx = (child.getX() - imageView.getX() - offsetX) / scale;
            float cy = (child.getY() - imageView.getY() - offsetY) / scale;
            float cScaleX = child.getScaleX();
            float cScaleY = child.getScaleY();

            Matrix m = new Matrix();
            m.postScale(cScaleX, cScaleY);
            Bitmap scaledChild = Bitmap.createBitmap(childBmp, 0, 0, childBmp.getWidth(), childBmp.getHeight(), m, true);
            c.drawBitmap(scaledChild, cx, cy, null);
            childBmp.recycle();
            scaledChild.recycle();
        }
        return out;
    }
}
