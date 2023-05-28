package io.proj3ct.TestBlaBlaBot.service;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.telegram.telegrambots.meta.api.objects.Location;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MyRouter {
    GraphHopper hopper;
    public MyRouter() {
        hopper = createGraphHopperInstance("E:\\Java\\TestBlaBlaBot\\src\\main\\resources\\crimean-fed-district-latest.osm.pbf");

    }
    public boolean isSuitableTo(Location pasTo, Location tripFrom, Location tripTo) {
        boolean result = false;
        List<GHPoint> tripPoints = routing(hopper, tripFrom.getLongitude(), tripFrom.getLatitude(),
                tripTo.getLongitude(), tripTo.getLatitude());
        for (int i = 0; i < tripPoints.size(); i++) {
            if ((pasTo.getLatitude() < Double.sum(tripPoints.get(i).getLat(), 0.2)) &&
                    ((pasTo.getLatitude() > Double.sum(tripPoints.get(i).getLat(), -0.2))) &&
                    (pasTo.getLongitude() < Double.sum(tripPoints.get(i).getLon(), 0.2)) &&
                    ((pasTo.getLongitude() > Double.sum(tripPoints.get(i).getLon(), -0.2)))) {
                return true;
            }
        }
        return result;
    }
    public boolean isSuitableFrom(Location pasFrom, Location tripFrom, Location tripTo) {
        List<GHPoint> tripPoints = routing(hopper, tripFrom.getLongitude(), tripFrom.getLatitude(),
                tripTo.getLongitude(), tripTo.getLatitude());
        for (int i = 1; i < tripPoints.size(); i++) {
            if ((pasFrom.getLatitude() < Double.sum(tripPoints.get(i).getLat(), 0.2)) &&
                    ((pasFrom.getLatitude() > Double.sum(tripPoints.get(i).getLat(), -0.2))) &&
                    (pasFrom.getLongitude() < Double.sum(tripPoints.get(i).getLon(), 0.2)) &&
                    ((pasFrom.getLongitude() > Double.sum(tripPoints.get(i).getLon(), -0.2)))) {
                return true;
            }
        }
        return false;
    }
    public boolean isSuitableTrip(Location pasFrom, Location pasTo, Location tripFrom, Location tripTo) {
        List<GHPoint> tripPoints = routing(hopper, tripFrom.getLongitude(), tripFrom.getLatitude(),
                tripTo.getLongitude(), tripTo.getLatitude());
        List<GHPoint> newTripPoints = tripPoints;
        int count = 0;
        for (int i = 1; i < tripPoints.size(); i++) {
            if ((pasFrom.getLatitude() < Double.sum(tripPoints.get(i).getLat(), 0.2)) &&
                    ((pasFrom.getLatitude() > Double.sum(tripPoints.get(i).getLat(), -0.2))) &&
                    (pasFrom.getLongitude() < Double.sum(tripPoints.get(i).getLon(), 0.2)) &&
                    ((pasFrom.getLongitude() > Double.sum(tripPoints.get(i).getLon(), -0.2)))) {
                count++;
                break;
            }
            newTripPoints.remove(i); // не правильно стоит remove, он удаляет только где истина, а надо все до истины
        }
        if (count > 0) {
            for (int i = 0; i < newTripPoints.size(); i++) {
                if ((pasTo.getLatitude() < Double.sum(newTripPoints.get(i).getLat(), 0.2)) &&
                        ((pasTo.getLatitude() > Double.sum(newTripPoints.get(i).getLat(), -0.2))) &&
                        (pasTo.getLongitude() < Double.sum(newTripPoints.get(i).getLon(), 0.2)) &&
                        ((pasTo.getLongitude() > Double.sum(newTripPoints.get(i).getLon(), -0.2)))) {
                    return true;
                }
            }
        }
        return false;
    }
    public List<GHPoint> getPoints (Location tripFrom, Location tripTo) {
        return routing(hopper, tripFrom.getLongitude(), tripFrom.getLatitude(),
                tripTo.getLongitude(), tripTo.getLatitude());
    }
    public List<GHPoint> routing(GraphHopper hopper, double fromLon, double fromLat, double toLon, double toLat) {
        GHRequest req = new GHRequest(fromLat, fromLon, toLat, toLon).
                    setProfile("car").
                    setLocale(Locale.US);
        GHResponse rsp = hopper.route(req);

        if (rsp.hasErrors())
            throw new RuntimeException(rsp.getErrors().toString());
        ResponsePath path = rsp.getBest();
        PointList points = path.getPoints();
        List<GHPoint> pointList = new ArrayList<>();
        DecimalFormat decimalFormat = new DecimalFormat("#00.00");
        for (int i = 1; i < points.size(); i++) {
            String oldLon = decimalFormat.format(points.get(i-1).getLon());
            String oldLat = decimalFormat.format(points.get(i-1).getLat());
            String newLon = decimalFormat.format(points.get(i).getLon());
            String newLat = decimalFormat.format(points.get(i).getLat());
            if (!oldLon.equals(newLon) && !oldLat.equals(newLat)) {
                pointList.add(points.get(i));
            }
        }
        return pointList;
    }

    private static GraphHopper createGraphHopperInstance(String ghLoc) {
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(ghLoc);
        // specify where to store graphhopper files
        hopper.setGraphHopperLocation("target/routing-graph-cache");

        // see docs/core/profiles.md to learn more about profiles
        hopper.setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(false));

        // this enables speed mode for the profile we called car
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));

        // now this can take minutes if it imports or a few seconds for loading of course this is dependent on the area you import
        hopper.importOrLoad();
        return hopper;
    }
}
