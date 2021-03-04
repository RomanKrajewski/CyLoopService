package com.romankrajewski.cyloopservice;

import com.graphhopper.*;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.reader.dem.SRTMProvider;
import com.graphhopper.routing.util.EncodingManager;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Locale;

@Service
public class GraphService {

    GraphHopper hopper;

    @PostConstruct
    private void postConstruct(){
        hopper = createGraphHopperInstance("mapdata/brandenburg-latest.osm.pbf");
    }

    @PreDestroy
    public void preDestroy(){
        hopper.close();
    }

    public RoutePOJO route(){
        GHRequest req = new GHRequest(52.512195, 13.412353, 52.546352, 13.343703).
                // note that we have to specify which profile we are using even when there is only one like here
                        setProfile("car").
                // define the language for the turn instructions
                        setLocale(Locale.US);
        GHResponse rsp = hopper.route(req);

        // handle errors
        if (rsp.hasErrors())
            throw new RuntimeException(rsp.getErrors().toString());

        // use the best path, see the GHResponse class for more possibilities.
        ResponsePath path = rsp.getBest();

        // points, distance in meters and time in millis of the full path
        return new RoutePOJO(path);
    }

    private GraphHopper createGraphHopperInstance(String ghLoc){
        GraphHopper hopper = new GraphHopper();
        hopper.setElevationProvider(new SRTMProvider());
        hopper.setOSMFile(ghLoc);
        // specify where to store graphhopper files
        hopper.setGraphHopperLocation("mapdata/routing-graph-cache");
        hopper.setEncodingManager(EncodingManager.create("car"));

        // see docs/core/profiles.md to learn more about profiles
        hopper.setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(false));

        // this enables speed mode for the profile we called car
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));

        // now this can take minutes if it imports or a few seconds for loading of course this is dependent on the area you import
        hopper.importOrLoad();
        return hopper;
    }


}
