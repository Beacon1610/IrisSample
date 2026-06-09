package com.iritech.iris;
import android.annotation.TargetApi;
import android.content.Context;
import com.iritech.mqel704.IrisCamera;
import android.content.res.AssetManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import com.iritech.mqel704.IrisCapture.CameraPreviewCallback;
import dalvik.system.DexClassLoader;

/**
 * This class manage the setting information and operations of the sensor.
 */
public class CustomIrisCamera extends IrisCamera {
	public static final int SUPPORTED_IMAGE_WIDTH = 1920;
	public static final int SUPPORTED_IMAGE_HEIGHT = 1080;
	public static final int SUPPORTED_IMAGE_FORMAT = 11; // 11:IF_GREY8
	private AssetManager m_assetManager;
	private boolean mIsPreviewing = false;
	private Class<Object> mOtherCameraClass = null;
	private Object mOtherCameraInstance = null;
	boolean mUseDefaultCamera = false;
	private Method mSetPreviewCallbackMethod = null;
	private Method mStartPreviewMethod = null;
	private Method mGetStreamingMethod = null;
	private Method mSetLedMethod = null;
	private Method mStopPreviewMethod = null;
	private Method mSetSizeMethod = null;
	private Method mStartIRMethod = null;
	private Method mStopIRMethod = null;
	private Method mOpenMethod = null;
	private Method mCloseMethod = null;
	private Method mOnSurfaceCreatedMethod = null;
	private Method mGetSupportedResolutionsMethod = null;
	private Method mGetResolutionMethod = null;
	private Method mGetCameraParamsMethod = null;

	public CustomIrisCamera(Context context)
	{
		super(context);
		m_assetManager = context.getAssets();
		loadIrisCameraModule(context);
	}

	public int open(int cameraId) {
		Log.d("CustomIrisCamera", "openCamera start.");
		mIsMirror = false;
		if (mOpenMethod != null)
		{
			setResolution(SUPPORTED_IMAGE_WIDTH, SUPPORTED_IMAGE_HEIGHT);
			mPreviewCallback.onCameraConfigured(this);
			try
            {
			    mOpenMethod.invoke(mOtherCameraInstance, cameraId);
            } catch (Exception e) {
                e.printStackTrace();
                stopPreview();
            }
			if (mSetPreviewCallbackMethod != null) {
				try {
					mSetPreviewCallbackMethod.invoke(mOtherCameraInstance, mPreviewCallback);
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					e.printStackTrace();
				}
			}
		}
		else
        {
            Log.d("CustomIrisCamera", "Use AndroidCamera!");
            mUseDefaultCamera = true;
            mUseDefaultCamera = 0 == super.open(cameraId);
        }

		Log.d("CustomIrisCamera", "openCamera exit.");
		return 0;
	}
	public boolean isOpened()
	{
		Log.d("CustomIrisCamera", "openCamera exit");
		return false;
	}

	public int close() {
		Log.d("CustomIrisCamera", "closeCamera started");
		if (mCloseMethod != null)
		{
            try
            {
                mCloseMethod.invoke(mOtherCameraInstance);
            } catch (Exception e) {
                e.printStackTrace();
                stopPreview();
            }
			return 0;
		}

		if(mUseDefaultCamera){
			super.close();
		}
		return 0;
	}
	@TargetApi(26)
	protected int startPreview() {
		Log.d("CustomIrisCamera", "startPreview");

		try
        {
            if (mStartPreviewMethod != null) {
                mStartPreviewMethod.invoke(mOtherCameraInstance);
            }
            else {
            	/*mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 8);
            	mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, 100);
				mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_OFF);
				mPreviewRequest = mPreviewRequestBuilder.build();
				mCaptureSession.setRepeatingRequest(mPreviewRequest, null, null);*/
				return super.startPreview();
			}
        } catch (Exception e) {
            e.printStackTrace();
            stopPreview();
        }

		Thread threadSendImage = new Thread(new Runnable() {
			@Override
			public void run() {
				ByteBuffer buffer = null;
				mIsPreviewing = true;
				try {
					if (mSetPreviewCallbackMethod == null) {
						if (mGetStreamingMethod == null)
						{
							Log.d("CustomIrisCamera", "mGetStreamingMethod == null");
							InputStream input = m_assetManager.open("test_2592_1200.raw");
							buffer = ByteBuffer.allocateDirect(input.available());
							buffer.order(ByteOrder.LITTLE_ENDIAN);
							input.read(buffer.array(), buffer.arrayOffset(), input.available());
							input.close();

							int count = 0;
							while (count < 1000 && mIsPreviewing)
							{
								CustomIrisCamera.this.mPreviewCallback.onPreviewFrame(buffer, null);
								count++;
							}
						}
						else
						{
							int blinking = 0;
							long s1 = 0;
							while (mIsPreviewing) {
								long s2 = (33 - (SystemClock.currentThreadTimeMillis() - s1));
								if (s2 > 0) {
									//Log.d("CustomIrisCamera", "Sleep " + s2 + " ms");
									Thread.sleep(s2);
								}

								s1 = SystemClock.currentThreadTimeMillis();
								if (mLedMode == 1) {

							/*		if(IR_BLINKING_CONFIG > 0) {
										if ((blinking % (IR_BLINKING_CONFIG * 2)) < IR_BLINKING_CONFIG) {
											setLedMethod.invoke(mOtherCameraInstance, 1);// 1: IRLed.LED_L
										} else {
											setLedMethod.invoke(mOtherCameraInstance, 2);// 1: IRLed.LED_R
										}
									}*/
									blinking++;
								}
								Log.d("CustomIrisCamera", "mGetStreamingMethod.invoke");

								buffer = (ByteBuffer) mGetStreamingMethod.invoke(mOtherCameraInstance);
								//Log.d("CustomIrisCamera", "Query buffer time= " + (SystemClock.currentThreadTimeMillis()-s1));
								CustomIrisCamera.this.mPreviewCallback.onPreviewFrame(buffer, null);
							}
							if (mSetLedMethod != null) {
								mSetLedMethod.invoke(mOtherCameraInstance, 0);// 0: IRLed.LED_NONE
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
					stopPreview();
				}
			}
		});

		threadSendImage.start();
		Log.d("CustomIrisCamera", "startPreview exit");

		return 0;
	}

	public void onSurfaceCreated(SurfaceHolder var1) {
		if(mUseDefaultCamera){
            super.onSurfaceCreated(var1);
        }
        Log.d("CustomIrisCamera", "onSurfaceCreated exit");
    }
	protected int stopPreview() {
		mIsPreviewing = false;
		Log.d("CustomIrisCamera", "stopPreview");
		if (mStopPreviewMethod != null) {
			try {
				mStopPreviewMethod.invoke(mOtherCameraInstance);
				return 0;
			}
			catch (Exception var3) {
				var3.printStackTrace();
			}
		}

		if(mUseDefaultCamera){
			return super.stopPreview();
		}

		Log.d("CustomIrisCamera", "stopPreview exit");
		return 0;
	}

	protected void startIR() {
		Log.d("CustomIrisCamera", "startIR started");
		if (mStartIRMethod != null)
		{
			try {
				mStartIRMethod.invoke(mOtherCameraInstance);
				return;
			}
			catch (Exception var3) {
				var3.printStackTrace();
			}
		}

		if(mUseDefaultCamera){
			super.startIR();
		}
	}

	protected void stopIR() {
		Log.d("CustomIrisCamera", "stopIR started");
		if (mStopIRMethod != null)
		{
			try {
				mStopIRMethod.invoke(mOtherCameraInstance);
				return;
			}
			catch (Exception var3) {
				var3.printStackTrace();
			}
		}

		if(mUseDefaultCamera){
			super.stopIR();
		}


	}

	public List<IrisCamera.Size> getSupportedResolutions() {
		List<IrisCamera.Size> var1 = new ArrayList();
		if(mUseDefaultCamera){
			var1 = super.getSupportedResolutions();
		}
		else {
			var1.add(new Size(SUPPORTED_IMAGE_WIDTH, SUPPORTED_IMAGE_HEIGHT));
		}

		String s = "";

		for (int i = 0; i < var1.size(); i++) {
			s += var1.get(i).width + "x" + var1.get(i).height + " ";
		}
		Log.d("CustomIrisCamera","Supported preview-sizes:" + s);

		return var1;
	}

	public int setResolution(int width, int height) {

		Log.d("CustomIrisCamera", "setResolution: " + width + "x" + height);
		int ret = 0;
		if (mSetSizeMethod != null)
		{
			try {
				mSetSizeMethod.invoke(mOtherCameraInstance, width, height);
				return 0;
			}
			catch (Exception var3) {
				var3.printStackTrace();
			}
		}

		if(mUseDefaultCamera){
			ret = super.setResolution(width, height);
		}

		Log.d("CustomIrisCamera", "setResolution ret=" + ret);
		return ret;
	}

	public IrisCamera.Size getResolution() {
        Log.d("CustomIrisCamera", "getResolution");
        if(mUseDefaultCamera){
            return super.getResolution();
        }
        return new IrisCamera.Size(SUPPORTED_IMAGE_WIDTH, SUPPORTED_IMAGE_HEIGHT);
    }

	protected IrisCamera.Parameters getCameraParams(){
		//mOrientation = 0;
		if(mUseDefaultCamera){
			IrisCamera.Parameters params = super.getCameraParams();
			//params.width = 1920;
			//params.height = 1080;
			Log.d("CustomIrisCamera", "mPreviewWidth x mPreviewHeight =" + mPreviewWidth + "x"+mPreviewHeight);
			Log.d("CustomIrisCamera", "getCameraParams " + params.width + "x" + params.height + ". fmt" + params.format + ". fps=" + params.previewFrameRate);
			Log.d("CustomIrisCamera", "getCameraParams mOrientation = " + mOrientation + ". isFront=" + mIsFront + ". isMirror=" + mIsMirror);
			
			return params;
		}
		return new IrisCamera.Parameters(SUPPORTED_IMAGE_WIDTH, SUPPORTED_IMAGE_HEIGHT, SUPPORTED_IMAGE_FORMAT, 30);
	}

	private void loadIrisCameraModule(Context context)
	{
		try
		{
			final String libPath = "/sdcard/iritech/iriscamera.dex";
			final File tmpDir = context.getDir("dex", 0);
			final DexClassLoader classloader = new DexClassLoader(libPath, tmpDir.getAbsolutePath(),
					context.getFilesDir().getAbsolutePath(), this.getClass().getClassLoader());

			mOtherCameraClass = (Class<Object>) classloader.loadClass(
					"com.iritech.mqel704.CustomIrisCamera");

			mOtherCameraInstance  = mOtherCameraClass.newInstance();
		} catch (Exception var3) {
            Log.d("CustomIrisCamera", "loadIrisCameraModule load failed!");
		}

		try{
			Class[] cArg = new Class[1];
			cArg[0] = Context.class;
			final Method initMethod = mOtherCameraClass.getMethod("init", cArg);
			initMethod.invoke(mOtherCameraInstance, context);
		} catch (Exception var3) {
            Log.d("CustomIrisCamera", "loadIrisCameraModule get method init failed!");
		}

		try{
			Class[] cArg = new Class[1];
			cArg[0] = CameraPreviewCallback.class;
			mSetPreviewCallbackMethod = mOtherCameraClass.getMethod("setPreviewCallback", cArg);
		} catch (Exception var3) {
            Log.d("CustomIrisCamera", "loadIrisCameraModule get method setPreviewCallback failed!");
		}

		try{
			mStartPreviewMethod = mOtherCameraClass.getMethod("startPreview");
		} catch (Exception var3) {
            Log.d("CustomIrisCamera", "loadIrisCameraModule get method startPreview failed!");
		}

		try{
			mGetStreamingMethod = mOtherCameraClass.getMethod("getStreaming");
		} catch (Exception var3) {
            Log.d("CustomIrisCamera", "loadIrisCameraModule get method getStreaming failed!");
		}

		try{
			Class[] cArg1 = new Class[1];
			cArg1[0] = int.class;
			mSetLedMethod = mOtherCameraClass.getMethod("setIRLed", cArg1);
		} catch (Exception var3) {
            Log.d("CustomIrisCamera", "loadIrisCameraModule get method setIRLed failed!");
		}

		try{
			mStopPreviewMethod = mOtherCameraClass.getMethod("stopPreview");
		} catch (Exception var3) {
            Log.d("CustomIrisCamera", "loadIrisCameraModule get method stopPreview failed!");
		}

		try{
			Class[] cArg2 = new Class[2];
			cArg2[0] = int.class;
			cArg2[1] = int.class;
			mSetSizeMethod = mOtherCameraClass.getMethod("setSize", cArg2);
		} catch (Exception var3) {
            Log.d("CustomIrisCamera", "loadIrisCameraModule get method setSize failed!");
		}

		try{
			mStartIRMethod = mOtherCameraClass.getMethod("startIR");
		} catch (Exception var3) {
            Log.d("CustomIrisCamera", "loadIrisCameraModule get method startIR failed!");
		}

		try{
			mStopIRMethod = mOtherCameraClass.getMethod("stopIR");
		} catch (Exception var3) {
            Log.d("CustomIrisCamera", "loadIrisCameraModule get method stopIR failed!");
		}

		try{
			Class[] cArg = new Class[1];
			cArg[0] = int.class;
			mOpenMethod = mOtherCameraClass.getMethod("open", cArg);
		} catch (Exception var3) {
            Log.d("CustomIrisCamera", "loadIrisCameraModule get method open failed!");
		}

		try{
			mCloseMethod = mOtherCameraClass.getMethod("close");
		} catch (Exception var3) {
            Log.d("CustomIrisCamera", "loadIrisCameraModule get method close failed!");
		}

		try{
			Class[] cArg = new Class[1];
			cArg[0] = SurfaceHolder.class;
			mOnSurfaceCreatedMethod = mOtherCameraClass.getMethod("onSurfaceCreated", cArg);
		} catch (Exception var3) {
            Log.d("CustomIrisCamera", "loadIrisCameraModule get method onSurfaceCreated failed!");
		}

		try{
			mGetSupportedResolutionsMethod = mOtherCameraClass.getMethod("getSupportedResolution");
		} catch (Exception var3) {
            Log.d("CustomIrisCamera", "loadIrisCameraModule get method getSupportedResolution failed!");
		}

		try{
			mGetResolutionMethod = mOtherCameraClass.getMethod("getResolution");
		} catch (Exception var3) {
            Log.d("CustomIrisCamera", "loadIrisCameraModule get method getResolution failed!");
		}

		try{
			mGetCameraParamsMethod = mOtherCameraClass.getMethod("getCameraParams");
		} catch (Exception var3) {
            Log.d("CustomIrisCamera", "loadIrisCameraModule get method getCameraParams failed!");
		}
	}
}

