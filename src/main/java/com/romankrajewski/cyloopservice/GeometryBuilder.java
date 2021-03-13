package com.romankrajewski.cyloopservice;

import com.graphhopper.util.shapes.GHPoint;

import java.util.List;

public interface GeometryBuilder {
    public abstract List<GHPoint> getNextGeometry();
    public abstract void setRouteLength(int routeLength);
}
