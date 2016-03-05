package com.fognl.dronekitbridge.web;

import android.location.Location;

import org.json.JSONObject;

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

    public static UserLocation populate(UserLocation ul, JSONObject jo) {
        ul.lat = jo.optDouble("lat");
        ul.lng = jo.optDouble("lng");
        ul.altitude = jo.optDouble("altitude");
        ul.speed = (float)jo.optDouble("speed");
        ul.heading = (float)jo.optDouble("heading");
        ul.accuracy = (float)jo.optDouble("accuracy");
        ul.time = jo.optLong("time");
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserLocation that = (UserLocation) o;

        if (Double.compare(that.lat, lat) != 0) return false;
        if (Double.compare(that.lng, lng) != 0) return false;
        if (Double.compare(that.altitude, altitude) != 0) return false;
        if (Float.compare(that.speed, speed) != 0) return false;
        if (Float.compare(that.heading, heading) != 0) return false;
        if (Float.compare(that.accuracy, accuracy) != 0) return false;
        return time == that.time;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(lat);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(lng);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(altitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (speed != +0.0f ? Float.floatToIntBits(speed) : 0);
        result = 31 * result + (heading != +0.0f ? Float.floatToIntBits(heading) : 0);
        result = 31 * result + (accuracy != +0.0f ? Float.floatToIntBits(accuracy) : 0);
        result = 31 * result + (int) (time ^ (time >>> 32));
        return result;
    }
}
