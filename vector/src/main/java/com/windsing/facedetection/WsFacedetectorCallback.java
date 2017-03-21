package com.windsing.facedetection;

/**
 * Created by New on 2017/2/14.
 */

public interface WsFacedetectorCallback {
    void onDetected();
    void onDetectLost();
    void onContent(String file, int type);
    void onVideoCall();
}
