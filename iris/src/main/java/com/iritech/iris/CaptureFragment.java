package com.iritech.iris;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.iritech.mirisreg704.MatchResult;
import com.iritech.mqel704.GemResult;
import com.iritech.util.MutableBoolean;
import java.util.ArrayList;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class CaptureFragment extends Fragment implements IriController.OnCaptureCallback {

    /**
     * Log tag
     */
    private static final String TAG = "CaptureFragment";

    /**
     * CapturePurpose
     */
    private CapturePurpose mCapturePurpose = CapturePurpose.NONE;
    private ActionType mActionType = ActionType.CAPTURE;
    private String mUserId;

    boolean isWaitingInit = false;
    private FrameLayout mCameraLayout;

    public static CaptureFragment newInstance(String action, String mUserID) {

        CaptureFragment captureFragment = new CaptureFragment();

        Bundle args = new Bundle();
        args.putString(Constants.EXTRA_USER_ID, mUserID);
        args.putString(Constants.EXTRA_ACTION, action);
        captureFragment.setArguments(args);

        return captureFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUserId = getArguments().getString(Constants.EXTRA_USER_ID);
        String action = getArguments().getString(Constants.EXTRA_ACTION);
        if(action != null) {
            if (Constants.ACTION_ENROLL.compareTo(action) == 0) {
                mActionType = ActionType.ENROLL;
            }
            else if (Constants.ACTION_VERIFY.compareTo(action) == 0) {
                mActionType = ActionType.VERIFY;
            }
            else if (Constants.ACTION_UNENROLL.compareTo(action) == 0) {
                mActionType = ActionType.UNENROLL;
            }
            else if (Constants.ACTION_IDENTIFY.compareTo(action) == 0) {
                mActionType = ActionType.IDENTIFY;
            }
            else if (Constants.ACTION_CAPTURE.compareTo(action) == 0){
                mActionType = ActionType.CAPTURE;
                mCapturePurpose = CapturePurpose.VERIFY;
            }
            else {
                finish(1, "Not supported function");
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if (CaptureActivity.mIsSupportUVC) {
            View view = inflater.inflate(R.layout.fragment_capture_uvc, container, false);

            mCameraLayout = view.findViewById(R.id.captureview_layout);
            return view;
        } else {
            View view = inflater.inflate(R.layout.fragment_capture, container, false);

            mCameraLayout = view.findViewById(R.id.captureview_layout);
            return view;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        initAndStartCapture();

    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    public void onStop() {
        super.onStop();
        IriController.getInstance().stopCapture();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (CaptureActivity.mIsSupportUVC) {
            IriController.getInstance().releaseCamera();
        }
        //IriController.getInstance().stopCapture();
    }

    private void initAndStartCapture() {
        if (!IriController.getInstance().isInitialized() && !IriController.getInstance().isInitializing()) {
            IriController.getInstance().initialize(getActivity(), new IriController.IriControllerListener() {
                @Override
                public void onIriControllerInitializedFinished() {
                    if (getActivity() == null)
                        return;
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            IriController.getInstance().setLayout(mCameraLayout);
                            if (mActionType == ActionType.CAPTURE) {
                                IriController.getInstance().startCapture(mCapturePurpose, CaptureFragment.this);
                            } else if (mActionType == ActionType.ENROLL) {
                                IriController.getInstance().enroll(mUserId, CaptureFragment.this);
                            } else if (mActionType == ActionType.VERIFY) {
                                IriController.getInstance().verify(mUserId, CaptureFragment.this);
                            } else if (mActionType == ActionType.UNENROLL) {
                                IriController.getInstance().unenroll(mUserId, CaptureFragment.this);
                            } else if (mActionType == ActionType.IDENTIFY) {
                                IriController.getInstance().identify(CaptureFragment.this);
                            }

                        }
                    });
                }
            });
        } else if (IriController.getInstance().isInitialized()) {
            IriController.getInstance().setLayout(mCameraLayout);
            IriController.getInstance().setFlipImages(Settings.getCameraFlipImages(getContext()));

            if (mActionType == ActionType.CAPTURE) {
                IriController.getInstance().startCapture(mCapturePurpose, CaptureFragment.this);
            } else if (mActionType == ActionType.ENROLL) {
                IriController.getInstance().enroll(mUserId, CaptureFragment.this);
            } else if (mActionType == ActionType.VERIFY) {
                IriController.getInstance().verify(mUserId, CaptureFragment.this);
            } else if (mActionType == ActionType.UNENROLL) {
                IriController.getInstance().unenroll(mUserId, CaptureFragment.this);
            } else if (mActionType == ActionType.IDENTIFY) {
                IriController.getInstance().identify(CaptureFragment.this);
            }
        }
    }

    ///////////////// OnCaptureCallback
    @Override
    public void onTemplateCreated(byte[] templateLeft, byte[] templateRight, byte[] templateUnknown) {

        finish(0, "Capture successfully", templateLeft, templateRight, templateUnknown);
    }

    @Override
    public void onFailed(int error, String msg) {
        finish(error, msg);
    }

    @Override
    public void onVerifyFinished(MutableBoolean matchingResult) {
        finish(0, "", matchingResult);
    }

    @Override
    public void onIdentifyFinished(ArrayList<MatchResult> results) {
        finish(0, "", results);
    }
    //////////////////

    public void finish(int resultCode, String errMsg) {
        Log.d(TAG, "finish>> code = " + resultCode + ", msg = " + errMsg);

        Intent returnIntent = new Intent();
        returnIntent.putExtra(Constants.EXTRA_RESULT_CODE, resultCode);
        GemResult result = new GemResult(resultCode);
        returnIntent.putExtra(Constants.EXTRA_RESULT_MSG, result.toString());
        getActivity().setResult(Activity.RESULT_OK, returnIntent);
        getActivity().finish();
    }

    private void finish(int resultCode, String errMsg, byte[] templateLeft, byte[] templateRight, byte[] templateUnknown) {

        Intent returnIntent = new Intent();
        returnIntent.putExtra(Constants.EXTRA_RESULT_CODE, resultCode);
        GemResult result = new GemResult(resultCode);
        returnIntent.putExtra(Constants.EXTRA_RESULT_MSG, result.toString());
        if (templateLeft != null) {
            returnIntent.putExtra(Constants.EXTRA_LEFT_TEMPLATE, templateLeft);
        }
        if (templateRight != null) {
            returnIntent.putExtra(Constants.EXTRA_RIGHT_TEMPLATE, templateRight);
        }
        if (templateUnknown != null) {
            returnIntent.putExtra(Constants.EXTRA_UNKNOWN_TEMPLATE, templateUnknown);
        }
        getActivity().setResult(Activity.RESULT_OK, returnIntent);
        getActivity().finish();
    }

    public void finish(int resultCode, String errMsg, MutableBoolean matchingResult) {
        Log.d(TAG, "finish>> code = " + resultCode + ", msg = " + errMsg);

        Intent returnIntent = new Intent();
        returnIntent.putExtra(Constants.EXTRA_RESULT_CODE, resultCode);
        GemResult result = new GemResult(resultCode);
        returnIntent.putExtra(Constants.EXTRA_RESULT_MSG, result.toString());
        returnIntent.putExtra(Constants.EXTRA_MATCHING_RESULT, matchingResult.value);
        getActivity().setResult(Activity.RESULT_OK, returnIntent);
        getActivity().finish();
    }

    public void finish(int resultCode, String errMsg, ArrayList<MatchResult> results) {
        Log.d(TAG, "finish>> code = " + resultCode + ", msg = " + errMsg);

        Intent returnIntent = new Intent();
        returnIntent.putExtra(Constants.EXTRA_RESULT_CODE, resultCode);
        GemResult result = new GemResult(resultCode);
        returnIntent.putExtra(Constants.EXTRA_RESULT_MSG, result.toString());

        int resultCount = results.size();
        returnIntent.putExtra(Constants.EXTRA_MATCHING_COUNT, resultCount);

        String resultItem = "";
        for (int i = 0; i < resultCount; i++) {
            resultItem += (i > 0 ? "; " : "") + "ID: " + results.get(i).getId() +
                    ", distance: " + results.get(i).getDistance();
        }

        returnIntent.putExtra("EXTRA_MATCHING_ITEMS", resultItem);

        getActivity().setResult(Activity.RESULT_OK, returnIntent);
        getActivity().finish();
    }
}
