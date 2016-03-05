package com.fognl.dronekitbridge.web;

import android.location.Location;

/**
 * Created by kellys on 3/4/16.
 */
public class UserLocation {

    public static UserLocation populate(UserLocation ul, Location loc) {

        ul.lat = loc.getLatitude();
        ul.lng = loc.getLongitude();
        ul.altitude = loc.getAltitude();
        ul.accuracy = loc.getAccuracy();
        ul.speed = loc.getSpeed();
        ul.heading = loc.getBearing();
        ul.time = loc.getTime();

        return ul;
    }

    private double lat;
    private double lng;
    private double altitude;
    private float speed;
    private float heading;
    private float accuracy;
    private long time;

    public UserLocation() {
        super();
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public float getHeading() {
        return heading;
    }

    public void setHeading(float heading) {
        this.heading = heading;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    @Override
    public String toString() {
        return "UserLocation{" +
                "lat=" + lat +
                ", lng=" + lng +
                ", altitude=" + altitude +
                ", speed=" + speed +
                ", heading=" + heading +
                ", accuracy=" + accuracy +
                ", time=" + time +
                '}';
    }
}
