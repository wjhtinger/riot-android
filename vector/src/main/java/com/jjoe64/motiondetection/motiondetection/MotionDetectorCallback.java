package com.jjoe64.motiondetection.motiondetection;

public interface MotionDetectorCallback {
    void onMotionDetected();
    void onTooDark();
    void onContent(String file, int type);
}
