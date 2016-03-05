package com.fognl.dronekitbridge.web;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Created by kellys on 3/4/16.
 */
public interface LocationRelayService {
    @POST("/follow/user")
    Call<ServerResponse> sendLocation(@Body UserLocationPostBody location);

    @DELETE("/follow/user/{groupId}/{userId}")
    Call<ServerResponse> deleteUserFromGroup(@Path("groupId") String groupId, @Path("userId") String userId);

    @DELETE("/follow/group/{groupId}")
    Call<ServerResponse> deleteGroup(@Path("groupId") String groupId);

    @GET("/follow/groups")
    Call<List<String>> retrieveGroupList();

    @GET("/follow/group/{groupId}")
    Call<ResponseBody> followGroup(@Path("groupId") String groupId);

    @GET("/follow/user/{groupId}/{userId}")
    Call<String> followUserInGroup(@Path("groupId") String groupId, @Path("userId") String userId);
}
