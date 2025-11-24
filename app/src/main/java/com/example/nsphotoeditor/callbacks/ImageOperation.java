package com.example.nsphotoeditor.callbacks;

import android.graphics.Bitmap;

public interface ImageOperation {
    Bitmap apply(Bitmap input, float value);
}
