package com.example.nsphotoeditor.utils;

public class FeatureState {
    public int seekValue; // 0..SEEKBAR_MAX
    public float value;
    public boolean applied;

    public FeatureState(int seekValue, float value, boolean applied) {
        this.seekValue = seekValue;
        this.value = value;
        this.applied = applied;
    }

    public FeatureState(FeatureState other) {
        this.seekValue = other.seekValue;
        this.value = other.value;
        this.applied = other.applied;
    }
}
