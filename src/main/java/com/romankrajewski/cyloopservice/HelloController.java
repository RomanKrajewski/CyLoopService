package com.romankrajewski.cyloopservice;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.PointList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Locale;

@RestController
public class HelloController {

    @Autowired
    private GraphService graphService;

    @RequestMapping("/")
    public String index() {
        return "Greetings from Spring Boot!";
    }

    @RequestMapping("/route")
    public List<RoutePOJO> route(@RequestParam(value = "lat") double lat, @RequestParam(value = "lng") double lng, @RequestParam(value = "length") int length){
        return graphService.route(lat, lng, length);
    }
}