package com.romankrajewski.cyloopservice;

public class PoiRoutePoints {
    public final PointOfInterest[] pois;
    public final int estimatedRouteLength;

    public PoiRoutePoints(int estimatedRouteLength, PointOfInterest... pois){
        this.estimatedRouteLength = estimatedRouteLength;
        this.pois = pois;
    };
}
