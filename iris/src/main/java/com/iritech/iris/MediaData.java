package com.iritech.iris;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;

/**
 * Created by Duy Nguyen on 1/8/2016.
 * MediaData
 */
public class MediaData
{
    private static final String TAG = "MediaData";

    private boolean isSoundDisable = false;
    static final int lookStraight = R.raw.lookstraight;
    static final int moveCloser = R.raw.movecloser;
    static final int moveFarther = R.raw.movefarther;
    static final int openEyesFully = R.raw.openeyesfully;
    static final int completed = R.raw.completed;
    private MediaPlayer mediaPlayer;

    private MediaData() {
    }

    public void setSoundEnable(boolean enable) {
        isSoundDisable = !enable;
        Log.d("PROFILE", "Sound: "+ isSoundDisable);
    }

    private static MediaData instance = null;
    static MediaData getInstance() {
        if(instance == null) {
            instance = new MediaData();
        }
        return instance;
    }

    void play(final Context context, final int resId) {
        if(isSoundDisable) return;

        try {
            if (mediaPlayer != null){
                if (!mediaPlayer.isPlaying()){
                    mediaPlayer = MediaPlayer.create(context, resId);
                    mediaPlayer.start();
                } else if (resId == R.raw.completed){
                    mediaPlayer.stop();
                    mediaPlayer = MediaPlayer.create(context, resId);
                    mediaPlayer.start();
                }
            } else {
                mediaPlayer = MediaPlayer.create(context, resId);
                mediaPlayer.start();
            }

        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public void release(){
        if (mediaPlayer != null){
            while (mediaPlayer.isPlaying()){
                // waiting for playing sound finished.
            }
            Log.d(TAG, "release media");
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}

