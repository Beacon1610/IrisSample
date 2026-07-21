package com.iritech.iris;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.iritech.android.iirisservicesettings.IriServiceSettings;
import com.iritech.android.licenseactivator.LicenseActivator;
import com.iritech.android.licenseactivator.LicenseActivatorException;
import com.iritech.android.widget.circleprogress.ArcProgress;
import com.iritech.irienvoy.IriEnvoyConnectListener;
import com.iritech.iris.DeveloperSettings;
import com.iritech.util.DataBuffer;
import com.iritech.mirisreg704.IrisReg;
import com.iritech.mirisreg704.MatchResult;
import com.iritech.mqel704.CaptureStatus;
import com.iritech.mqel704.GemResult;
import com.iritech.mqel704.ILicenseFailCallBack;
import com.iritech.mqel704.ImageData;
import com.iritech.mqel704.Indication;
import com.iritech.mqel704.IrisCapture;
import com.iritech.util.MutableBoolean;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.io.File;
import static com.iritech.iris.CapturePurpose.ENROLL;
import static com.iritech.iris.CapturePurpose.VERIFY;

/**
 * Created by VuNH on 5/16/2018.
 * IriController
 */

public class IriController implements IrisCapture.OnCaptureListener, IriEnvoyConnectListener {

    @Override
    public void onAttach() {
        MediaData.getInstance().play(mContext.get(), R.raw.device_detected);
    }

    @Override
    public void onDetach() {
        MediaData.getInstance().play(mContext.get(), R.raw.device_disconnected);
    }

    @Override
    public void onConnect() {
        System.out.println("onConnect");
    }

    @Override
    public void onDisconnect() {

    }

    @Override
    public void onCancel() {

    }

    public interface IriControllerListener {
        public void onIriControllerInitializedFinished();
    }

    public interface OnCaptureCallback {
        public void onTemplateCreated(byte[] templateLeft, byte[] templateRight,  byte[] templateUnknown);
        public void onFailed(int error, String msg);
        public void onVerifyFinished(MutableBoolean matchingResult);
        public void onIdentifyFinished(ArrayList<MatchResult> results);
    }

    /**
     * Log tag
     */
    private static final String TAG = "IriController";

    /**
     * Static instance
     */
    private static IriController mInstance = null;

    private View mView;

    /**
     * IrisCamera instance
     */
    private CustomIrisCamera mIrisCamera = null;

    /**
     * IrisCapture instance
     */
    private IrisCapture mIrisCapture = null;

    /**
     * IrisReg instance
     */
    private IrisReg mIrisReg = null;

    /**
     * For checking if activate License done
     */
    volatile private boolean mIsActivateLSDone = false;

    /**
     * For storing LicenseActivation Error code
     */
    private int mActLSErrorCode = LicenseActivator.ERR_NONE;

    /**
     * For checking if IriController was Initialized
     */
    private boolean mIsInitialized = false;

    /**
     * For checking if IriController is Initializing resources
     */
    private volatile boolean mIsInitializing = false;

    private IriControllerListener mListener;
    private OnCaptureCallback mCaptureCallback;

    /**
     * Week reference to current mContext
     */
    private WeakReference<Context> mContext;

    /**
     * Message codes
     */
    private static final int MSG_ENROLL_PERCENTAGE = 5;
    private static final int MSG_CAPTURE_STATUS = 6;
    private static final int MSG_INDICATION = 16;

    /**
     * Colors
     */
    private static final int RED = 0;
    private static final int ANIMATION = 1;
    private static final int GREEN = 2;
    private static final int GRAY = 3;
    private static final int INVISIBLE = 4;

    /**
     * CapturePurpose
     */
    private CapturePurpose mCapturePurpose = CapturePurpose.NONE;

    private ActionType mActionType = ActionType.CAPTURE;

    private String mEnrolleeId;

    private boolean isEnrollAction = false;

    /**
     * IsShowIndicator
     */
    private volatile boolean mIsShowIndicator = true;

    /**
     * For checking if stop capture
     */
    private volatile boolean mIsStoppedCapture = false;

    private String mCustomerId;
    private String mLicenseId;
    private String mLicPath = "/sdcard/iritech/license";

    private static Thread mInitThread = null;

    private boolean isBinocular = true;
    /**
     * A private constructor
     */
    private IriController(){
        mCustomerId = LicenseInfo.getInstance().getCustomerID();
        mLicenseId = LicenseInfo.getInstance().getLicenseID();
        if (Build.BRAND.toLowerCase().compareTo("infocus") == 0){
            isBinocular = false;
        }
    }

    /**
     * Get Instance
     * @return Instance of IriController
     */
    public static IriController getInstance(){
        if(mInstance == null){
            mInstance = new IriController();
        }
        return mInstance;
    }

    /**
     * mIsCapturing
     */
    private volatile int mCaptureStatus;

    /**
     * SettingMap
     */
    private Map<String, Object> mSettingMap = new HashMap<>();

    /**
     * Initialize CameraManager for new session
     * @param context mContext
     */
    public synchronized void initialize(final Context context, IriControllerListener listener){

        if (mInitThread == null) {
            mIsInitializing = true;
            mListener = listener;
            this.mContext = new WeakReference<>(context);
            mInitThread = new Thread(()-> {

                try {
                    // Check & active License
                    boolean isForceActive = IriServiceSettings.getForceActiveLicense(context);
                    //activateLicense(isForceActive);

                    // Check if need to request new license after license changed
                    boolean isNeedReqNewLicense = Settings.isNeedRequestNewLicense(context);
                    if (isNeedReqNewLicense){
                        File path = new File(mLicPath + "/_lsa_");
                        if( path.exists() ) {
                            File[] files = path.listFiles();
                            for(int i=0; i<files.length; i++) {
                                if (files[i].exists()){
                                    files[i].delete();
                                }
                            }
                        }
                        Settings.setRequestNewLicense(context, false);
                    }

                    // New IrisCapture, IrisReg instances & Init native
                    // Pass isForceActive to determine license file place

                    mIrisCapture = IrisCapture.getInstance(context, new ILicenseFailCallBack() {
                        @Override
                        public void onLicenseFail(){
                            Log.d(TAG, "onLicenseFail called");

                            // Set a flag to sharedReference to force active for next time starting service
                            Context context = mContext.get();
                            if(context != null) {
                                IriServiceSettings.setForceActiveLicense(context, true);
                            }
                            //activateLicense(true);
                        }
                    }, mCustomerId, mLicenseId, mLicPath);

                    mIrisCapture.setIriEnvoyConnectListener(this);

                    mIrisCapture.setBinocular(isBinocular);

                    mIrisReg = IrisReg.getInstance();
                    String[] arr = mContext.get().getPackageName().split(Pattern.quote("."));
                    String savePath = Constants.DEFAULT_SAVE_PATH + arr[arr.length -1];
                    mIrisReg.getIRegSetting().loadGallery(savePath + "/iritechdb.repo");
                    // New IrisCamera
                    mIrisCamera = new CustomIrisCamera(context.getApplicationContext());

                    // Attach IrisCapture to irisReg
                    mIrisReg.attach(mIrisCapture);

                    // if license does not exist then use requestLicenseServer to download license via the Internet
                    LicenseActivator activator = LicenseActivator.produceActivator();
                    if (!activator.isActLicense(context, mCustomerId, mLicenseId, mLicPath)) {
                        activator.requestLicenseServer(context, mLicPath, mCustomerId, mLicenseId);
                        mIrisCapture.reloadLicense(context);
                        mIrisReg.reloadLicense();
                    }

                    mActLSErrorCode = activator.getActLSErrorCode();
                    if (mActLSErrorCode != LicenseActivator.ERR_NONE && !isNetworkAvailable(context)) {
                        Thread threadCheckInternetAndLicense = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                while (true){
                                    if (isNetworkAvailable(context)){
                                        mIsInitializing = true;
                                        try {
                                            // if license does not exist then use requestLicenseServer to download license via the Internet
                                            if (!activator.isActLicense(context, mCustomerId, mLicenseId, mLicPath)) {
                                                activator.requestLicenseServer(context, mLicPath, mCustomerId, mLicenseId);
                                                mIrisCapture.reloadLicense(context);
                                                mIrisReg.reloadLicense();
                                            }
                                        } catch (LicenseActivatorException ex){
                                            ex.printStackTrace();
                                        }
                                        mIsInitializing = false;
                                        break;
                                    }
                                    try {
                                        Thread.sleep(2000);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        });
                        threadCheckInternetAndLicense.start();
                    }

                } catch (UnsatisfiedLinkError ex) {
                    ex.printStackTrace();
                } catch (LicenseActivatorException ex) {
                    ex.printStackTrace();
                }
                mIsInitialized = true;
                mIsInitializing = false;

                mListener.onIriControllerInitializedFinished();
            });
            mInitThread.start();
        }
    }

    public void setLayout(FrameLayout layoutContainer) {
        if (layoutContainer != null) {
            Context context = layoutContainer.getContext();
            if (true) {
                mView = LayoutInflater.from(context).inflate(R.layout.capture_view_uvc, null);
            } else {
                mView = LayoutInflater.from(context).inflate(R.layout.capture_view, null);
            }

            //Resize stream layout
            /*DisplayMetrics displaymetrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            windowManager.getDefaultDisplay().getMetrics(displaymetrics);
            int screenWidth = displaymetrics.widthPixels;
            int layoutHeight = (int) (screenWidth / 1.8f);
            RelativeLayout captureViewLayout = (RelativeLayout) mView.findViewById(R.id.captureview_layout_id);
            LinearLayout.LayoutParams rel_btn = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, layoutHeight);
            captureViewLayout.setLayoutParams(rel_btn);*/

            ImageView btnStop = (ImageView) mView.findViewById(R.id.btnStop);
            btnStop.setTag(RED);

            layoutContainer.post(new Runnable() {
                @Override
                public void run() {
                    layoutContainer.addView(mView);
                }
            });
        }
    }

    /**
     *
     * @param capturePurpose
     * @return
     */
    public synchronized void startCapture(CapturePurpose capturePurpose, OnCaptureCallback captureCallback) {
        mCaptureCallback = captureCallback;
        if (!isEnrollAction) {
            mActionType = ActionType.CAPTURE;
        }
        isEnrollAction = false;
        
        // Mock Mode: Bypass nhận diện mống mắt khi đang dev
        DeveloperSettings devSettings = new DeveloperSettings(mContext.get());
        if (devSettings.isMockModeEnabled()) {
            // Fake delay để giống thật
            new Thread(() -> {
                try {
                    Thread.sleep(1500); // 1.5s fake processing
                    byte[] fakeTemplate = devSettings.generateMockTemplate();
                    mCaptureCallback.onTemplateCreated(fakeTemplate, fakeTemplate, null);
                } catch (InterruptedException e) {
                    mCaptureCallback.onFailed(-1, "Mock capture interrupted");
                }
            }).start();
            return;
        }

        if (!isInitialized() || isInitializing()) {
            mCaptureCallback.onFailed(GemResult.IDDK_NOT_INITIALIZED, "Not initialized");
        }

//        if (mView == null) {
//            mCaptureCallback.onFailed(GemResult.IDDK_NOT_INITIALIZED, "Layout is not initialized");
//        }

        resetProgress();

        mCapturePurpose = capturePurpose;

        ViewGroup group = (mView == null ? null : (ViewGroup) mView.findViewById(R.id.camerasurfaceview_id));
        int ret = openCamera(mContext.get(), group);
        if(ret != 0){
            mCaptureCallback.onFailed(ret, "Open camera failed");
        }
        registerCaptureListener(this);

        String date = (String) android.text.format.DateFormat.format("_9kkmmss", new java.util.Date());
        final String sessionID = date;

        if(mCapturePurpose == ENROLL) {
            updateEnrollBar();
            ret = startCamera(mContext.get(), sessionID, IrisCapture.CAPTURE_FOR_ENROLLMENT_LIVE);
            if (ret != 0) {// fail
                // Finish activity
                mCaptureCallback.onFailed(ret, "Start camera failed");
            }
        } else if(mCapturePurpose == VERIFY) {

            updateBar();

            //ret = startCamera(getApplicationContext(), sessionID, IrisCapture.CAPTURE_FOR_VERIFYING);
            ret = startCamera(mContext.get(), sessionID, IrisCapture.CAPTURE_FOR_IDENTIFYING);
            if (ret != 0) {// fail
                // Finish activity
                mCaptureCallback.onFailed(ret, "Start camera failed");
            }
        }

        mIsStoppedCapture = false;
    }

    public synchronized void enroll(String enrolleeId,  OnCaptureCallback captureCallback) {
        mEnrolleeId = enrolleeId;
        mActionType = ActionType.ENROLL;
        isEnrollAction = true;
        startCapture(ENROLL, captureCallback);
    }

    public synchronized MutableBoolean verify(String mEnrolleeId, OnCaptureCallback captureCallback) {
        MutableBoolean result = new MutableBoolean(false);
        mCaptureCallback = captureCallback;
        mIsStoppedCapture = false;
        mActionType = ActionType.VERIFY;
        
        // Mock Mode: Luôn verify thành công
        DeveloperSettings devSettings = new DeveloperSettings(mContext.get());
        if (devSettings.isMockModeEnabled()) {
            new Thread(() -> {
                try {
                    Thread.sleep(1500);
                    result.value = true; // Fake match success
                    mCaptureCallback.onVerifyFinished(result);
                } catch (InterruptedException e) {
                    mCaptureCallback.onFailed(-1, "Mock verify interrupted");
                }
            }).start();
            return result;
        }
        
        if (!isInitialized() || isInitializing()) {
            mCaptureCallback.onFailed(GemResult.IDDK_NOT_INITIALIZED, "Not initialized");
        }

//        if (mView == null) {
//            mCaptureCallback.onFailed(GemResult.IDDK_NOT_INITIALIZED, "Layout is not initialized");
//        }

        resetProgress();

        ViewGroup group = (mView == null ? null : (ViewGroup) mView.findViewById(R.id.camerasurfaceview_id));
        int ret = openCamera(mContext.get(), group);
        if(ret != 0){
            mCaptureCallback.onFailed(ret, "Open camera failed");
        }

        registerCaptureListener(this);
        updateBar();

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                int ret = mIrisReg.verifyCapture(mEnrolleeId, result);
                if (ret != 0) {
                    captureCallback.onFailed(ret, "Verify failed");
                } else {
                    captureCallback.onVerifyFinished(result);
                }
            }
        });

        thread.start();

        return result;
    }

    public void unenroll(String mEnrolleeId, OnCaptureCallback captureCallback) {
        int ret = mIrisReg.unenroll(mEnrolleeId);
        if (ret == 0) {
            captureCallback.onFailed(ret, "Unenroll successfully");
        } else {
            captureCallback.onFailed(ret, "Unenroll failed");
        }
    }

    public synchronized void identify(OnCaptureCallback captureCallback) {
        mCaptureCallback = captureCallback;
        mIsStoppedCapture = false;
        mActionType = ActionType.IDENTIFY;
        
        // Mock Mode: Return empty result (không tìm thấy ai)
        DeveloperSettings devSettings = new DeveloperSettings(mContext.get());
        if (devSettings.isMockModeEnabled()) {
            new Thread(() -> {
                try {
                    Thread.sleep(1500);
                    ArrayList<MatchResult> emptyResult = new ArrayList<>();
                    mCaptureCallback.onIdentifyFinished(emptyResult);
                } catch (InterruptedException e) {
                    mCaptureCallback.onFailed(-1, "Mock identify interrupted");
                }
            }).start();
            return;
        }
        
        if (!isInitialized() || isInitializing()) {
            mCaptureCallback.onFailed(GemResult.IDDK_NOT_INITIALIZED, "Not initialized");
        }

//        if (mView == null) {
//            mCaptureCallback.onFailed(GemResult.IDDK_NOT_INITIALIZED, "Layout is not initialized");
//        }

        resetProgress();

        ViewGroup group = (mView == null ? null : (ViewGroup) mView.findViewById(R.id.camerasurfaceview_id));
        int ret = openCamera(mContext.get(), group);
        if(ret != 0){
            mCaptureCallback.onFailed(ret, "Open camera failed");
        }

        registerCaptureListener(this);
        updateBar();


        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<MatchResult> result = new ArrayList<MatchResult>();
                int ret = mIrisReg.identifyCapture(result);
                if (ret != 0) {
                    captureCallback.onFailed(ret, "Identify failed");
                } else {
                    captureCallback.onIdentifyFinished(result);
                }
            }
        });

        thread.start();
    }

    /**
     * Stop camera capture, update status and remove top view
     */
    public synchronized void stopCapture() {
        if (!isInitialized() || isInitializing()) {
            return;
        }
        Log.d(TAG, "stopCapture>>start");

        // Prevent calling stop multiple times
        if (mIsStoppedCapture) {
            Log.d(TAG, "stopCapture>>end without doing anything");
            return;
        }
        mIsStoppedCapture = true;

        boolean isVerifyAborted = false;
        if(mCapturePurpose == CapturePurpose.VERIFY && mCaptureStatus != CaptureStatus.IDDK_COMPLETE){
            isVerifyAborted = true;
        }

        // Close Camera
        closeCamera(mContext.get());

        // DeRegister Capture Listener
        deRegisterCaptureListener();

        // Release Media
        MediaData.getInstance().release();

        // Change CaptureStatus
        if(mCaptureStatus == CaptureStatus.IDDK_COMPLETE && !isVerifyAborted) {
            changeCaptureStatus(mCaptureStatus);
        }
        else{
            changeCaptureStatus(CaptureStatus.IDDK_ABORT);
        }

        mCaptureCallback = null;

        Log.d(TAG, "stopCapture>>end");
    }

    public void releaseCamera() {
        mIrisCapture.releaseCamera();
    }
    private void updateBar()
    {
        if (mView == null)
            return;

        LinearLayout guideBar = (LinearLayout) mView.findViewById(R.id.guide_bar);
        LinearLayout guideBarMono = (LinearLayout) mView.findViewById(R.id.guide_bar_mono);
        LinearLayout progressBar = (LinearLayout) mView.findViewById(R.id.progress_bar);
        LinearLayout progressBarMono = (LinearLayout) mView.findViewById(R.id.progress_bar_mono);

        progressBar.setVisibility(View.GONE);
        progressBarMono.setVisibility(View.GONE);

        if (!isBinocular) {
            guideBar.setVisibility(View.GONE);
            guideBarMono.setVisibility(View.VISIBLE);
        } else {
            guideBar.setVisibility(View.VISIBLE);
            guideBarMono.setVisibility(View.GONE);
        }
    }

    private void updateEnrollBar()
    {
        if (mView == null)
            return;

        LinearLayout guideBar = (LinearLayout) mView.findViewById(R.id.guide_bar);
        LinearLayout guideBarMono = (LinearLayout) mView.findViewById(R.id.guide_bar_mono);
        LinearLayout progressBar = (LinearLayout) mView.findViewById(R.id.progress_bar);
        LinearLayout progressBarMono = (LinearLayout) mView.findViewById(R.id.progress_bar_mono);

        guideBar.setVisibility(View.GONE);
        guideBarMono.setVisibility(View.GONE);

        if (!isBinocular)
        {
            progressBar.setVisibility(View.GONE);
            progressBarMono.setVisibility(View.VISIBLE);
        }
        else
        {
            progressBar.setVisibility(View.VISIBLE);
            progressBarMono.setVisibility(View.GONE);
        }
    }
    /**
     * Change CaptureStatus
     * @param status status
     */
    private void changeCaptureStatus(int status){
        mCaptureStatus = status;
    }
    /**
     * Register a CaptureListener
     * @param listener CaptureListener
     */
    private void registerCaptureListener(IrisCapture.OnCaptureListener listener){
        // Set CaptureListener
        mIrisCapture.setCaptureListener(listener);
    }

    private void deRegisterCaptureListener(){
        mIrisCapture.setCaptureListener(null);
    }

    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = null;
        if (connectivityManager != null) {
            activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        }
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    /**
     * IsInitialized
     * @return Was be already Initialized or not
     */
    public boolean isInitialized(){
        return mIsInitialized;
    }

    /**
     * IsInitializing
     * @return true if IriController is initializing
     */
    public boolean isInitializing(){
        return mIsInitializing;
    }

    //++++++++++++++++++++++++++++++++++++ IrisCapture place ++++++++++++++++++++++++++++++++++++
    /**
     * Start IrisCamera
     * @param context mContext
     * @param sessionId sessionId
     * @param capturePurpose capturePurpose
     * @return GemResult
     */
    private int startCamera(final Context context, String sessionId, int capturePurpose) {
        // Turn on IR
        //IRControl.turnOnIR(activity.getApplicationContext());
        //mCaptureStatus = CaptureStatus.IN_PROGRESS;
        int iRet = mIrisCapture.startCapture(capturePurpose, sessionId);
        if (iRet != GemResult.IDDK_OK) {
            stopCamera(context);
        }
        return iRet;
    }

    /**
     * Stop IrisCamera
     */
    private void stopCamera(final Context context) {
        // Turn of IR
        //IRControl.turnOffIR(context);

        // Call stopCapture
        mIrisCapture.stopCapture();
    }

    /**
     * Open IrisCamera
     * @param context mContext
     * @param group group
     * @return GemResult
     */
    private int openCamera(Context context, ViewGroup group) {
        try {
           /* int ret = mIrisCapture.setCamera(mIrisCamera);
            if (ret != GemResult.IDDK_OK) {
                return ret;
            }*/

            // Open camera
            int ret = mIrisCamera.open(-1);

            do{
                if (ret != GemResult.IDDK_OK) {
                    break;
                }
                mIrisCapture.updateUSBMonitor(CaptureActivity.mUSBActivity);
                ret = mIrisCapture.setCamera(mIrisCamera);
                if (ret != GemResult.IDDK_OK) {
                    break;
                }
                ret = mIrisCapture.setPreviewLayout(group);
            }
            while (false);
            return ret;
        } catch (RuntimeException ex) {
            return GemResult.IDDK_SENSOR_OPEN_FAILED;
        }
    }

    /**
     * Close IrisCamera
     */
    private void closeCamera(final Context context) {
        stopCamera(context);
        mIrisCamera.close();
    }

    public int getResultImages(ImageData leftImage, ImageData rightImage, ImageData unknownImage) {
        if (leftImage == null || rightImage == null || unknownImage == null) {
            return GemResult.IDDK_INVALID_PARAM;
        }
        int ret = GemResult.IDDK_OK;
        if (mIrisCapture != null) {
            ret = mIrisCapture.getResultImages(leftImage, rightImage, unknownImage);
        }
        return ret;
    }

    //+++++++++++++++++++++++++++++++++++ End IrisCapture place +++++++++++++++++++++++++++++++++

    //----------------------------------- IrisReg place -----------------------------------------
    /**
     * Get IrisReg object
     * @return IrisReg object
     */
    IrisReg getIrisReg(){
        return mIrisReg;
    }

    public int getResultTemplates(DataBuffer left, DataBuffer right, DataBuffer unknown) {
        Log.d(TAG, "getResultTemplates begin ");
        if (left == null || right == null || unknown == null) {
            return GemResult.IDDK_INVALID_PARAM;
        }
        int ret = GemResult.IDDK_OK;
        if (mIrisReg != null) {
            ret = mIrisReg.getResultTemplates(left, right, unknown);
            Log.d(TAG, "getResultTemplates return " + left.getDataSize() + " right size = "+ right.getDataSize() + " unknown size = "+ unknown.getDataSize());
        }
        return ret;
    }
    //---------------------------------- End IrisReg place --------------------------------------

    ////////////////////// IrisCapture.OnCaptureListener ////////////////////
    @Override
    public int onCaptureNotify(int msgCode, int info) {
        Log.d(TAG, "onCaptureNotify>> msg = " + msgCode + " info = " + info);

        Log.d(TAG, "onCaptureNotify>> mActionType = " + mActionType);
        if(msgCode == MSG_ENROLL_PERCENTAGE){
            if(mCapturePurpose == ENROLL){
                if (!isBinocular)
                {
                    setProgressBarMono(info);
                }
                else
                {
                    int leftPercent = (info >> 16);
                    int rightPercent = (info & 0x0000ff00) >> 8;
                    if (mIrisCamera.isFront()) {
                        setProgressBar(leftPercent, rightPercent);
                    }
                    else {
                        setProgressBar(rightPercent, leftPercent);
                    }
                }
            }
        }
        else if(msgCode == MSG_INDICATION && (mCapturePurpose == ENROLL || mIsShowIndicator)){
            updateIndicator(info);
        }
        else if(msgCode == MSG_CAPTURE_STATUS){
            mCaptureStatus = info;
            if (info == CaptureStatus.IDDK_READY){
                if (getCaptureViewColor() != ANIMATION){
                    updateCaptureStatusView(ANIMATION);
                }
            }
            else if (info == CaptureStatus.IDDK_COMPLETE){
                MediaData.getInstance().play(mContext.get(), R.raw.completed);
                if (mActionType == ActionType.ENROLL) {
                    int ret = mIrisReg.enrollCapture(mEnrolleeId);
                    if (ret != GemResult.IDDK_OK) {
                        mCaptureCallback.onFailed(ret, "Enroll failed");
                    }

                }
                if (mActionType == ActionType.ENROLL || mActionType == ActionType.CAPTURE) {
                    DataBuffer leftTemplate = new DataBuffer();
                    DataBuffer rightTemplate = new DataBuffer();
                    DataBuffer unknownTemplate = new DataBuffer();
                    String errMsg = "";
                    int ret = getResultTemplates(leftTemplate, rightTemplate, unknownTemplate);
                    if (ret != GemResult.IDDK_OK) {
                        mCaptureCallback.onFailed(ret, "Get templates failed");
                        return ret;
                    }

                    mCaptureCallback.onTemplateCreated(leftTemplate.getData(), rightTemplate.getData(), unknownTemplate.getData());
                }
                Log.d(TAG, "onUpdateStatus>> call finish");
            }
            else if (info == CaptureStatus.IDDK_TIMEOUT){
                MediaData.getInstance().play(mContext.get(), R.raw.timeout);
                mCaptureCallback.onFailed(GemResult.IDDK_TIMEOUT, "Capture timeout");
                Log.d(TAG, "onUpdateStatus>> call finish timeout");
            }
            else if (info == CaptureStatus.IDDK_ABORT){
                MediaData.getInstance().play(mContext.get(), R.raw.capture_aborted_heather);
                mCaptureCallback.onFailed(GemResult.IDDK_CAPTURE_ABORTED, "Capture abort");
                Log.d(TAG, "onUpdateStatus>> call finish abort");
            }
        }
        else if(MSG_ERROR == msgCode && 28 == info){
            final String txt = "No valid license";
            Log.d(TAG, txt);
            if (mView != null)
            {
                mView.post(()-> {
                    TextView tv = (TextView) mView.findViewById(R.id.txtIndicator);
                    tv.setText(txt);
                });
            }
        }
        return 0;
    }

    private void resetProgress() {
        if (mView == null)
            return;

        mView.post(new Runnable() {
                       @Override
                       public void run() {
                           ArcProgress leftPro = (ArcProgress) mView.findViewById(R.id.left_progress);
                           leftPro.setProgress(0);

                           ArcProgress rightPro = (ArcProgress) mView.findViewById(R.id.right_progress);
                           rightPro.setProgress(0);
                       }
                   }
        );
    }

    /**
     * Update progressBar value
     * @param left left eye percent
     * @param right right eye percent
     */
    public void setProgressBar(final int left, final int right) {
        if(mIsStoppedCapture || mView == null){
            return;
        }
        mView.post(()->
                new CountDownTimer(400, 20) {
                    public void onTick(long millisUntilFinished) {
                        ArcProgress leftPro = (ArcProgress) mView.findViewById(R.id.left_progress);
                        int curLeft = leftPro.getProgress();
                        curLeft = curLeft + (int) ((left - curLeft) / 20.0);
                        leftPro.setProgress(curLeft);

                        ArcProgress rightPro = (ArcProgress) mView.findViewById(R.id.right_progress);
                        int curRight = rightPro.getProgress();
                        curRight = curRight + (int) ((right - curRight) / 20.0);
                        rightPro.setProgress(curRight);
                    }

                    public void onFinish() {
                        ArcProgress leftPro = (ArcProgress) mView.findViewById(R.id.left_progress);
                        leftPro.setProgress(left);

                        ArcProgress rightPro = (ArcProgress) mView.findViewById(R.id.right_progress);
                        rightPro.setProgress(right);
                    }
                }.start()
        );
    }

    /**
     * Update progressBarMono value
     * @param unknown unknown eye percent
     */
    public void setProgressBarMono(final int unknown) {
        if(mIsStoppedCapture || mView == null){
            return;
        }
        mView.post(()->
                new CountDownTimer(400, 20) {
                    public void onTick(long millisUntilFinished) {
                        ArcProgress unknownPro = (ArcProgress) mView.findViewById(R.id.mono_progress);
                        int curUnknown = unknownPro.getProgress();
                        curUnknown = curUnknown + (int) ((unknown - curUnknown) / 20.0);
                        unknownPro.setProgress(curUnknown);
                    }

                    public void onFinish() {
                        ArcProgress unknownPro = (ArcProgress) mView.findViewById(R.id.mono_progress);
                        unknownPro.setProgress(unknown);
                    }
                }.start()
        );
    }

    /**
     * Update indicator
     * @param indication indication
     */
    public void updateIndicator(final int indication) {
        if(mIsStoppedCapture || mView == null){
            return;
        }
        String guideTxt = "";
        switch (indication) {
            case Indication.GOOD:
                if (getCaptureViewColor() != GREEN){
                    updateCaptureStatusView(GREEN);
                }
                break;
            case Indication.CAPTURE_LOOK_STRAIGHT:
                guideTxt = "Look straight to camera";
                MediaData.getInstance().play(mContext.get(), MediaData.lookStraight);
                break;
            case Indication.CAPTURE_MOVE_CLOSER:
                guideTxt = "Move closer";
                MediaData.getInstance().play(mContext.get(), MediaData.moveCloser);
                break;
            case Indication.CAPTURE_MOVE_FARTHER:
                guideTxt = "Move farther";
                MediaData.getInstance().play(mContext.get(), MediaData.moveFarther);
                break;
            case Indication.CAPTURE_OPEN_EYE_FULL:
                guideTxt = "Open eyes fully";
                MediaData.getInstance().play(mContext.get(), MediaData.openEyesFully);
                break;
            case Indication.CAPTURE_NO_EYE:
                guideTxt = "No eye detected!";
                break;
            case Indication.NOT_GOOD:

                break;
            case Indication.CAPTURE_INDICATION_LEFT_FINISH:
            {
                Log.d(TAG, "onUpdateStatus>> call CAPTURE_INDICATION_LEFT_FINISH");
                mView.post(()-> {
                    ImageView leftImage = (ImageView) mView.findViewById(R.id.left_guide);
                    leftImage.setImageDrawable(mContext.get().getResources().getDrawable(R.drawable.ic_circle_green));

                });
            }
            break;
            case Indication.CAPTURE_INDICATION_RIGHT_FINISH:
            {
                Log.d(TAG, "onUpdateStatus>> call CAPTURE_INDICATION_RIGHT_FINISH");
                mView.post(()-> {
                    ImageView rightImage = (ImageView) mView.findViewById(R.id.right_guide);
                    rightImage.setImageDrawable(mContext.get().getResources().getDrawable(R.drawable.ic_circle_green));
                });
            }
            break;
            case Indication.CAPTURE_INDICATION_TOO_BRIGHT:
                guideTxt = "Too bright, Move farther!";
                break;
            case Indication.CAPTURE_INDICATION_TOO_DARK:
                guideTxt = "Too dark! Move closer!";
                break;

            default:
                break;
        }
        final String txt = guideTxt;
        mView.post(()-> {
            TextView tv = (TextView) mView.findViewById(R.id.txtIndicator);
            tv.setText(txt);
        });
    }

    /**
     * getCaptureViewColor
     * @return CaptureViewColor
     */
    private int getCaptureViewColor() {
        if (mView == null)
            return -1;

        ImageView button = (ImageView) mView.findViewById(R.id.btnStop);
        return (int) button.getTag();
    }

    /**
     * UpdateCaptureStatusView
     * @param color color
     */
    private void updateCaptureStatusView(final int color) {
        if (mView == null)
            return;

        mView.post(()-> {
            {
                ImageView button = (ImageView) mView.findViewById(R.id.btnStop);
                button.clearAnimation();
                button.setVisibility(View.VISIBLE);
                if (color == GREEN) {
                    button.setImageResource(R.drawable.ic_stop_green);
                    button.setTag(GREEN);
                } else if (color == RED) {
                    button.setImageResource(R.drawable.ic_stop);
                    button.setTag(RED);
                } else if (color == ANIMATION) {
                    button.setImageResource(R.drawable.ic_stop_anim);
                    Animation rotation = AnimationUtils.loadAnimation(mContext.get(), R.anim.rotate_full);
                    button.startAnimation(rotation);
                    button.setTag(ANIMATION);
                } else if (color == GRAY) {
                    button.setImageResource(R.drawable.ic_stop_gray);
                    button.setTag(GRAY);
                } else if (color == INVISIBLE) {
                    button.setImageResource(R.drawable.ic_stop_gray);
                    button.setTag(INVISIBLE);
                    button.setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    int setFlipImages(boolean isFlipImages) {
        return 1;
    }

}
