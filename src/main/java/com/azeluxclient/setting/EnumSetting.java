package com.azeluxclient.setting;

public class EnumSetting<E extends Enum<E>> extends Setting<E> {
    private final E[] values;

    @SuppressWarnings("unchecked")
    public EnumSetting(String name, E defaultValue) {
        super(name, defaultValue);
        this.values = (E[]) defaultValue.getDeclaringClass().getEnumConstants();
    }

    public void cycle() {
        int next = (value.ordinal() + 1) % values.length;
        value = values[next];
    }

    public E[] getValues() { return values; }
}
