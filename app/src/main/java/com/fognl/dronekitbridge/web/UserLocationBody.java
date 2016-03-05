package com.fognl.dronekitbridge.web;

import com.google.gson.annotations.SerializedName;

/**
 * Created by kellys on 3/4/16.
 */
public class UserLocationBody {
    @SerializedName("loc")
    private UserLocation location;

    public UserLocationBody(UserLocation location) {
        super();
        this.location = location;
    }

    public UserLocationBody() {
        this(null);
    }

    public UserLocation getLocation() {
        return location;
    }

    public void setLocation(UserLocation location) {
        this.location = location;
    }
}
