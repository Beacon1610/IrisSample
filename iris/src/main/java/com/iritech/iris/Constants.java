package com.iritech.iris;

import android.os.Environment;

/**
 * Created by VuNH on 5/16/2018.
 * Contain IriConstants
 */

public class Constants {
    static final String DEFAULT_SAVE_PATH = Environment.getExternalStorageDirectory().getPath() + "/iritech/";
    public static final String ACTION_ENROLL = "com.iritech.android.iriservice.ENROLL";
    public static final String ACTION_VERIFY = "com.iritech.android.iriservice.VERIFY";
    public static final String ACTION_UNENROLL = "com.iritech.android.iriservice.UNENROLL";
    public static final String ACTION_IDENTIFY = "com.iritech.android.iriservice.IDENTIFY";
    public static final String ACTION_CAPTURE = "com.iritech.android.iriservice.CAPTURE";
    static final String ACTION_ABORT = "com.iritech.android.iriservice.ABORT";
    public static final String EXTRA_USER_ID = "USER_ID";
    public static final String EXTRA_CAPTURE_PURPOSE = "CAPTURE_PURPOSE";
    static final String EXTRA_ALLOW_PAUSE = "ALLOW_PAUSE";

    static final String ACTION_CREATE_LOCKCODE = "com.iritech.android.iriservice.CREATE_LOCKCODE";
    static final String ACTION_DECODE_LOCKCODE = "com.iritech.android.iriservice.DECODE_LOCKCODE";
    static final String EXTRA_IS_SHOW_BEST = "IS_SHOW_BEST";
    static final String EXTRA_IS_QUALITY_REQUIRED = "IS_QUALITY_REQUIRED";
    static final String EXTRA_DATA = "DATA";
    static final String EXTRA_LOCKCODE = "LOCKCODE";
    static final String EXTRA_ERROR_MESSAGE = "ERROR_MESSAGE";

    public static final String EXTRA_ACTION = "EXTRA_ACTION";

    public static final String EXTRA_RESULT_CODE = "RESULT_CODE";
    public static final String EXTRA_RESULT_MSG = "RESULT_MSG";
    public static final String EXTRA_LEFT_TEMPLATE = "EXTRA_LEFT_TEMPLATE";
    public static final String EXTRA_RIGHT_TEMPLATE = "EXTRA_RIGHT_TEMPLATE";
    public static final String EXTRA_UNKNOWN_TEMPLATE = "EXTRA_UNKNOWN_TEMPLATE";
    public static final String EXTRA_MATCHING_RESULT = "EXTRA_MATCHING_RESULT";
    public static final String EXTRA_MATCHING_COUNT = "EXTRA_MATCHING_COUNT";
    public static final String EXTRA_MATCHING_ITEMS = "EXTRA_MATCHING_ITEMS";
}
