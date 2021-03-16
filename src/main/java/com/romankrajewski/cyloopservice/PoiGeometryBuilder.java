package com.romankrajewski.cyloopservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.shapes.GHPoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class PoiGeometryBuilder implements GeometryBuilder{
    Logger logger = LoggerFactory.getLogger(GraphService.class);
    private final int ROUTE_POI_COUNT = 3;
    private TreeMap <Integer, PoiRoutePoints> combinations;

    private int routeLength;

    public  PoiGeometryBuilder(int routeLength, GHPoint start, List<String> poiCategories){
        this.routeLength = routeLength;
        List<PointOfInterest> pointsOfInterest = queryPOIs(poiCategories,
                start.getLat(), start.getLon(), routeLength);

        String[] foundCategories = pointsOfInterest.stream().map(pointOfInterest -> pointOfInterest.category).distinct().toArray(String[] :: new);
        PointOfInterest[][] arraysToCombine = new PointOfInterest[ROUTE_POI_COUNT][];
        for (int i = 0; i < arraysToCombine.length; i++) {
            int finalI = i;
            arraysToCombine[i] = pointsOfInterest
                    .stream()
                    .filter(pointOfInterest -> pointOfInterest.category.equals(foundCategories[finalI %foundCategories.length]))
                    .limit(100)
                    .toArray(PointOfInterest[]::new);
        }
        combinations = new TreeMap<>();
        combine(combinations, new LinkedList<>() , arraysToCombine);
    }

    public List<? extends GHPoint> getNextGeometry(){
        var upper = combinations.ceilingEntry(routeLength);
        var lower = combinations.lowerEntry(routeLength);
        Map.Entry<Integer, PoiRoutePoints> bestEntry;
        if(upper == null){
            bestEntry = lower;
        }
        else if(lower == null){
            bestEntry = upper;
        }
        else {
            bestEntry = Math.abs(lower.getKey() - routeLength) < Math.abs(upper.getKey() - routeLength) ? lower : upper;
        }
        combinations.remove(bestEntry.getKey());
        return bestEntry.getValue().pois;
    }

    public void setRouteLength(int routeLength){
        this.routeLength = routeLength;
    }

    public void combine(TreeMap<Integer, PoiRoutePoints> combinations, List<PointOfInterest> current, PointOfInterest[][] arraysToCombine){
        if(current.size() == arraysToCombine.length){
            if(current.stream().distinct().count() < current.size()){
                return;
            }
            int estimatedRouteLength = 0;
            for (int i = 0; i < current.size(); i++) {
                estimatedRouteLength += DistanceCalcEarth.DIST_EARTH.calcDist(current.get(i).lat, current.get(i).lon,
                        current.get((i+1)%current.size()).lat, current.get((i+1)%current.size()).lon);
            }
            combinations.put(estimatedRouteLength, new PoiRoutePoints(estimatedRouteLength, current));
        }else{
            var currentPoiIndex = current.size();
            for (int i = 0; i < arraysToCombine[currentPoiIndex].length; i++) {
                List<PointOfInterest> currentCopy = new LinkedList<>(current);
                currentCopy.add(arraysToCombine[currentPoiIndex][i]);
                combine(combinations, currentCopy, arraysToCombine);
            }
        }
    }


    List<PointOfInterest> queryPOIs(List<String> categories, double aroundLat, double aroundLong, int radius) {
        var categoryString = String.join("|", categories);
        var client = HttpClient.newHttpClient();
        var uriBase = "https://overpass.kumi.systems/api/interpreter?data=";

        var queryUnencoded = "[out:json][timeout:300];\n" +
            String.format(Locale.US, "(node[\"tourism\"~\"^%s$\"](around:%d, %f, %f);\n", categoryString, radius, aroundLat, aroundLong) +
            String.format(Locale.US, "node[\"amenity\"~\"^%s$\"](around:%d, %f, %f););\n", categoryString, radius, aroundLat, aroundLong) +
            "out body;";

        var encodedString = URLEncoder.encode(queryUnencoded, StandardCharsets.UTF_8);
        HttpRequest request = null;
            request = HttpRequest.newBuilder(URI.create(uriBase + encodedString))
                    .header("accept", "application/json")
                    .build();

        HttpResponse<String> response = null;
        var objectMapper = new ObjectMapper();
        JsonNode node = null;

        try {
            response = client.send(request, responseInfo -> HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }

        if(response.statusCode() != 200){
            logger.error("OSM POI request failed. Statuscode:" + response.statusCode() +
                    "\n Query: " + queryUnencoded +
                    "\n Message: " + response.body());
            return null;
        }

        try {
            node =  objectMapper.readTree(response.body());
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        LinkedList<PointOfInterest> pointsOfInterest = new LinkedList<>();
        for(var element: node.get("elements")){
            String category = element.get("tags").get("amenity") != null ? element.get("tags").get("amenity").asText() : element.get("tags").get("tourism").asText();
            pointsOfInterest.add(new PointOfInterest(element.get("id").asInt(), element.get("lat").asDouble(), element.get("lon").asDouble(), category));
        }
        return pointsOfInterest;
    }
}
