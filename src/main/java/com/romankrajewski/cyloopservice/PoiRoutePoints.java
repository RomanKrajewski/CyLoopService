package com.romankrajewski.cyloopservice;

import com.graphhopper.util.shapes.GHPoint;

import java.util.List;

public class PoiRoutePoints {
    public final List<GHPoint> pois;
    public final int estimatedRouteLength;

    public PoiRoutePoints(int estimatedRouteLength, List<GHPoint> pois){
        this.estimatedRouteLength = estimatedRouteLength;
        this.pois = pois;
    };
}
