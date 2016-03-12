package com.fognl.dronekitbridge.locationrelay;

import android.content.Context;
import android.location.Location;
import android.util.Log;
import android.util.Pair;

import com.fognl.dronekitbridge.R;
import com.fognl.dronekitbridge.web.LocationRelayService;
import com.fognl.dronekitbridge.web.ServerResponse;
import com.fognl.dronekitbridge.web.UserLocation;
import com.fognl.dronekitbridge.web.UserLocationPostBody;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
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

    public interface WsMessageCallback {
        void onUserDeleted(String user);
        void onUserLocation(String user, UserLocation location);
        void onError(Throwable error);
    }

    private static LocationRelayService sService;

    static String getJsonString(String type, String group, String user) {
        try {
            JSONObject jo = new JSONObject();
            jo.put("type", type);
            jo.put("groupId", group);
            if(user != null) {
                jo.put("userId", user);
            }

            return jo.toString();
        }
        catch(JSONException ex) {
            return "";
        }
    }

    public static String getSubscribeToString(String group, String user) {
        return getJsonString("subscribe", group, user);
    }

    public static String getSubscribeToString(String group) {
        return getSubscribeToString(group, null);
    }

    public static String getUnsubscribeString(String group, String user) {
        return getJsonString("unsubscribe", group, user);
    }

    public static void handleIncomingWsObject(JSONObject jo, WsMessageCallback cb) {
        try {
            String type = jo.getString("type");
            switch(type) {
                case "location": {
                    Pair<String, UserLocation> pair = toUserLocation(jo);
                    cb.onUserLocation(pair.first, pair.second);
                    break;
                }

                case "delete": {
                    cb.onUserDeleted(jo.getString("userId"));
                    break;
                }
            }
        }
        catch(JSONException ex) {
            cb.onError(ex);
        }
    }

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
                    callback.onFailure(call, new Exception("Server is down"));
                }
            }

            @Override
            public void onFailure(Call<List<String>> call, Throwable t) {
                callback.onFailure(call, t);
            }
        });
    }

    static Pair<String, UserLocation> toUserLocation(JSONObject jo) {
        try {
            String user = jo.getString("userId");
            JSONObject joLoc = jo.getJSONObject("location");
            return new Pair<String, UserLocation>(user, UserLocation.populate(new UserLocation(), joLoc));
        }
        catch(JSONException ex) {
            Log.e(TAG, ex.getMessage(), ex);
        }

        return null;
    }

    public static void retrieveGroupLocations(Context context, String groupId,
                                              final RelayCallback<Map<String, UserLocation>> callback) {

        final LocationRelayService service = getLocationRelayService(context);

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
                        callback.error(new Exception("Server is down"));
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
                    callback.onFailure(call, new Exception("Server is down"));
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

    public static void getPublicIpAddress(Context context, final RelayCallback<String> callback) {
        LocationRelayService service = getLocationRelayService(context);
        final Call<ResponseBody> call = service.retrieveMyIp();

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                ResponseBody body = response.body();
                if(body != null) {
                    try {
                        callback.complete(body.string());
                    }
                    catch(IOException ex) {
                        callback.error(ex);
                    }
                }
                else {
                    callback.error(new Exception("Server is down"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {

            }
        });
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
