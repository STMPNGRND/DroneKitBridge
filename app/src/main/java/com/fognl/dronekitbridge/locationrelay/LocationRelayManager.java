package com.fognl.dronekitbridge.locationrelay;

import android.content.Context;
import android.location.Location;

import com.fognl.dronekitbridge.R;
import com.fognl.dronekitbridge.web.LocationRelayService;
import com.fognl.dronekitbridge.web.ServerResponse;
import com.fognl.dronekitbridge.web.UserLocation;
import com.fognl.dronekitbridge.web.UserLocationPostBody;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by kellys on 3/4/16.
 */
public class LocationRelayManager {
    static final String TAG = LocationRelayManager.class.getSimpleName();

    public interface RelayCallback<T> {
        void complete(T result);
        void error(Throwable error);
    }

    private static LocationRelayService sService;

    public static void sendLocation(
            Context context, String groupId, String userId, Location location, final Callback<ServerResponse> callback) {
        final UserLocation loc = UserLocation.populate(new UserLocation(), location);

        final LocationRelayService service = getLocationRelayService(context);
        final UserLocationPostBody body = new UserLocationPostBody(groupId, userId, loc);

        Call<ServerResponse> call = service.sendLocation(body);
        call.enqueue(new Callback<ServerResponse>() {
            @Override
            public void onResponse(Call<ServerResponse> call, Response<ServerResponse> response) {
                ServerResponse sr = response.body();
                if(sr != null) {
                    callback.onResponse(call, response);
                }
                else {
                    callback.onFailure(call, new Exception("Server appears to be down (or dyno is stopped)"));
                }
            }

            @Override
            public void onFailure(Call<ServerResponse> call, Throwable t) {
                callback.onFailure(call, t);
            }
        });
    }

    public static void retrieveGroups(Context context, final Callback<List<String>> callback) {
        final LocationRelayService service = getLocationRelayService(context);
        Call<List<String>> call = service.retrieveGroupList();
        call.enqueue(new Callback<List<String>>() {

            @Override
            public void onResponse(Call<List<String>> call, Response<List<String>> response) {
                List<String> data = response.body();
                if(data != null) {
                    callback.onResponse(call, response);
                }
                else {
                    callback.onFailure(call, new Exception("Server is down or dyno is stopped"));
                }
            }

            @Override
            public void onFailure(Call<List<String>> call, Throwable t) {
                callback.onFailure(call, t);
            }
        });
    }

    public static void retrieveGroupLocations(Context context, String groupId,
                                              final RelayCallback<Map<String, UserLocation>> callback) {

        final LocationRelayService service = getLocationRelayService(context);

        // TODO: Figure out how to get Retrofit to just map the response to an object.
        final Call<ResponseBody> call = service.followGroup(groupId);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if(response != null && response.body() != null) {
                        final HashMap<String, UserLocation> map = new HashMap<String, UserLocation>();

                        String str = response.body().string();
                        JSONObject jo = new JSONObject(str);

                        JSONArray names = jo.names();

                        if(names != null) {
                            final int len = names.length();
                            for(int i = 0; i < len; ++i) {
                                String name = names.getString(i);
                                JSONObject sub = jo.getJSONObject(name);
                                JSONObject joLoc = sub.optJSONObject("loc");
                                if(joLoc != null) {
                                    UserLocation userLoc = UserLocation.populate(new UserLocation(), joLoc);
                                    map.put(name, userLoc);
                                }
                            }
                        }

                        callback.complete(map);
                    }
                    else {
                        callback.error(new Exception("Server is down or dyno is stopped"));
                    }
                }
                catch(Throwable ex) {
                    callback.error(ex);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.error(t);
            }
        });
    }

    public static void deleteUser(Context context,
                                  String groupId, String userId,
                                  final Callback<ServerResponse> callback) {
        LocationRelayService service = getLocationRelayService(context);

        final Call<ServerResponse> call = service.deleteUserFromGroup(groupId, userId);
        call.enqueue(new Callback<ServerResponse>() {

            @Override
            public void onResponse(Call<ServerResponse> call, Response<ServerResponse> response) {
                if(response.body() != null) {
                    callback.onResponse(call, response);
                }
                else {
                    callback.onFailure(call, new Exception("Server is down or dyno is stopped"));
                }
            }

            @Override
            public void onFailure(Call<ServerResponse> call, Throwable t) {
                callback.onFailure(call, t);
            }
        });
    }

    public static void deleteGroup(Context context, String groupId, Callback<ServerResponse> callback) {
        LocationRelayService service = getLocationRelayService(context);

        final Call<ServerResponse> call = service.deleteGroup(groupId);
        call.enqueue(callback);
    }

    public static LocationRelayService getLocationRelayService(Context context) {
        if(sService == null) {
            Retrofit retro = new Retrofit.Builder()
                    .baseUrl(context.getString(R.string.retro_base_url))
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            sService = retro.create(LocationRelayService.class);
        }

        return sService;
    }
}
