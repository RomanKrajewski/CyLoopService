package com.romankrajewski.cyloopservice;

public enum Vehicle {
    BIKE("bike"),
    RACING_BIKE("racingbike"),
    MTB("mtb"),
    HIKE("hike");

    private final String stringValue;
    Vehicle(final String s) { stringValue = s; }
    public String toString() { return stringValue; }
    public static Vehicle fromString(String text) {
        for (Vehicle b : Vehicle.values()) {
            if (b.stringValue.equals(text)) {
                return b;
            }
        }
        throw new IllegalStateException();
    }
}
