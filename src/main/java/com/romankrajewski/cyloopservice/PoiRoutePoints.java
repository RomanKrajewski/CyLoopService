package com.romankrajewski.cyloopservice;

import java.util.List;

public class PoiRoutePoints {
    public final List<PointOfInterest> pois;
    public final int estimatedRouteLength;

    public PoiRoutePoints(int estimatedRouteLength, List<PointOfInterest> pois){
        this.estimatedRouteLength = estimatedRouteLength;
        this.pois = pois;
    };
}
