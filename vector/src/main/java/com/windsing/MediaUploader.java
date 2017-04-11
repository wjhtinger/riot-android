package com.windsing;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.windsing.common.FileControl;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.MXEncryptedAttachments;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.listeners.MXMediaUploadListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.AudioMessage;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.VideoMessage;
import org.matrix.androidsdk.util.ImageUtils;
import org.matrix.androidsdk.util.JsonUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import im.vector.Matrix;
import im.vector.R;
import im.vector.util.ResourceUtils;

/**
 * Created by wangjha on 2017/2/10.
 */

public class MediaUploader {
    private static final String LOG_TAG = "MediaUploader";

    static void delLocalMedia(Context context, String file){
        if (Matrix.getInstance(context).getSharedGCMRegistrationManager().isFunctionEnable(context.getString(R.string.settings_enable_save_local_file))){
            return;
        }

        FileControl.delFile(file);
    }

    static void roomSendEvent(Room room, Event event){
        room.storeOutgoingEvent(event);
        room.sendEvent(event, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                Log.d(LOG_TAG, "Send message : onSuccess ");
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.d(LOG_TAG, "Send message : onNetworkError " + e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.d(LOG_TAG, "Send message : onMatrixError " + e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.d(LOG_TAG, "Send message : onUnexpectedError " + e.getLocalizedMessage());
            }
        });
    }


    static void fileUploader(final Context context, final MXSession session, final Room room, final String file){
        InputStream audioStream = null;
        try {
            audioStream = new FileInputStream(new File(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        final String fileName= file.substring(file.lastIndexOf("/") + 1);
        final String url = " file://" + file;
        session.getMediasCache().uploadContent(audioStream, fileName, "application/msword", url, new MXMediaUploadListener() {
            @Override
            public void onUploadStart(String uploadId) {
            }

            @Override
            public void onUploadCancel(String uploadId) {
            }

            @Override
            public void onUploadError(final String uploadId, final int serverResponseCode, final String serverErrorMessage) {
                delLocalMedia(context, file);
            }

            @Override
            public void onUploadComplete(final String uploadId, final String contentUri) {
                FileMessage fileMessage = new FileMessage();
                fileMessage.url = contentUri;
                fileMessage.body = fileName;
                Room.fillFileInfo(context, fileMessage, Uri.parse(uploadId), "application/msword");

                Event newEvent = new Event(fileMessage, session.getCredentials().userId, room.getRoomId());
                newEvent.mSentState = Event.SentState.SENDING;
                roomSendEvent(room, newEvent);

                delLocalMedia(context, file);
            }
        });
    }


    static void audioUploader(final Context context, final MXSession session, final Room room, final String file){
        InputStream audioStream = null;
        try {
            audioStream = new FileInputStream(new File(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        final String fileName= file.substring(file.lastIndexOf("/") + 1);
        final String url = " file://" + file;
        session.getMediasCache().uploadContent(audioStream, fileName, "audio/x-wav", url, new MXMediaUploadListener() {
            @Override
            public void onUploadStart(String uploadId) {
            }

            @Override
            public void onUploadCancel(String uploadId) {
            }

            @Override
            public void onUploadError(final String uploadId, final int serverResponseCode, final String serverErrorMessage) {
                delLocalMedia(context, file);
            }

            @Override
            public void onUploadComplete(final String uploadId, final String contentUri) {
                AudioMessage audioMessage = new AudioMessage();
                audioMessage.url = contentUri;
                audioMessage.body = fileName;
                Room.fillFileInfo(context, audioMessage, Uri.parse(uploadId), "audio/x-wav");

                Event newEvent = new Event(audioMessage, session.getCredentials().userId, room.getRoomId());
                newEvent.mSentState = Event.SentState.SENDING;
                roomSendEvent(room, newEvent);

                delLocalMedia(context, file);
            }
        });
    }

    static void pictureUploader(final Context context, final MXSession session, final Room room, final String file){
        int rotationAngle = ImageUtils.getRotationAngleForBitmap(context, Uri.parse("file://" + file));
        Log.d(LOG_TAG, "pictureUploader rotationAngle:" + rotationAngle);
        if (0 != rotationAngle) {
            ImageUtils.rotateImage(context, "file://" + file, rotationAngle, session.getMediasCache());
        }

        final String fileName= file.substring(file.lastIndexOf("/") + 1);
        final String url = " file://" + file;
        final ImageMessage imageMessage = new ImageMessage();
        imageMessage.url = url;
        imageMessage.thumbnailUrl = null;
        imageMessage.body = fileName;
        Room.fillImageInfo(context, imageMessage, Uri.parse(url), "image/jpeg");

        InputStream imageStream = null;
        try {
            imageStream = new FileInputStream(new File(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        session.getMediasCache().uploadContent(imageStream, fileName, "image/jpeg", url, new MXMediaUploadListener() {
            @Override
            public void onUploadStart(String uploadId) {
            }

            @Override
            public void onUploadCancel(String uploadId) {
            }

            @Override
            public void onUploadError(final String uploadId, final int serverResponseCode, final String serverErrorMessage) {
                delLocalMedia(context, file);
            }

            @Override
            public void onUploadComplete(final String uploadId, final String contentUri) {
                imageMessage.url = contentUri;

                Event newEvent = new Event(imageMessage, session.getCredentials().userId, room.getRoomId());
                room.storeOutgoingEvent(newEvent);
                newEvent.mSentState = Event.SentState.SENDING;
                newEvent.updateContent(JsonUtils.toJson(imageMessage));
                roomSendEvent(room, newEvent);

                delLocalMedia(context, file);
            }
        });
    }

    public static void pictureUploaderwithThumb(final Context context, final MXSession session, final Room room, ImageMessage imageMessage, String thumbnailUrl, final String anImageUrl, final String mediaFilename, final String imageMimeType) {
        if(thumbnailUrl == null){
            //Bitmap thumbnailBitmap = MediaStore.Images.Thumbnails.getThumbnail(context.getContentResolver(), imageId, MediaStore.Images.Thumbnails.MINI_KIND, null);
            Bitmap thumbnailBitmap = ResourceUtils.createThumbnailBitmap(context, Uri.parse(anImageUrl), 320, 320);
            thumbnailUrl = session.getMediasCache().saveBitmap(thumbnailBitmap, null);
        }

        if (null == imageMessage) {
            imageMessage = new ImageMessage();
            imageMessage.url = anImageUrl;
            imageMessage.thumbnailUrl = thumbnailUrl;
            imageMessage.body = mediaFilename;
        }

        String mimeType = null;
        MXEncryptedAttachments.EncryptionResult encryptionResult = null;
        InputStream imageStream = null;
        String url = null;
        String filename;

        try {
            Uri imageUri = Uri.parse(anImageUrl);
            Uri thumbUri = Uri.parse(thumbnailUrl);

            if (null == imageMessage.info) {
                Room.fillImageInfo(context, imageMessage, imageUri, imageMimeType);
            }
            if ((null != thumbnailUrl) && (null == imageMessage.thumbnailInfo)) {
                Room.fillThumbnailInfo(context, imageMessage, thumbUri, "image/jpeg");
            }

            if (imageMessage.isThumbnailLocalContent()) {
                url = thumbnailUrl;
                mimeType = "image/jpeg";
                filename = thumbUri.getPath();
            } else {
                url = anImageUrl;
                mimeType = imageMimeType;
                filename = imageUri.getPath();
            }

            imageStream = new FileInputStream(new File(filename));

            if (room.isEncrypted() && session.isCryptoEnabled() && (null != imageStream)) {
                encryptionResult = MXEncryptedAttachments.encryptAttachment(imageStream, mimeType);
                imageStream.close();

                if (null != encryptionResult) {
                    imageStream = encryptionResult.mEncryptedStream;
                    mimeType = "application/octet-stream";
                } else {
                    //displayEncryptionAlert();
                    Log.e(LOG_TAG, "pictureUploaderwithThumb displayEncryptionAlert!");
                    return;
                }
            }

            imageMessage.body = imageUri.getLastPathSegment();

        } catch (Exception e) {
            Log.e(LOG_TAG, "pictureUploaderwithThumb failed with " + e.getMessage());
        }

        if (TextUtils.isEmpty(imageMessage.body)) {
            imageMessage.body = "Image";
        }

        final String fMimeType = mimeType;
        final ImageMessage fImageMessage = imageMessage;
        final String fThumbnailUrl = thumbnailUrl;

        final MXEncryptedAttachments.EncryptionResult fEncryptionResult = encryptionResult;

        session.getMediasCache().uploadContent(imageStream, imageMessage.isThumbnailLocalContent() ? ("thumb" + imageMessage.body) : imageMessage.body, mimeType, url, new MXMediaUploadListener() {
            @Override
            public void onUploadStart(String uploadId) {
            }

            @Override
            public void onUploadCancel(String uploadId) {
            }

            @Override
            public void onUploadError(final String uploadId, final int serverResponseCode, final String serverErrorMessage) {
                Log.e(LOG_TAG, "pictureUploaderwithThumb uploadContent error!");
                delLocalMedia(context, anImageUrl.replace("file://", ""));
            }

            @Override
            public void onUploadComplete(final String uploadId, final String contentUri) {
                if (fImageMessage.isThumbnailLocalContent()) {
                    if (null != fEncryptionResult) {
                        fImageMessage.info.thumbnail_file = fEncryptionResult.mEncryptedFileInfo;
                        fImageMessage.info.thumbnail_file.url = contentUri;
                        fImageMessage.thumbnailUrl = null;
                        session.getMediasCache().saveFileMediaForUrl(contentUri, fThumbnailUrl, -1, -1, "image/jpeg");

                    } else {
                        fImageMessage.thumbnailUrl = contentUri;
                        session.getMediasCache().saveFileMediaForUrl(contentUri, fThumbnailUrl, 320, 320, "image/jpeg");
                    }
                    // upload the high res picture
                    pictureUploaderwithThumb(context, session, room, fImageMessage, contentUri, anImageUrl, mediaFilename,  fMimeType);
                } else {
                    // replace the thumbnail and the media contents by the computed one
                    session.getMediasCache().saveFileMediaForUrl(contentUri, anImageUrl, fImageMessage.getMimeType());

                    if (null != fEncryptionResult) {
                        fImageMessage.file = fEncryptionResult.mEncryptedFileInfo;
                        fImageMessage.file.url = contentUri;
                        fImageMessage.url = null;
                    } else {
                        fImageMessage.url = contentUri;
                    }

                    // update the event content with the new message info
                    Event newEvent = new Event(fImageMessage, session.getCredentials().userId, room.getRoomId());
                    room.storeOutgoingEvent(newEvent);
                    newEvent.mSentState = Event.SentState.SENDING;
                    newEvent.updateContent(JsonUtils.toJson(fImageMessage));
                    roomSendEvent(room, newEvent);

                    delLocalMedia(context, anImageUrl.replace("file://", ""));
                }
            }
        });
    }



    static public void videoUploader(final Context context, final MXSession session, final Room room, final VideoMessage sourceVideoMessage, final String tUrl, final String thumbnailMimeType, final String videoUrl, final String body, final String videoMimeType) {
        final String thumbnailUrl;

        if(tUrl == null){
            Bitmap thumb = ThumbnailUtils.createVideoThumbnail(Uri.parse(videoUrl).getPath(), MediaStore.Images.Thumbnails.MINI_KIND);
            thumbnailUrl = Matrix.getInstance(context).getMediasCache().saveBitmap(thumb, null);
        }else{
            thumbnailUrl = tUrl;
        }

        Uri uri = Uri.parse(videoUrl);
        Uri thumbUri = Uri.parse(thumbnailUrl);

        VideoMessage videoMessage = sourceVideoMessage;
        if(videoMessage == null){
            videoMessage = new VideoMessage();
            videoMessage.url = videoUrl;
            videoMessage.body = body;
            Room.fillVideoInfo(context, videoMessage, uri, "video/mp4", thumbUri, "image/jpeg");
        }

        InputStream imageStream = null;
        String filename = "";
        String uploadId = "";
        String mimeType = "";

        try{
            if (videoMessage.isThumbnailLocalContent()) {
                uploadId = thumbnailUrl;
                imageStream = new FileInputStream(new File(thumbUri.getPath()));
                mimeType = thumbnailMimeType;
            }else{
                uploadId = videoUrl;
                imageStream = new FileInputStream(new File(uri.getPath()));
                filename = videoMessage.body;
                mimeType = videoMimeType;
            }
        }catch (Exception e) {
            Log.e(LOG_TAG, "uploadVideoContent : media parsing failed " + e.getLocalizedMessage());
        }

        final boolean isContentUpload = TextUtils.equals(uploadId, videoUrl);
        final VideoMessage finalVideoMessage = videoMessage;

        session.getMediasCache().uploadContent(imageStream, filename, mimeType, uploadId, new MXMediaUploadListener(){
            @Override
            public void onUploadStart(String uploadId) {
            }

            @Override
            public void onUploadCancel(String uploadId) {
            }

            @Override
            public void onUploadError(final String uploadId, final int serverResponseCode, final String serverErrorMessage) {
                delLocalMedia(context, videoUrl.replace("file://", ""));
            }

            @Override
            public void onUploadComplete(final String uploadId, final String contentUri) {
                if (isContentUpload) {
                    finalVideoMessage.url = contentUri;

                    Event newEvent = new Event(finalVideoMessage, session.getCredentials().userId, room.getRoomId());
                    room.storeOutgoingEvent(newEvent);
                    newEvent.mSentState = Event.SentState.SENDING;
                    newEvent.updateContent(JsonUtils.toJson(finalVideoMessage));
                    roomSendEvent(room, newEvent);
	                delLocalMedia(context, videoUrl.replace("file://", ""));
                } else {
                    finalVideoMessage.info.thumbnail_url = contentUri;
                    Matrix.getInstance(context).getMediasCache().saveFileMediaForUrl(contentUri, thumbnailUrl, -1, -1, thumbnailMimeType, true);

                    videoUploader(context, session, room, finalVideoMessage, thumbnailUrl, thumbnailMimeType, videoUrl, finalVideoMessage.body, videoMimeType);
                }

            }
        });

    }
}
