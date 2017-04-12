package com.windsing.sounddetection;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.opengl.GLES11Ext;
import android.os.Environment;
import android.util.Log;

import com.windsing.common.AudioControl;
import com.windsing.common.CameraControl;
import com.windsing.common.FileControl;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by wangjha on 2017/2/10.
 */

public class SoundDetector {
    private static final String LOG_TAG = "SoundDetector";
    private final Context mContext;
    private final SoundDetectorCallback mContentCallback;
    private MediaRecorder mRecorder;
    private SoundDetectorThread worker;
    private int detectCount = 0;
    private int dbAll = 0;
    private int dbAllLast = 0;
    private int detectState = 0;
    private int silentState = 0;
    private final static int detectCountAll = 20;       //每次录制1秒钟：20*50
    private final static int detectSoundDbDiff = (8 + 2);
    private final static int detectSoundDbDiffAll = detectSoundDbDiff * detectCountAll;
    private final static int detectSoundCount = 20;      //最大录制20秒：20*1
    private final static int detectSoundSlientCount = 4;  //静音计数
    private LocalServerSocket lss;
    private LocalSocket receiver, sender;
    private byte[] mDateBuf;
    private int cameraId;
    private int mContentType;
    private int mVideoFrameRate = 15;
    private int mVideoWidth = 1280, mVideoHeight = 720;
    private Camera mCamera = null;
    private SurfaceTexture mSurfaceTexture = null;
    private volatile boolean runing_Flg = false;
    private String dirString;
    private String fileString;
    private DataInputStream mDataInput;
    private int byteOffset = 0;


    class SoundDetectorThread extends Thread{
        private AtomicBoolean runingFlg = new AtomicBoolean(true);

        public void stopThread(){
            runingFlg.set(false);
        }

        public void run(){
            while(runingFlg.get()){
                int db = getDb(mRecorder.getMaxAmplitude());
                dbAll += db;

                //readData();

                if(++detectCount > detectCountAll){
                    if(dbAllLast != 0){
                        Log.d(LOG_TAG, "######>>>>>>db:" + dbAll + "," + dbAllLast + "," + (dbAll - dbAllLast) + "," + detectState);

                        if(detectState > detectSoundCount){
                            detectResult(true);
                            detectState = 0;
                            silentState = 0;
                        }
                        else if((dbAll - dbAllLast) > detectSoundDbDiffAll){
                            Log.d(LOG_TAG, "######################################>>>>in:" + detectState);
                            if(fileString == null){
                                stopRecord();
                                startRecord(mContentType, true);
                            }
                            if(detectState == 0){
                                detectState++;
                            }
                            silentState = 0;
                        }else if(detectState != 0 && (dbAllLast - dbAll) > detectSoundDbDiffAll){
                            Log.d(LOG_TAG, "######################################>>>>out:" + silentState);
                            if(silentState == 0){
                                silentState++;
                            }
                        }

                        if(silentState > detectSoundSlientCount){
                            detectResult(true);
                            detectState = 0;
                            silentState = 0;
                        }

                        if(detectState != 0){
                            detectState++;
                        }
                        if(silentState != 0) {
                            silentState++;
                        }
                    }

                    dbAllLast = dbAll;
                    dbAll = 0;
                    detectCount = 0;
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private int getDb(int amplitude){
        double db =  20 * Math.log10((double)Math.abs(amplitude));
        db = (db > 10 && db < 200) ? db : 30;

        return (int) db;
    }

    private void readData() {
        int count = 0;
        try {
            count = mDataInput.read(mDateBuf, byteOffset, 1024 * 50);
        } catch (IOException e) {
            //e.printStackTrace();
        }

        //Log.d(LOG_TAG, "######readData:" + count + "," + byteOffset);
        if (count > 0) {
            byteOffset += count;
        }
    }

    private String getFileString(){
        if(dirString == null){
            File sdCard = Environment.getExternalStorageDirectory();
            dirString = sdCard.getAbsolutePath() + "/SoundDetector";
        }
        File dir = new File(dirString);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat("'Timer'_yyyyMMdd_HHmmss");
        String fileName = dir + "/" + dateFormat.format(date);
        Log.d(LOG_TAG, "getFileString:" + fileName);

        return fileName;
    }

    private boolean checkCameraHardware() {
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            return true;
        } else {
            return false;
        }
    }

    private Camera getCameraInstance(){
        Camera c = null;

        if (Camera.getNumberOfCameras() >= 2) {
            c = Camera.open(cameraId);
        } else {
            c = Camera.open();
        }

        return c;
    }

    private void initCamera(){
        if(mCamera != null){
            return;
        }

        if(checkCameraHardware()){
            mCamera = getCameraInstance();
            mSurfaceTexture = new SurfaceTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
            try {
                mCamera.setPreviewTexture(mSurfaceTexture);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPictureSize(mVideoWidth, mVideoHeight);
            parameters.setPreviewFrameRate(mVideoFrameRate);
            mCamera.setParameters(parameters);
        }
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    private void startRecord(int contentType, boolean trueFile){
        mRecorder = new MediaRecorder();

        if(contentType == 0){
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        }else if(contentType == 1){
            initCamera();
            mCamera.unlock();

            mRecorder.setCamera(mCamera);
            mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mRecorder.setVideoFrameRate(mVideoFrameRate);
            mRecorder.setVideoSize(mVideoWidth, mVideoHeight);
            mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mRecorder.setVideoEncodingBitRate(1024 * 1024 * 2);
            mRecorder.setOrientationHint(CameraControl.getVideoRotation(mContext, cameraId));
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        }

        if(trueFile){
            if(contentType == 0){
                fileString = FileControl.getFileString("Sounddetector", "Sound") + ".wav";
            }else{
                fileString = FileControl.getFileString("Sounddetector", "Sound") + ".mp4";
            }

            mRecorder.setOutputFile(fileString);
        }else{
            fileString = null;
            mRecorder.setOutputFile("/dev/null");
        }


//        try{
//            lss = new LocalServerSocket("SoundDetector");
//            receiver = new LocalSocket();
//            receiver.connect(new LocalSocketAddress("SoundDetector"));
//            receiver.setReceiveBufferSize(500000);
//            receiver.setSoTimeout(1);
//            //receiver.setSendBufferSize(50000);
//            sender = lss.accept();
//            //sender.setReceiveBufferSize(500000);
//            sender.setSendBufferSize(500000);
//            mDataInput = new DataInputStream(receiver.getInputStream());
//        }catch (IOException e1){
//            e1.printStackTrace();
//        }
//        mRecorder.setOutputFile(sender.getFileDescriptor());

        byteOffset= 0;
        detectState = 0;

        dbAllLast = dbAll;
        dbAll = 0;
        detectCount = 0;

        try {
            mRecorder.prepare();
            mRecorder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void stopRecord(){
        mRecorder.stop();
        mRecorder.release();
//        try {
//            receiver.close();
//            sender.close();
//            lss.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }


    private void detectResult(boolean ok){
        Log.d(LOG_TAG, "detectResult:" + ok + "," + detectState);
        stopRecord();

        if(ok && mContentCallback != null && fileString != null){
//            if(mContentType == 0){
//                fileString = getFileString() + ".amr";
//            }else{
//                fileString = getFileString() + ".mp4";
//            }
//
//            try {
//                FileOutputStream out = new FileOutputStream(new File(fileString));
//                out.write(mDateBuf, 0, byteOffset);
//                out.close();
//            } catch (FileNotFoundException e) {
//                e.printStackTrace();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

            mContentCallback.onContent(fileString, mContentType);
        }

        startRecord(mContentType, false);
    }

    public SoundDetector(Context context, SoundDetectorCallback callback){
        mContentCallback = callback;
        mContext = context;
        mDateBuf = new byte[1024 * 1024 * 2];
    }

    public void setCamera(int c){
        if( c == 0){
            cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        }
        else if(c == 1){
            cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        }
    }

    public void setContentType(int type) {
        mContentType = type;
    }

    public synchronized int start(){
        if(runing_Flg) {
            return -1;
        }
        runing_Flg = true;

        AudioControl.setMediaMute(mContext, true);
        startRecord(mContentType, false);
        worker = new SoundDetectorThread();
        worker.start();

        return 0;
    }

    public synchronized int stop(){
        if(!runing_Flg){
            return -1;
        }
        AudioControl.setMediaMute(mContext, false);
        worker.stopThread();
        stopRecord();
        releaseCamera();
        runing_Flg = false;

        return 0;
    }

    public boolean getStatus(){
        return runing_Flg;
    }

}
