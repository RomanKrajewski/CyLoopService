package com.romankrajewski.cyloopservice;

import com.graphhopper.ResponsePath;
import com.graphhopper.util.PointList;

public class RoutePOJO {
    public double[] longitudes;
    public double[] lattitudes;
    public double[] elevations;
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
}
