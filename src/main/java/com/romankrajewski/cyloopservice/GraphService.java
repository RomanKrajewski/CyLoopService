package com.romankrajewski.cyloopservice;

import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.IntSet;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.graphhopper.*;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.reader.dem.MultiSourceElevationProvider;
import com.graphhopper.reader.dem.SRTMProvider;
import com.graphhopper.routing.*;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.AvoidEdgesWeighting;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;
import jackson.GraphHopperConfigModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GraphService {
    Logger logger = LoggerFactory.getLogger(GraphService.class);
    GraphHopper hopper;

    @PostConstruct
    private void postConstruct(){
        hopper = createGraphHopperInstance();
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
        var numRetries = 0;
        var algoOpts = AlgorithmOptions.start().algorithm(Parameters.Algorithms.ASTAR_BI).weighting(bikeweighting).build();

        while (routes.size() < numRoutes && numRetries < maxRetries){
            var heading = random.nextInt(360);
            var lastSnap = startSnap;
            List<Snap> snaps = new LinkedList<>();
            snaps.add(lastSnap);
            for (int i = 0; i < numPoints - 1; i++) {
                var nextPoint = DistanceCalcEarth.DIST_EARTH.projectCoordinate(
                        lastSnap.getSnappedPoint().getLat(), lastSnap.getSnappedPoint().getLon(),
                        routeLength/(double)numPoints, heading + i*(360/(double)numPoints));
                var nextSnap = locationIndex.findClosest(nextPoint.getLat(), nextPoint.getLon(), edgeFilter);
                if(!nextSnap.isValid()){
                    numRetries ++;
                    break;
                }
                snaps.add(nextSnap);
                lastSnap = nextSnap;
            }
            IntSet previousEdges = new IntHashSet();
            AvoidEdgesWeighting avoidPreviousPathsWeighting = new AvoidEdgesWeighting(bikeweighting).setEdgePenaltyFactor(5);
            avoidPreviousPathsWeighting.setAvoidedEdges(previousEdges);
            AlgorithmOptions currentRouteAlgoOpts = AlgorithmOptions.start(algoOpts).
                    weighting(avoidPreviousPathsWeighting).build();
            var queryGraph = QueryGraph.create(hopper.getGraphHopperStorage(), snaps);
            var pathCalculator = new FlexiblePathCalculator(queryGraph,
                    new RoutingAlgorithmFactorySimple(), currentRouteAlgoOpts);

            List<Path> paths = new LinkedList<>();

            for (int i = 0; i < numPoints; i++) {

                //Use Tower Nodes (actual junctions) for generated Points. Use closest node for start end end, because thats the actual starting location of the trip
                var nodeA = i == 0 ? snaps.get(0).getClosestNode() : snaps.get(i).getClosestEdge().getAdjNode();
                var nodeB = (i+1) % numPoints == 0 ? snaps.get(0).getClosestNode() : snaps.get((i+1) % numPoints).getClosestEdge().getAdjNode();
                var path = pathCalculator.calcPaths(nodeA, nodeB, new EdgeRestrictions()).get(0);

                for (IntCursor c : path.getEdges()) {
                    previousEdges.add(c.value);
                }

                paths.add(path);
            }
            int[] pathStartIndices = new int[paths.size()];
            List<List<EdgeIteratorState>> pathEdgeLists = paths.stream().map(Path::calcEdges).collect(Collectors.toList());

            var mergedPath = new Path(queryGraph);
            var tmpNode = pathEdgeLists.get(0).get(0).getBaseNode();
            mergedPath.setFromNode(tmpNode);
            //Merge paths and cut off tails
            for(int currentPath =0; currentPath < paths.size(); currentPath++){
                var nextPath = (currentPath+1) % paths.size();
                var crossingFound = false;
                for (int j = pathStartIndices[currentPath]; j < pathEdgeLists.get(currentPath).size() && !crossingFound; j++) {
                    var currentEdge = pathEdgeLists.get(currentPath).get(j);
                    mergedPath.addEdge(currentEdge.getEdge());
                    mergedPath.addDistance(currentEdge.getDistance());
                    mergedPath.addTime(bikeweighting.calcEdgeMillis(currentEdge, false));
                    for (int k = pathEdgeLists.get(nextPath).size() -1; k > 0; k--) {
                        if(currentEdge.getAdjNode() == pathEdgeLists.get(nextPath).get(k).getBaseNode()){
                            pathStartIndices[nextPath] = k;
                            crossingFound = true;
                        }
                    }
                }
            }
            routes.add(new RoutePOJO(Collections.singletonList(mergedPath)));
        }

        return routes;
    }

    private GraphHopper createGraphHopperInstance(){
        GraphHopper hopper = new GraphHopper();
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
        return hopper;
    }


}
