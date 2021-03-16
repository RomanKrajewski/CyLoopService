package com.romankrajewski.cyloopservice;

import com.graphhopper.util.shapes.GHPoint;

import java.util.Objects;

public class PointOfInterest extends GHPoint{
    public final int osmID;
    public final String category;


    public  PointOfInterest(int osmID, double lat, double lon, String category){
        super(lat, lon);
        this.osmID = osmID;
        this.category = category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PointOfInterest that = (PointOfInterest) o;
        return osmID == that.osmID;
    }

    @Override
    public int hashCode() {
        return Objects.hash(osmID);
    }
}
