package com.fognl.dronekitbridge.locationrelay;

import android.content.Context;
import android.location.Location;

import com.fognl.dronekitbridge.R;
import com.fognl.dronekitbridge.web.LocationRelayService;
import com.fognl.dronekitbridge.web.ServerResponse;
import com.fognl.dronekitbridge.web.UserLocation;
import com.fognl.dronekitbridge.web.UserLocationPostBody;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by kellys on 3/4/16.
 */
public class LocationRelayManager {
    static final String TAG = LocationRelayManager.class.getSimpleName();

    private static LocationRelayService sService;

    public static void sendLocation(
            Context context, String groupId, String userId, Location location, Callback<ServerResponse> callback) {
        final UserLocation loc = UserLocation.populate(new UserLocation(), location);

        final LocationRelayService service = getLocationRelayService(context);
        final UserLocationPostBody body = new UserLocationPostBody(groupId, userId, loc);

        Call<ServerResponse> call = service.sendLocation(body);
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
