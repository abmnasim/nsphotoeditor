package com.example.nsphotoeditor.utils;

import com.example.nsphotoeditor.enums.FeatureType;

public class FeatureItem {
    public final String id;
    public final String name;
    public final int iconRes;
    public final FeatureType type;

    public FeatureItem(String id, String name, int iconRes, FeatureType type) {
        this.id = id;
        this.name = name;
        this.iconRes = iconRes;
        this.type = type;
    }
}
