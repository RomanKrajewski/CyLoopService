package com.romankrajewski.cyloopservice;

import com.graphhopper.ResponsePath;
import com.graphhopper.routing.Path;

import java.util.LinkedList;
import java.util.List;

public class RoutePOJO {
    public double[] longitudes;
    public double[] lattitudes;
    public double[] elevations;
    public PointOfInterest[] pointsOfInterest;
    public double totalLength;


    public RoutePOJO(ResponsePath path) {
        this.longitudes = new double[path.getPoints().size()];
        this.lattitudes =  new double[path.getPoints().size()];
        this.elevations =  new double[path.getPoints().size()];
        this.totalLength = path.getDistance();
        for (int i = 0; i < path.getPoints().size(); i++) {
            longitudes[i] = path.getPoints().getLon(i);
            lattitudes[i] = path.getPoints().getLat(i);
            elevations[i] = path.getPoints().getEle(i);
        }
    }

    public RoutePOJO(List<Path> paths, List<PointOfInterest> pointsOfInterest){
        LinkedList<Double> longitudes = new LinkedList<>();
        LinkedList<Double> lattitudes = new LinkedList<>();
        LinkedList<Double> elevations = new LinkedList<>();
        for (Path path : paths) {
            var pointList = path.calcPoints();
            for (int i = 0; i < pointList.size(); i++) {
                longitudes.add(pointList.getLon(i));
                lattitudes.add(pointList.getLat(i));
                elevations.add(pointList.getEle(i));
            }
            this.totalLength += path.getDistance();
        }
        this.longitudes = longitudes.stream().mapToDouble(Double::doubleValue).toArray();
        this.lattitudes = lattitudes.stream().mapToDouble(Double::doubleValue).toArray();
        this.elevations = elevations.stream().mapToDouble(Double::doubleValue).toArray();
        this.pointsOfInterest = pointsOfInterest.toArray(PointOfInterest[]::new);
    }
}
