package com.example.nsphotoeditor.utils;

public class FeatureConfig {
    public float min;
    public float max;
    public float defaultValue;
    boolean centeredDefault;

    public FeatureConfig(float min, float max, float defaultValue, boolean centeredDefault) {
        this.min = min;
        this.max = max;
        this.defaultValue = defaultValue;
        this.centeredDefault = centeredDefault;
    }
}
