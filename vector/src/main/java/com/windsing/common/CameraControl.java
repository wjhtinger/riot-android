package com.windsing.common;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.VectorDrawable;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.net.Uri;
import android.opengl.GLES11Ext;
import android.view.Surface;
import android.view.WindowManager;

import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by New on 2017/2/19.
 */

public class CameraControl{
    private static final String LOG_TAG = "CameraControl";


    public static int getPhotoRotation(Context context, int cameraId){
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);

        WindowManager wmManager=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        int rotation = wmManager.getDefaultDisplay().getRotation();
        rotation = 90; //TODO,因为没有UI,getRotation()不起作用，所以这里默认所有照片都是立着的，但实际应该按照手机方位设置立着还是躺着
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break; // portrait
            case Surface.ROTATION_90: degrees = 90; break; // landscape
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break; // landscape
        }

        int previewRotation;
        int imageRotation;

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            imageRotation = previewRotation = (info.orientation + degrees) % 360;
            previewRotation = (360 - previewRotation) % 360;  // compensate the mirror
        } else {  // back-facing
            imageRotation = previewRotation = (info.orientation - degrees + 360) % 360;
        }

        return imageRotation;
    }

    public static int getVideoRotation(Context context, int cameraId) {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);

        WindowManager wmManager=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        int rotation = wmManager.getDefaultDisplay().getRotation();
        rotation = 90;  //TODO,因为没有UI,getRotation()不起作用，所以这里默认所有照片都是立着的，但实际应该按照手机方位设置立着还是躺着
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (info.orientation + degrees) % 360;
        } else {  // back-facing
            return (info.orientation - degrees + 360) % 360;
        }
    }

    static CamcorderProfile camcorderProfile = null;
    public static CamcorderProfile getCamcorderProfile(int cameraId) {

        // we should test by camera id but hasProfile failed on some devices
        if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P)) {
            try {
                camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getCamcorderProfile() : " + e.getMessage());
            }
        }

        if ((null == camcorderProfile) && CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)) {
            try {
                camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getCamcorderProfile() : " + e.getMessage());
            }
        }

        if (null == camcorderProfile) {
            camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        }

        Log.d(LOG_TAG, "getCamcorderProfile for camera " + cameraId + " width " + camcorderProfile.videoFrameWidth + " height " + camcorderProfile.videoFrameWidth);
        return camcorderProfile;
    }

    public static void rotateImage(Context context, String filename, int rotationAngle) {
        try
        {
            // there is one
            if (0 != rotationAngle) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                options.outWidth = -1;
                options.outHeight = -1;

                // decode the bitmap
                Bitmap bitmap = null;
                try {
                    FileInputStream imageStream = new FileInputStream(filename);
                    bitmap = BitmapFactory.decodeStream(imageStream, null, options);
                    imageStream.close();
                } catch (OutOfMemoryError e) {
                    Log.e(LOG_TAG, "applyExifRotation BitmapFactory.decodeStream : " + e.getLocalizedMessage());
                } catch (Exception e) {
                    Log.e(LOG_TAG, "applyExifRotation " + e.getLocalizedMessage());
                }

                android.graphics.Matrix bitmapMatrix = new android.graphics.Matrix();
                bitmapMatrix.postRotate(rotationAngle);
                Bitmap transformedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), bitmapMatrix, false);
                bitmap.recycle();

                FileOutputStream fos = new FileOutputStream(filename);
                transformedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.flush();
                fos.close();
            }

        } catch (OutOfMemoryError e) {
            Log.e(LOG_TAG, "applyExifRotation " + e.getLocalizedMessage());
        } catch (Exception e) {
            Log.e(LOG_TAG, "applyExifRotation " + e.getLocalizedMessage());
        }
    }

}
