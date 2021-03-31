package com.romankrajewski.cyloopservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.shapes.GHPoint;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.tour.ChristofidesThreeHalvesApproxMetricTSP;
import org.jgrapht.alg.tour.HeldKarpTSP;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
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
    private static final boolean USE_TSP = true;
    Logger logger = LoggerFactory.getLogger(GraphService.class);
    private final int ROUTE_POI_COUNT = 3;
    private TreeMap <Integer, PoiRoutePoints> combinations;
    private final int POIS_TO_QUERY = 1000;
    private final int POIS_TO_COMBINE = 100;
    private ChristofidesThreeHalvesApproxMetricTSP<GHPoint, DefaultWeightedEdge> christofidesTSP = new ChristofidesThreeHalvesApproxMetricTSP<>();
    private HeldKarpTSP<GHPoint, DefaultWeightedEdge> heldKarpTSP = new HeldKarpTSP<>();

    private int routeLength;

    public  PoiGeometryBuilder(int routeLength, GHPoint start, List<String> poiCategories){
        this.routeLength = routeLength;
        
        var timeStamp = System.currentTimeMillis();
        List<PointOfInterest> pointsOfInterest = queryPOIs(poiCategories,
                start.getLat(), start.getLon(), routeLength/3);
        logger.info("Queried POIs in " + (System.currentTimeMillis() - timeStamp)/1000.0 + "s");

        String[] foundCategories = pointsOfInterest.stream().map(pointOfInterest -> pointOfInterest.category).distinct().toArray(String[] :: new);
        PointOfInterest[][] arraysToCombine = new PointOfInterest[ROUTE_POI_COUNT][];
        for (int i = 0; i < arraysToCombine.length; i++) {
            Collections.shuffle(pointsOfInterest);
            int finalI = i;
            arraysToCombine[i] = pointsOfInterest
                    .stream()
                    .filter(pointOfInterest -> pointOfInterest.category.equals(foundCategories[finalI %foundCategories.length]))
                    .limit(POIS_TO_COMBINE)
                    .toArray(PointOfInterest[]::new);
        }
        combinations = new TreeMap<>();
        timeStamp = System.currentTimeMillis();
        combine(combinations, new LinkedList<>() ,0, arraysToCombine, start);
        var combinationsTime = (System.currentTimeMillis() - timeStamp)/1000.0;
        logger.info("Generated" + combinations.size()+ " combinations in " + combinationsTime + "s");
    }

    public List<? extends GHPoint> getNextGeometry(){
        var upper = combinations.ceilingEntry(routeLength);
        var lower = combinations.lowerEntry(routeLength);
        Map.Entry<Integer, PoiRoutePoints> bestEntry;
        if(lower == null && upper == null){
            return null;
        }
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

    public void combine(TreeMap<Integer, PoiRoutePoints> combinations, List<GHPoint> current,int currentIndex, PointOfInterest[][] arraysToCombine, GHPoint start){
        if(current.size() == arraysToCombine.length){
            if(current.stream().distinct().count() < current.size()){
                return;
            }
            current.add(0,start);
            if(USE_TSP){
                var bestPathApprox = tsp(current);
                combinations.put((int) bestPathApprox.getWeight(), new PoiRoutePoints((int) bestPathApprox.getWeight(), bestPathApprox.getVertexList()));
            }else {
                int estimatedRouteLength = 0;
                for (int i = 0; i < current.size(); i++) {
                    estimatedRouteLength += DistanceCalcEarth.DIST_EARTH.calcDist(current.get(i).lat, current.get(i).lon,
                            current.get((i+1)%current.size()).lat, current.get((i+1)%current.size()).lon);
                    combinations.put(estimatedRouteLength, new PoiRoutePoints(estimatedRouteLength, current));
                }
            }
        }else{
            for (int i = 0; i < arraysToCombine[currentIndex].length; i++) {
                List<GHPoint> currentCopy = new LinkedList<>(current);
                currentCopy.add(arraysToCombine[currentIndex][i]);
                combine(combinations, currentCopy,(currentIndex+1)%arraysToCombine.length, arraysToCombine, start);
            }
        }
    }

    private GraphPath<GHPoint, DefaultWeightedEdge> tsp(List<GHPoint> points){
        SimpleWeightedGraph<GHPoint, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        points.forEach(graph::addVertex);
        for (int i = 0; i < points.size(); i++) {
            for (int j = i +1; j < points.size(); j++) {
                var edge = graph.addEdge(points.get(i), points.get(j));
                graph.setEdgeWeight(edge, DistanceCalcEarth.DIST_EARTH.calcDist(points.get(i).lat, points.get(i).lon,
                        points.get((j)%points.size()).lat, points.get((j)%points.size()).lon));
            }
        }
        return heldKarpTSP.getTour(graph);
    }


    List<PointOfInterest> queryPOIs(List<String> categories, double aroundLat, double aroundLong, int radius) {
        var client = HttpClient.newHttpClient();
        var uriBase = "https://overpass.kumi.systems/api/interpreter?data=";

        StringBuilder queryUnencodedBuilder = new StringBuilder("[out:json][timeout:300];\n");
        categories.forEach(category -> queryUnencodedBuilder.append(String.format(Locale.US,
                "node[\"tourism\"=\"%s\"](around:%d, %f, %f)->.%stourism;\n", category, radius, aroundLat, aroundLong, category)));
        categories.forEach(category -> queryUnencodedBuilder.append(String.format(Locale.US,
                "node[\"amenity\"=\"%s\"](around:%d, %f, %f)->.%samenity;\n", category, radius, aroundLat, aroundLong, category)));
        categories.forEach(category -> queryUnencodedBuilder.append(String.format(Locale.US, ".%stourism out body %d;", category, POIS_TO_QUERY)));
        categories.forEach(category -> queryUnencodedBuilder.append(String.format(Locale.US, ".%samenity out body %d;", category, POIS_TO_QUERY)));


        var queryUnencoded = queryUnencodedBuilder.toString();

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
            String category = element.get("tags").get("amenity") != null && categories.contains(element.get("tags").get("amenity").asText()) ?
                    element.get("tags").get("amenity").asText() : element.get("tags").get("tourism").asText();
            pointsOfInterest.add(new PointOfInterest(element.get("id").asInt(), element.get("lat").asDouble(), element.get("lon").asDouble(), category));
        }
        return pointsOfInterest;
    }
}
