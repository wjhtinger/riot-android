package com.jjoe64.motiondetection.motiondetection;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Uri;
import android.opengl.GLES11Ext;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

import com.windsing.common.FileControl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Thread.sleep;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;

public class MotionDetector {
    class MotionDetectorThread extends Thread {
        private AtomicBoolean isRunning = new AtomicBoolean(true);

        public void stopDetection() {
            isRunning.set(false);
        }

        @Override
        public void run() {
            while (isRunning.get()) {
                long now = System.currentTimeMillis();

//                if(now-recdorderLastCheck > 1000*5){
//                    recdorderLastCheck = now;
//                    mediaRecordStop();
//                }

                if (now-lastCheck > checkInterval) {
                    lastCheck = now;

                    //刚启动摄像头图像不稳，可能误判，因此忽略前面几帧
                    if(detectCount < 5){
                        detectCount++;
                        mCamera.addCallbackBuffer(mBuffer);
                        continue;
                    }
                    Log.d(LOG_TAG, "######################000");
                    if(safeToTakePicture == false){
                        continue;
                    }
                    Log.d(LOG_TAG, "######################111");
                    if (nextData.get() != null) {
                        int[] img = ImageProcessing.decodeYUV420SPtoLuma(nextData.get(), nextWidth.get(), nextHeight.get());

                        // check if it is too dark
                        int lumaSum = 0;
                        for (int i : img) {
                            lumaSum += i;
                        }
                        if (lumaSum < minLuma) {
                            if (motionDetectorCallback != null) {
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        motionDetectorCallback.onTooDark();
                                    }
                                });
                            }
                        } else if (detector.detect(img, nextWidth.get(), nextHeight.get())) {
                            // check
                            if (motionDetectorCallback != null) {
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        motionDetectorCallback.onMotionDetected();
                                    }
                                });
                            }

                            if(safeToTakePicture == true){
                                if(contentType == 0){
                                    //takePicturefromJpg();
                                    takePicturefromRaw(nextData.get(), nextWidth.get(), nextHeight.get());
                                }else if(contentType == 1){
                                    takeVideo();
                                }
                            }
                        }

                        if(runing_Flg)
                            mCamera.addCallbackBuffer(mBuffer);
                    }
                }
                if(runing_Flg){
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static final String LOG_TAG = "MotionDetector";
    private final AggregateLumaMotionDetection detector;
    private long checkInterval = 200;
    private long lastCheck = 0;
    private MotionDetectorCallback motionDetectorCallback;
    private Handler mHandler = new Handler();

    private AtomicReference<byte[]> nextData = new AtomicReference<>();
    private AtomicInteger nextWidth = new AtomicInteger();
    private AtomicInteger nextHeight = new AtomicInteger();
    private int minLuma = 1000;
    private MotionDetectorThread worker;

    private Camera mCamera;
    private volatile boolean runing_Flg = false;
    private SurfaceHolder previewHolder;
    private Context mContext;
    private SurfaceView mSurface;

    SurfaceTexture mSurfaceTexture;
    private int cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private String dirString = null;
    private int mWidth = 1280, mHeight = 720;
    private byte[] mBuffer;
    private volatile boolean safeToTakePicture = true;
    private long detectCount;
    private int contentType = 0;
    private static MotionDetector mMotionDetector = null;
    private MediaRecorder mRecorder;
    private LocalServerSocket lss;
    private LocalSocket receiver, sender;
    private byte[] recdorderByteBuffer;
    private long recdorderLastCheck = 0;
    private boolean cameraSwitchFlg = false;

    public MotionDetector(Context context, SurfaceView previewSurface) {
        detector = new AggregateLumaMotionDetection();
        mContext = context;

        if(previewSurface == null){
            mSurfaceTexture = new SurfaceTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
            mSurface = null;
        }else{
            mSurface = previewSurface;
        }
    }

    public void setMotionDetectorCallback(MotionDetectorCallback motionDetectorCallback) {
        this.motionDetectorCallback = motionDetectorCallback;
    }

    public void consume(byte[] data, int width, int height) {
        nextData.set(data);
        nextWidth.set(width);
        nextHeight.set(height);
    }

    public void setCheckInterval(long checkInterval) {
        this.checkInterval = checkInterval;
    }

    public void setMinLuma(int minLuma) {
        this.minLuma = minLuma;
    }

    public void setLeniency(int l) {
        detector.setLeniency(l);
    }

    public void setCamera(int c){
        int id = cameraId;
        if( c == 0){
            cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        }
        else if(c == 1){
            cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        }

        if(id != cameraId){
            cameraSwitchFlg = true;
        }
    }

    public void setContentType(int type){
        contentType = type;
    }

    public void setDirString(String dir){
        dirString = dir;
    }

    public void onResume() {
        if(mCamera == null){
            if (checkCameraHardware()) {
                mCamera = getCameraInstance();

                if(mSurface == null){
                    try {
                        mCamera.setPreviewTexture(mSurfaceTexture);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    Camera.Parameters parameters = mCamera.getParameters();
                    parameters.setPreviewSize(mWidth, mHeight);
                    //parameters.setPreviewFpsRange(5, 15);
                    //size2  = size2 * ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8;
                    mBuffer = new byte[mWidth * mHeight * 4]; // class variable
                    mCamera.addCallbackBuffer(mBuffer);
                    mCamera.setPreviewCallbackWithBuffer(previewCallback);
                    mCamera.setParameters(parameters);
                    mCamera.startPreview();

                }else{
                    // configure preview
                    previewHolder = mSurface.getHolder();
                    previewHolder.addCallback(surfaceCallback);
                    previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                }

                //worker = new MotionDetectorThread();
                //worker.start();

                runing_Flg = true;
            }
        }

        detectCount = 0;

//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                mediaRecordStart(3);
//            }
//        }).run();
    }

    public void onPause() {
        runing_Flg = false;
        try {
            while(!safeToTakePicture){
                Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (worker != null) worker.stopDetection();
        if (previewHolder != null) previewHolder.removeCallback(surfaceCallback);
        releaseCamera();
    }

    public synchronized int start(){
        if(runing_Flg && !cameraSwitchFlg){
            return -1;
        }
        if(runing_Flg && cameraSwitchFlg){
            onPause();
            cameraSwitchFlg = false;
        }

        onResume();
        return 0;
    }

    public synchronized int stop(){
        if(!runing_Flg){
            return -1;
        }

        onPause();
        return 0;
    }

    public boolean checkCameraHardware() {
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    private Camera getCameraInstance(){
        Camera c = null;

        try {
            if (Camera.getNumberOfCameras() >= 2) {
                //if you want to open front facing camera use this line
                c = Camera.open(cameraId);
            } else {
                c = Camera.open();
            }
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
            //txtStatus.setText("Kamera nicht zur Benutzung freigegeben");
        }
        return c; // returns null if camera is unavailable
    }

    private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void onPreviewFrame(byte[] data, Camera cam) {
            if (data == null) return;
            Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null) return;
            Log.d("MotionDetectorSSSSSSSSS", "Using width=" + size.width + " height=" + size.height);
            consume(data, size.width, size.height);
            handleCameraData();
        }
    };


    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                mCamera.setPreviewDisplay(previewHolder);
                //mCamera.setPreviewCallback(previewCallback);
            } catch (Throwable t) {
                Log.e("MotionDetector", "Exception in setPreviewDisplay()", t);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Camera.Parameters parameters = mCamera.getParameters();
            Camera.Size size = getBestPreviewSize(width, height, parameters);
            if (size != null) {
                parameters.setPreviewSize(size.width, size.height);
                Log.d("MotionDetector", "Using width=" + size.width + " height=" + size.height);
            }

            parameters.setPreviewSize(1280, 720);
            //parameters.setPictureSize(1280, 720);
            //parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);

            int size2 = 1920 * 1080 * 3;
            //size2  = size2 * ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8;
            mBuffer = new byte[size2]; // class variable
            mCamera.addCallbackBuffer(mBuffer);
            mCamera.setPreviewCallbackWithBuffer(previewCallback);

            mCamera.setParameters(parameters);
            mCamera.startPreview();
            runing_Flg = true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // Ignore
        }
    };

    private static Camera.Size getBestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;

        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                } else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;

                    if (newArea > resultArea) result = size;
                }
            }
        }

        return result;
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    private class PhotoHandler implements Camera.PictureCallback {

        @Override
        public void onPictureTaken(byte[] data, android.hardware.Camera camera) {
            String fileSrting = FileControl.getFileString("Motiondetector", "Motion") + ".jpg";
            try {
                File outFile = new File(fileSrting);
                FileOutputStream outStream = new FileOutputStream(outFile);
                outStream.write(data);
                outStream.flush();
                outStream.close();
                Log.d("MotionDetector", "onPictureTaken - wrote bytes: " + data.length + " to " + outFile.getAbsolutePath());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
            }

            if(runing_Flg){
                mediaScanBc(fileSrting);
                mCamera.startPreview();
            }

            new Handler().postDelayed(new Runnable(){
                public void run() {
                    safeToTakePicture = true;
                }
            }, 1000);
        }
    }

    private void takePicturefromRaw(byte[] yuv420sp, int width, int height) {
        safeToTakePicture = false;
        detector.clear();

        int[] rgb = ImageProcessing.decodeYUV420SPtoRGB(yuv420sp, width, height);
        Bitmap bitmap = Bitmap.createBitmap(rgb, nextWidth.get(), nextHeight.get(), Bitmap.Config.ARGB_8888);
        Matrix m = new Matrix();
        m.setRotate(-90, (float) bitmap.getWidth(), (float) bitmap.getHeight());
        Bitmap bitmapR = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);

        final String fileSrting = FileControl.getFileString("Motiondetector", "Motion") + ".jpg";
        File outFile = new File(fileSrting);
        try {
            FileOutputStream fos = new FileOutputStream(outFile);
            bitmapR.compress(Bitmap.CompressFormat.JPEG, 80, fos);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if(runing_Flg){
            mediaScanBc(fileSrting);

            if (motionDetectorCallback != null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        motionDetectorCallback.onContent(fileSrting, contentType);
                    }
                });
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        safeToTakePicture = true;
        detectCount = 2;    //防止之前残余帧引起的误判断
    }

    private void takePicturefromJpg(){
        safeToTakePicture = false;
        detector.clear();
        mCamera.takePicture(null, null, new PhotoHandler());
    }

    private void takeVideo(){
        safeToTakePicture = false;
        detector.clear();

        mCamera.unlock();

        MediaRecorder recorder = new MediaRecorder();
        recorder.setCamera(mCamera);
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);

        // 3: Set parameters, Following code does the same as getting a CamcorderProfile (but customizable)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        // 3.1 Video Settings
        recorder.setVideoSize(1280, 720);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setVideoEncodingBitRate(500000);
        recorder.setVideoFrameRate(15);
        recorder.setOrientationHint(270);
        // 3.2 Audio Settings
        recorder.setAudioChannels(1);
        recorder.setAudioSamplingRate(44100);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        recorder.setAudioEncodingBitRate(16);

        // Step 4: Set output file
        final String fileSrting = FileControl.getFileString("Motiondetector", "Motion") + ".mp4";
        recorder.setOutputFile(fileSrting);

        try {
            recorder.prepare();
            recorder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            int count = 100;
            while(runing_Flg && count > 0){
                Thread.sleep(50);
                count--;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        recorder.stop();
        recorder.release();

        if(runing_Flg){
            mediaScanBc(fileSrting);
            if (motionDetectorCallback != null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        motionDetectorCallback.onContent(fileSrting, contentType);
                    }
                });
            }
        }

        safeToTakePicture = true;
        detectCount = 2;
    }

    private void mediaRecordStart(int type){
        mRecorder = new MediaRecorder();
        Log.d("MotionDetector", "################################################FFFFF00");
        if(type == 0 || type == 3){
            //mCamera.unlock();
            //mRecorder.setCamera(mCamera);
            //mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
//            mRecorder.setVideoSize(1280, 720);
//            mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//            mRecorder.setVideoEncodingBitRate(500000);
//            mRecorder.setVideoFrameRate(15);
//            mRecorder.setOrientationHint(270);
        }

        if(type == 1 || type == 3){
            //mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            //mRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB);
        }
        Log.d("MotionDetector", "################################################FFFFF001");
        mRecorder.setAudioChannels(1);
        mRecorder.setAudioSamplingRate(44100);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mRecorder.setAudioEncodingBitRate(16);
        Log.d("MotionDetector", "################################################FFFFF002");
        try {
            lss = new LocalServerSocket("H264TEST");
            receiver = new LocalSocket();
            receiver.connect(new LocalSocketAddress("H264TEST"));
            sender = lss.accept();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("MotionDetector", "################################################FFFFF003");
        //mRecorder.setOutputFile(sender.getFileDescriptor());
        final String fileSrting = FileControl.getFileString("Motiondetector", "Motion") + ".mp4";
        mRecorder.setOutputFile(fileSrting);

        recdorderByteBuffer = new byte[1024*1024];

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mRecorder.start();
        Log.d("MotionDetector", "################################################FFFFF11");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DataInputStream dataInput = new DataInputStream(receiver.getInputStream());
                    dataInput.read(recdorderByteBuffer);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Log.d("MotionDetector", "################################################FFFFF22");

                try {
                    FileOutputStream fos = new FileOutputStream(FileControl.getFileString("Motiondetector", "Motion") + ".mp4");
                    fos.write(recdorderByteBuffer);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }).run();

    }

    private void mediaRecordTakeFile(){

    }

    private void mediaRecordStop(){
        if(mRecorder != null){
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
    }

    private void mediaScanBc(String fileString){
        Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(new File(fileString)));
        mContext.sendBroadcast(mediaScanIntent);
    }

    private void handleCameraData(){
        long now = System.currentTimeMillis();
        if (now-lastCheck > checkInterval) {
            lastCheck = now;

            if(detectCount < 5){//刚启动摄像头图像不稳，可能误判，因此忽略前面几帧
                detectCount++;
            }else{
                if (nextData.get() != null) {
                    int[] img = ImageProcessing.decodeYUV420SPtoLuma(nextData.get(), nextWidth.get(), nextHeight.get());

                    // check if it is too dark
                    int lumaSum = 0;
                    for (int i : img) {
                        lumaSum += i;
                    }
                    if (lumaSum < minLuma) {
                        if (motionDetectorCallback != null) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    motionDetectorCallback.onTooDark();
                                }
                            });
                        }
                    } else if (detector.detect(img, nextWidth.get(), nextHeight.get())) {
                        if (motionDetectorCallback != null) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    motionDetectorCallback.onMotionDetected();
                                }
                            });
                        }

                        if(contentType == 0){
                            takePicturefromRaw(nextData.get(), nextWidth.get(), nextHeight.get());
                        }else if(contentType == 1){
                            takeVideo();
                        }
                    }
                }
            }

        }
        if(runing_Flg)
            mCamera.addCallbackBuffer(mBuffer);
    }


    public boolean getStatus(){
        return runing_Flg;
    }

    protected void finalize()
    {
        onPause();
    }
}
