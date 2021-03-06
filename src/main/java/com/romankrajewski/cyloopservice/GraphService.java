package com.romankrajewski.cyloopservice;

import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.IntSet;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.routing.*;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.AvoidEdgesWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.exceptions.PointNotFoundException;
import jackson.GraphHopperConfigModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class GraphService {
    Logger logger = LoggerFactory.getLogger(GraphService.class);
    GraphHopper hopper;

    @PostConstruct
    private void postConstruct(){
        hopper = new GraphHopper();
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        objectMapper.registerModule(new GraphHopperConfigModule());

        GraphHopperConfig config = null;

        try {
            config = objectMapper.readValue(getClass().getClassLoader().getResourceAsStream("./graphhopper-config.yaml"), GraphHopperConfig.class);
        } catch (IOException e) {
            e.printStackTrace();
        }

        hopper.init(config);
        // now this can take minutes if it imports or a few seconds for loading of course this is dependent on the area you import
        hopper.importOrLoad();
    }

    @PreDestroy
    public void preDestroy(){
        hopper.close();
    }

    public List<RoutePOJO> route(double startLat, double startLng, int routeLength){

        var numPoints = 3;
        var numRoutes = 10;
        var maxRetries = 30;

        var locationIndex = hopper.getLocationIndex();
        var bikeweighting = hopper.createWeighting(hopper.getProfile("bike"), new PMap());

        //Only consider Edges that are available to bikes
        EdgeFilter edgeFilter = edgeState -> !Double.isInfinite(bikeweighting.calcEdgeWeightWithAccess(edgeState, false))
                || !Double.isInfinite(bikeweighting.calcEdgeWeightWithAccess(edgeState, true));

        var startSnap = locationIndex.findClosest(startLat, startLng, edgeFilter);

        if(!startSnap.isValid()){
            throw new PointNotFoundException("Can't find Node for start Coordinates", 0);
        }
        List<RoutePOJO> routes = new LinkedList<>();
        Random random = new Random();
        var retryCount = 0;
        var algoOpts = AlgorithmOptions.start().algorithm(Parameters.Algorithms.ASTAR_BI).weighting(bikeweighting).build();
        var estimatedBeelineDistance = (int) (routeLength * 0.8);

        while (routes.size() < numRoutes && retryCount < maxRetries){
            var heading = random.nextInt(360);
            List<Snap> snaps = null;
            try {
                snaps = generateRouteGeometry(estimatedBeelineDistance, numPoints, locationIndex, edgeFilter, startSnap, heading);
            }catch (PointNotFoundException pnfe){
                maxRetries ++;
                logger.debug(pnfe.getMessage());
                continue;
            }
            var queryGraph = QueryGraph.create(hopper.getGraphHopperStorage(), snaps);

            List<Path> paths = calculatePaths(bikeweighting, algoOpts, snaps, queryGraph);
            if(paths == null){
                retryCount ++;
                continue;
            }
            Path mergedPath = mergePaths(bikeweighting, queryGraph, paths);
            //check if route has an acceptable length and recalculate beeline distance estimate if necessary
            if(mergedPath.getDistance() > routeLength + routeLength/10.0 || mergedPath.getDistance() < routeLength - routeLength/10.0){
                estimatedBeelineDistance =(int) ((routeLength/mergedPath.getDistance()) * estimatedBeelineDistance);
                retryCount ++;
                continue;
            }
            routes.add(new RoutePOJO(Collections.singletonList(mergedPath)));
        }

        return routes;
    }

    private List<Snap> generateRouteGeometry(int routeLength, int numPoints, LocationIndex locationIndex, EdgeFilter edgeFilter, Snap startSnap, int heading) {
        var lastSnap = startSnap;
        List<Snap> snaps = new LinkedList<>();
        snaps.add(lastSnap);
        for (int i = 0; i < numPoints - 1; i++) {
            var nextPoint = DistanceCalcEarth.DIST_EARTH.projectCoordinate(
                    lastSnap.getSnappedPoint().getLat(), lastSnap.getSnappedPoint().getLon(),
                    routeLength /(double) numPoints, heading + i*(360/(double) numPoints));
            var nextSnap = locationIndex.findClosest(nextPoint.getLat(), nextPoint.getLon(), edgeFilter);
            if(!nextSnap.isValid()){
                throw new PointNotFoundException("Point not found for Roundtrip", i);
            }
            snaps.add(nextSnap);
            lastSnap = nextSnap;
        }
        return snaps;
    }

    private List<Path> calculatePaths(Weighting bikeweighting, AlgorithmOptions algoOpts, List<Snap> snaps, QueryGraph queryGraph) {
        IntSet previousEdges = new IntHashSet();
        AvoidEdgesWeighting avoidPreviousPathsWeighting = new AvoidEdgesWeighting(bikeweighting).setEdgePenaltyFactor(5);
        avoidPreviousPathsWeighting.setAvoidedEdges(previousEdges);
        AlgorithmOptions currentRouteAlgoOpts = AlgorithmOptions.start(algoOpts).
                weighting(avoidPreviousPathsWeighting).build();
        var pathCalculator = new FlexiblePathCalculator(queryGraph,
                new RoutingAlgorithmFactorySimple(), currentRouteAlgoOpts);

        List<Path> paths = new LinkedList<>();

        for (int i = 0; i < snaps.size(); i++) {

            //Use Tower Nodes (actual junctions) for generated Points. Use closest node for start end end, because thats the actual starting location of the trip
            var nodeA = i == 0 ? snaps.get(0).getClosestNode() : snaps.get(i).getClosestEdge().getAdjNode();
            var nodeB = (i+1) % snaps.size() == 0 ? snaps.get(0).getClosestNode() : snaps.get((i+1) % snaps.size()).getClosestEdge().getAdjNode();
            Path path = null;
            try {
                path = pathCalculator.calcPaths(nodeA, nodeB, new EdgeRestrictions()).get(0);
            }catch (IllegalArgumentException | IllegalStateException ex){
                logger.debug(ex.getMessage());
                return null;
            }

            for (IntCursor c : path.getEdges()) {
                previousEdges.add(c.value);
            }

            paths.add(path);
        }
        return paths;
    }

    private Path mergePaths(Weighting bikeWeighting, QueryGraph queryGraph, List<Path> paths) {
        int[] pathStartIndices = new int[paths.size()];
        List<List<EdgeIteratorState>> pathEdgeLists = paths.stream().map(Path::calcEdges).collect(Collectors.toList());

        var mergedPath = new Path(queryGraph);
        mergedPath.setFromNode(pathEdgeLists.get(0).get(0).getBaseNode());

        //Merge paths and cut off tails (parts of the route, where 2 consecutive paths cross each other)
        for(int currentPath = 0; currentPath < paths.size(); currentPath++){
            var nextPath = (currentPath+1) % paths.size();
            var crossingFound = false;
            for (int j = pathStartIndices[currentPath]; j < pathEdgeLists.get(currentPath).size() && !crossingFound; j++) {
                var currentEdge = pathEdgeLists.get(currentPath).get(j);
                mergedPath.addEdge(currentEdge.getEdge());
                mergedPath.addDistance(currentEdge.getDistance());
                mergedPath.addTime(bikeWeighting.calcEdgeMillis(currentEdge, false));
                for (int k = pathEdgeLists.get(nextPath).size() -1; k > 0; k--) {
                    if(currentEdge.getAdjNode() == pathEdgeLists.get(nextPath).get(k).getBaseNode()){
                        pathStartIndices[nextPath] = k;
                        crossingFound = true;
                    }
                }
            }
        }
        return mergedPath;
    }
}
