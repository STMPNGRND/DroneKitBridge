package com.fognl.dronekitbridge.web;

/**
 * Created by kellys on 3/4/16.
 */
public class UserLocationPostBody extends UserLocationBody {
    private String groupId;
    private String userId;

    public UserLocationPostBody() {
        super();
    }

    public UserLocationPostBody(String groupId, String userId, UserLocation loc) {
        super(loc);
        this.groupId = groupId;
        this.userId = userId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
