/*
 * Copyright 2014 OpenMarket Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.util;

import android.content.Context;

import org.matrix.androidsdk.call.IMXCall;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import im.vector.R;

/**
 * This class contains the call toolbox.
 */
public class CallUtilities {
    //
    private static SimpleDateFormat mHourMinSecFormat = null;
    private static SimpleDateFormat mMinSecFormat = null;

    /**
     * Format a time in seconds to a HH:MM:SS string.
     *
     * @param seconds the time in seconds
     * @return the formatted time
     */
    private static String formatSecondsToHMS(long seconds) {
        if (null == mHourMinSecFormat) {
            mHourMinSecFormat = new SimpleDateFormat("HH:mm:ss");
            mHourMinSecFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            mMinSecFormat = new SimpleDateFormat("mm:ss");
            mMinSecFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        }

        if (seconds < 3600) {
            return mMinSecFormat.format(new Date(seconds * 1000));
        } else {
            return mHourMinSecFormat.format(new Date(seconds * 1000));
        }
    }

    /**
     * Return a user friendly message for the given error
     *
     * @param context
     * @param error
     * @return user friendly error
     */
    public static String getUserFriendlyError(final Context context, final String error) {
        String userFriendlyError = error;
        if (error != null) {
            switch (error) {
                case IMXCall.CALL_ERROR_USER_NOT_RESPONDING:
                    userFriendlyError = context.getString(R.string.call_error_user_not_responding);
                    break;
                case IMXCall.CALL_ERROR_ICE_FAILED:
                    userFriendlyError = context.getString(R.string.call_error_ice_failed);
                    break;
                case IMXCall.CALL_ERROR_CAMERA_INIT_FAILED:
                    userFriendlyError = context.getString(R.string.call_error_camera_init_failed);
                    break;
            }
        }
        return userFriendlyError;
    }

    /**
     * Return the call status.
     *
     * @param context
     * @param call    the dedicated call
     * @return the call status.
     */
    public static String getCallStatus(final Context context, final IMXCall call) {
        // sanity check
        if (null == call) {
            return null;
        }

        final String callState = call.getCallState();

        String callStatus = null;
        switch (callState) {
            case IMXCall.CALL_STATE_RINGING:
                if (call.isIncoming()) {
                    callStatus = context.getString(call.isVideo() ? R.string.incoming_video_call : R.string.incoming_voice_call);
                } else {
                    callStatus = context.getString(R.string.call_ring);
                }
                break;
            case IMXCall.CALL_STATE_CREATING_CALL_VIEW:
            case IMXCall.CALL_STATE_FLEDGLING:
            case IMXCall.CALL_STATE_WAIT_LOCAL_MEDIA:
            case IMXCall.CALL_STATE_WAIT_CREATE_OFFER:
            case IMXCall.CALL_STATE_CREATE_ANSWER:
            case IMXCall.CALL_STATE_INVITE_SENT:
                callStatus = context.getString(call.isIncoming() ? R.string.call_connecting : R.string.call_ring);
                break;
            case IMXCall.CALL_STATE_CONNECTING:
                callStatus = context.getString(R.string.call_connecting);
                break;
            case IMXCall.CALL_STATE_CONNECTED:
                final long elapsedTime = call.getCallElapsedTime();
                if (elapsedTime < 0) {
                    callStatus = context.getString(R.string.call_connected);
                } else {
                    callStatus = formatSecondsToHMS(elapsedTime);
                }
                break;
            case IMXCall.CALL_STATE_ENDED:
                callStatus = context.getString(R.string.call_ended);
                break;
        }

        return callStatus;
    }

}
