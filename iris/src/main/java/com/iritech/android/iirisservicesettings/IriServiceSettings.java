package com.iritech.android.iirisservicesettings;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by VuNH on 5/25/2018.
 * Store & Read Settings
 */
public class IriServiceSettings {

    /**
     * Log tag
     */
    private static final String TAG = "IriServiceSettings";

    /**
     * JsonString to Set
     * @param json json
     * @return a set of string
     */
    private static Set<String> jsonToSet(final String json){
        Set<String> result = new HashSet<>();
        try {
            JSONArray jsonArray = new JSONArray(json);
            for(int i = 0; i < jsonArray.length(); ++i){
                result.add(jsonArray.getString(i));
            }
        }
        catch (JSONException ex){
            ex.printStackTrace();
        }
        return result;
    }

    /**
     *  Get Initialized Flags
     * @param context context
     * @return A set of String
     */
    public static Set<String> getInitFlags(Context context){

        // If cached the return the cached Map
        Set<String> result = null;
        SharedPreferences sharedPreferences = context.getSharedPreferences(IriConstants.IRI_SETTINGS_REF_NAME, Context.MODE_PRIVATE);
        String json = sharedPreferences.getString(IriConstants.INITIALIZED_FLAGS, null);
        if(json != null) {
            result = jsonToSet(json);
        }
        if(result == null) {
            result = new HashSet<>();
        }
        return result;
    }

    /**
     * SaveInitFlags
     * @param context context
     */
    private static void submitInitMap(Set<String> initFlags, Context context){
        Gson gson = new Gson();
        String json = gson.toJson(initFlags);
        SharedPreferences sharedPreferences = context.getSharedPreferences(IriConstants.IRI_SETTINGS_REF_NAME, Context.MODE_PRIVATE);
        sharedPreferences.edit().putString(IriConstants.INITIALIZED_FLAGS, json).apply();
    }

    public static boolean getForceActiveLicense(Context context){
        SharedPreferences sharedPreferences = context.getSharedPreferences(IriConstants.IRI_SETTINGS_REF_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(IriConstants.FORCE_ACTIVE_LICENSE, false);
    }

    public static void setForceActiveLicense(Context context, boolean value){
        SharedPreferences sharedPreferences = context.getSharedPreferences(IriConstants.IRI_SETTINGS_REF_NAME, Context.MODE_PRIVATE);
        sharedPreferences.edit().putBoolean(IriConstants.FORCE_ACTIVE_LICENSE, value).apply();
    }
}
