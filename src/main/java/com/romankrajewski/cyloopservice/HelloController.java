package com.romankrajewski.cyloopservice;

import com.graphhopper.util.shapes.GHPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

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

    @RequestMapping("/poiroute")
    public int poiRoute(@RequestParam(value = "lat") double lat, @RequestParam(value = "lng") double lng,
                                          @RequestParam(value = "length") int length, @RequestParam(value = "category") String category){
        return new PoiGeometryBuilder(length, new GHPoint(lat, lng) , Collections.singletonList(category)).poiCombinations();
    }
}