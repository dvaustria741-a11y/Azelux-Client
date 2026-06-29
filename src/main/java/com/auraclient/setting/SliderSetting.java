package com.auraclient.setting;

public class SliderSetting extends Setting<Double> {
    private final double min, max;

    public SliderSetting(String name, double defaultValue, double min, double max) {
        super(name, defaultValue);
        this.min = min;
        this.max = max;
    }

    public double getMin() { return min; }
    public double getMax() { return max; }
}
