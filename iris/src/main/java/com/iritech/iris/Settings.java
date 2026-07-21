package com.iritech.iris;

import android.content.Context;
import android.content.SharedPreferences;

public class Settings {
    // Keys
    private static String CUR_DEFAULT_PREFS = "cur_default_prefs";
    private static String CAM_PREVIEW_SCALE = "cam_preview_scale";

    private static String CAM_FLIP_IMAGES = "cam_flip_images";
    private static String IS_LIC_ENTERED = "is_lic_entered";
    private static String LIC_CUSTOMER_ID = "lic_customer_id";
    private static String LIC_LICENSE_ID = "lic_license_id";
    private static String NEED_REQ_NEW_LICENSE = "need_req_new_license";

    // Camera preview scle
    public static void setCameraPreviewScale(Context context, int camPreviewScale) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(CUR_DEFAULT_PREFS, Context.MODE_PRIVATE);
        sharedPreferences.edit().putInt(CAM_PREVIEW_SCALE, camPreviewScale).apply();
    }
    public static int getCameraPreviewScale(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(CUR_DEFAULT_PREFS, Context.MODE_PRIVATE);
        int value = sharedPreferences.getInt(CAM_PREVIEW_SCALE, 100);
        return value;
    }

    public static void setCameraFlipImages(Context context, boolean camFlipImages) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(CUR_DEFAULT_PREFS, Context.MODE_PRIVATE);
        sharedPreferences.edit().putBoolean(CAM_FLIP_IMAGES, camFlipImages).apply();
    }
    public static boolean getCameraFlipImages(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(CUR_DEFAULT_PREFS, Context.MODE_PRIVATE);
        boolean value = sharedPreferences.getBoolean(CAM_FLIP_IMAGES, true);
        return value;
    }

    // License Information
    // Check is license entered
    public static void setLicenseEntered(Context context, boolean isEntered) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(CUR_DEFAULT_PREFS, Context.MODE_PRIVATE);
        sharedPreferences.edit().putBoolean(IS_LIC_ENTERED, isEntered).apply();
    }
    public static boolean getLicenseEntered(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(CUR_DEFAULT_PREFS, Context.MODE_PRIVATE);
        boolean value = sharedPreferences.getBoolean(IS_LIC_ENTERED, false);
        return value;
    }

    // CustomerID
    public static void setCustomerID(Context context, String customerID) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(CUR_DEFAULT_PREFS, Context.MODE_PRIVATE);
        sharedPreferences.edit().putString(LIC_CUSTOMER_ID, customerID).apply();
    }
    public static String getCustomerID(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(CUR_DEFAULT_PREFS, Context.MODE_PRIVATE);
        String value = sharedPreferences.getString(LIC_CUSTOMER_ID, "");
        return value;
    }

    // LicenseID
    public static void setLicenseID(Context context, String licenseID) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(CUR_DEFAULT_PREFS, Context.MODE_PRIVATE);
        sharedPreferences.edit().putString(LIC_LICENSE_ID, licenseID).apply();
    }
    public static String getLicenseID(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(CUR_DEFAULT_PREFS, Context.MODE_PRIVATE);
        String value = sharedPreferences.getString(LIC_LICENSE_ID, "");
        return value;
    }

    // Check if need to request new license after license changed
    public static void setRequestNewLicense(Context context, boolean isEntered) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(CUR_DEFAULT_PREFS, Context.MODE_PRIVATE);
        sharedPreferences.edit().putBoolean(NEED_REQ_NEW_LICENSE, isEntered).apply();
    }
    public static boolean isNeedRequestNewLicense(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(CUR_DEFAULT_PREFS, Context.MODE_PRIVATE);
        boolean value = sharedPreferences.getBoolean(NEED_REQ_NEW_LICENSE, false);
        return value;
    }
}
