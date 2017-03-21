package com.windsing.timerdetection;

/**
 * Created by wangjha on 2017/2/13.
 */

public interface TimerDetectorCallback {
    void onContent(String file, int type);
    void oneshot();
}
