package com.fognl.dronekitbridge.web;

/**
 * Created by kellys on 3/4/16.
 */
public class ServerResponse {
    String status;
    String error;

    public String getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public boolean hasError() { return (error != null); }

    public String getMessage() {
        return (hasError())? getError(): getStatus();
    }
}
