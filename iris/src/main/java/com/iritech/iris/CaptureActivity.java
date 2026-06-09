package com.iritech.iris;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.view.WindowManager;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.iritech.mqel704.ImageData;

public class CaptureActivity extends FragmentActivity {

    /**
     * Log tag
     */
    private static final String TAG = "CaptureActivity";

    /**
     * UserID
     */
    private String mUserID;

    /**
     * CapturePurpose
     */
    private CapturePurpose mCapturePurpose = CapturePurpose.NONE;

    protected static Activity mUSBActivity = null;

    protected static boolean mIsSupportUVC = true;

    public static void setUSBActivity(Activity activity) {
        mUSBActivity = activity;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load layout tùy theo việc có hỗ trợ UVC hay không
        if (mIsSupportUVC) {
            setContentView(R.layout.capture_view_uvc);
        } else {
            setContentView(R.layout.capture_view);
        }


        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE){
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int height = displayMetrics.heightPixels;
            int width = displayMetrics.widthPixels;

            WindowManager.LayoutParams params = getWindow().getAttributes();

            float scale = (1.0f * Settings.getCameraPreviewScale(this)) / 100;
            params.height = (int)(height * scale);
            params.width = (int)(width * scale);
            this.getWindow().setAttributes(params);
        }

        // avoid screen blink at first time start (blink caused by surface re-created)
        getWindow().setFormat(PixelFormat.TRANSLUCENT);

        Intent intent = getIntent();
        String action = intent.getAction();

        mUserID = intent.getStringExtra(Constants.EXTRA_USER_ID);

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        CaptureFragment fragment = CaptureFragment.newInstance(action, mUserID);
        fragmentTransaction.replace(android.R.id.content, fragment);
        fragmentTransaction.commit();

        ImageView btnStop = findViewById(R.id.btnStop);
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!IriController.getInstance().isInitialized())
                    return;

                IriController.getInstance().stopCapture();

                if (isFinishing())
                    return;

                onBackPressed();
            }
        });
    }

    public static int getResultImages(ImageData leftImage, ImageData rightImage, ImageData unknownImage) {
        return IriController.getInstance().getResultImages(leftImage, rightImage, unknownImage);
    }
}
