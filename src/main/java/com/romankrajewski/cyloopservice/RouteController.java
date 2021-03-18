package com.romankrajewski.cyloopservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
public class RouteController {

    @Autowired
    private GraphService graphService;

    @RequestMapping("/")
    public String index() {
        return "Greetings from Spring Boot!";
    }

    @RequestMapping("/route")
    public List<RoutePOJO> route(@RequestParam(value = "lat") double lat,
                                 @RequestParam(value = "lng") double lng,
                                 @RequestParam(value = "length") int length,
                                 @RequestParam(value="vehicle") String vehicle){
        return graphService.route(lat, lng, length, Collections.emptyList(), Vehicle.fromString(vehicle));
    }

    @RequestMapping("/poiroute")
    public List<RoutePOJO> poiroute(@RequestParam(value = "lat") double lat,
                                    @RequestParam(value = "lng") double lng,
                                    @RequestParam(value = "length") int length,
                                    @RequestParam(value = "category") List<String> categories,
                                    @RequestParam(value="vehicle") String vehicle){
        return graphService.route(lat,lng,length, categories, Vehicle.fromString(vehicle));
    }
}