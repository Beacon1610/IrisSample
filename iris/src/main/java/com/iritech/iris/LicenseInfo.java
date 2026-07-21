package com.iritech.iris;

import android.content.Context;

import com.iritech.android.licenseactivator.LicenseActivator;
import com.iritech.android.licenseactivator.LicenseActivatorException;

public class LicenseInfo {
    private boolean mIsInitialized;
    private boolean mIsLicenseExist;
    private String mCustomerID;
    private String mLicenseID;

    private static LicenseInfo mInstance = null;
    public static LicenseInfo getInstance(){
        if(mInstance == null){
            mInstance = new LicenseInfo();
        }
        return mInstance;
    }

    public boolean isInitialized(){
        return mIsInitialized;
    }

    public void initialize(Context context){
        mIsLicenseExist = Settings.getLicenseEntered(context);
        if (mIsLicenseExist){
            mCustomerID = Settings.getCustomerID(context);
            mLicenseID = Settings.getLicenseID(context);
        }

        mIsInitialized = true;
    }

    private LicenseInfo() {
    }

    public void setLicenseInfo(Context context, String customerID, String licenseID){
        Settings.setCustomerID(context, customerID);
        Settings.setLicenseID(context,  licenseID);
        Settings.setLicenseEntered(context, true);

        mCustomerID = customerID;
        mLicenseID = licenseID;
        mIsLicenseExist = true;
    }

    public boolean isLicenseExisted()
    {
        return mIsLicenseExist;
    }

    public String getCustomerID()
    {
        return mCustomerID;
    }

    public String getLicenseID()
    {
        return mLicenseID;
    }
}
