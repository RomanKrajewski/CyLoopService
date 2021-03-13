package com.romankrajewski.cyloopservice;

import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.shapes.GHPoint;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class PolygonGeometryBuilder implements GeometryBuilder{


    private int routeLength;
    private int numPoints;
    private GHPoint start;
    private Random random;

    public PolygonGeometryBuilder(int routeLength, int numPoints, GHPoint start) {
        this.routeLength = routeLength;
        this.numPoints = numPoints;
        this.start = start;
        random = new Random();
    }

    public List<GHPoint> getNextGeometry(){
        var heading = random.nextInt(360);
        var lastPoint = start;
        List<GHPoint> points = new LinkedList<>();
        points.add(lastPoint);
        for (int i = 0; i < numPoints - 1; i++) {
            var nextPoint = DistanceCalcEarth.DIST_EARTH.projectCoordinate(
                    lastPoint.getLat(), lastPoint.getLon(),
                    routeLength /(double) numPoints, heading + i*(360/(double) numPoints));
            points.add(nextPoint);
            lastPoint = nextPoint;
        }
        return points;
    }

    public void setRouteLength(int routeLength) {
        this.routeLength = routeLength;
    }
}
