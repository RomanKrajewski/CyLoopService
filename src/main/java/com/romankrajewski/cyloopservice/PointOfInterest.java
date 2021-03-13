package com.romankrajewski.cyloopservice;

import com.graphhopper.util.shapes.GHPoint;

import java.util.Objects;

public class PointOfInterest {
    public final int osmID;
    public final GHPoint location;
    public final String category;


    public  PointOfInterest(int osmID, double lat, double lon, String category){
        this.osmID = osmID;
        this.location = new GHPoint(lat, lon);
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
