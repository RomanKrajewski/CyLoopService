package com.romankrajewski.cyloopservice;

import com.graphhopper.util.shapes.GHPoint;

import java.util.List;

public interface GeometryBuilder {
    List<? extends GHPoint> getNextGeometry();
    void setRouteLength(int routeLength);
}
