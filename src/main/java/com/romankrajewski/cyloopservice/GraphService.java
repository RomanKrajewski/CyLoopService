package com.romankrajewski.cyloopservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.graphhopper.*;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.reader.dem.MultiSourceElevationProvider;
import com.graphhopper.reader.dem.SRTMProvider;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.FlexiblePathCalculator;
import com.graphhopper.routing.RoundTripRouting;
import com.graphhopper.routing.RoutingAlgorithmFactorySimple;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import jackson.GraphHopperConfigModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    public RoutePOJO route(double startLat, double startLng, int routeLength){

        var roundtripHints = new PMap();
        roundtripHints.putObject(Parameters.Algorithms.RoundTrip.DISTANCE, routeLength);
        roundtripHints.putObject(Parameters.Algorithms.RoundTrip.POINTS, 3);

        RoundTripRouting.Params rtrps = new RoundTripRouting.Params(roundtripHints, 0, 3);
        var points = new ArrayList<GHPoint>();
        points.add(new GHPoint(startLat, startLng));
        var bikeweighting = hopper.createWeighting(hopper.getProfile("bike"), new PMap());
        var snaps = RoundTripRouting.lookup(points, bikeweighting, hopper.getLocationIndex(), rtrps);
        var algoOpts = AlgorithmOptions.start().algorithm(Parameters.Algorithms.ASTAR_BI).weighting(bikeweighting).build();
        var result = RoundTripRouting.calcPaths(snaps,
                new FlexiblePathCalculator(QueryGraph.create(hopper.getGraphHopperStorage(), snaps),
                        new RoutingAlgorithmFactorySimple(), algoOpts));
        // points, distance in meters and time in millis of the full path
        return new RoutePOJO(result.paths);
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
