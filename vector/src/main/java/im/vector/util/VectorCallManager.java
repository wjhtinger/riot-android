package im.vector.util;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorCallViewActivity;
import im.vector.activity.VectorHomeActivity;
import im.vector.services.EventStreamService;

public class VectorCallManager implements MXCallsManager.MXCallsManagerListener, IMXCall.MXCallListener {
    private static final String LOG_TAG = VectorCallManager.class.getSimpleName();

    private static VectorCallManager sInstance;

    private List<MXCallsManager> mMxCallsManagerList;

    // TODO replace by list of call when multi session
    private IMXCall mCall;

    // True when incoming call (mCall might be null in onCallEnd so we cannot check if it is incoming using the object)
    private boolean mIsIncoming;
    // True when the incoming/outgoing call is not answered yet by the callee
    private boolean mIsAwaitingAnswer;

    /*
     * *********************************************************************************************
     * Singleton
     * *********************************************************************************************
     */

    private VectorCallManager() {
        Log.d(LOG_TAG, "Create VectorCallManager instance ");
        mMxCallsManagerList = new ArrayList<>();
    }

    public static VectorCallManager getInstance() {
        if (sInstance == null) {
            sInstance = new VectorCallManager();
        }
        return sInstance;
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    public void addMXCallsManager(final MXCallsManager callsManager) {
        if (callsManager != null && !mMxCallsManagerList.contains(callsManager)) {
            callsManager.addListener(this);
            mMxCallsManagerList.add(callsManager);
            Log.e(LOG_TAG, "Added MXCallsManager " + mMxCallsManagerList.size());
        }
    }

    public void removeMXCallsManager(final MXCallsManager callsManager) {
        for (Iterator<MXCallsManager> iterator = mMxCallsManagerList.listIterator(); iterator.hasNext(); ) {
            MXCallsManager mxCallsManager = iterator.next();
            if (mxCallsManager == callsManager) {
                iterator.remove();
                Log.e(LOG_TAG, "Removed MXCallsManager");
            }
        }
    }

    public void setCurrentCall(final IMXCall call) {
        if (call != null) {
            mCall = call;
            mIsIncoming = call.isIncoming();
            Log.e(LOG_TAG, "setCurrentCall " + call.getCallId() + " state:" + call.getCallState());
            call.addListener(this);
        }
    }

    /**
     * Provide the current call
     *
     * @return current call
     */
    public IMXCall getCall() {
        return mCall;
    }

    public boolean hasIncomingCall() {
        return mCall != null && mCall.isIncoming();
    }

    /**
     * Check whether the current call is still valid
     *
     * @return true if valid
     */
    public boolean isValidCall() {
        final boolean isValid = mCall.getSession().mCallsManager.getCallWithCallId(mCall.getCallId()) != null;
        if (!isValid) {
            mCall.removeListener(this);
            mCall = null;
        }
        return isValid;
    }

    /**
     * @return true if the call can be resumed.
     * i.e this callView can be closed to be re opened later.
     */
    public boolean canCallBeResumed() {
        if (null != mCall) {
            final String state = mCall.getCallState();

            // active call must be
            return
                    (state.equals(IMXCall.CALL_STATE_RINGING) && !mCall.isIncoming()) ||
                            state.equals(IMXCall.CALL_STATE_CONNECTING) ||
                            state.equals(IMXCall.CALL_STATE_CONNECTED) ||
                            state.equals(IMXCall.CALL_STATE_CREATE_ANSWER);
        }

        return false;
    }

    /**
     * Manage hangup event.
     * The ringing sound is disabled and pending incoming call is dismissed.
     */
    public void hangUp() {
        if (mCall != null) {
            Log.d(LOG_TAG, "hangUp : hide call notification and stopRinging for call " + mCall.getCallId());
            mCall.hangup(null);
            EventStreamService.getInstance().hideCallNotifications();
            VectorCallSoundManager.stopRinging();
        }
    }

    /**
     * Check if there is an incoming call which needs to be answered
     *
     * @return true if there is an incoming call which needs to be answered
     */
    public boolean hasPendingIncomingCall() {
        if (!hasIncomingCall()) {
            return false;
        }

        final String callState = mCall.getCallState();
        return TextUtils.equals(callState, IMXCall.CALL_STATE_CREATED) || TextUtils.equals(callState, IMXCall.CALL_STATE_RINGING);
    }

    /**
     * Check whether an answer is expected for the current call
     *
     * @return true if the callee did not accept yet
     */
    public boolean isWaitingUserResponse() {
        if (mCall == null) {
            return false;
        }

        final String callState = mCall.getCallState();
        if (hasIncomingCall()) {
            return TextUtils.equals(mCall.getCallState(), IMXCall.CALL_STATE_CREATED) ||
                    TextUtils.equals(callState, IMXCall.CALL_STATE_RINGING);
        } else {
            return TextUtils.equals(callState, IMXCall.CALL_STATE_CREATING_CALL_VIEW) ||
                    TextUtils.equals(callState, IMXCall.CALL_STATE_FLEDGLING) ||
                    TextUtils.equals(callState, IMXCall.CALL_STATE_WAIT_LOCAL_MEDIA) ||
                    TextUtils.equals(callState, IMXCall.CALL_STATE_WAIT_CREATE_OFFER) ||
                    TextUtils.equals(callState, IMXCall.CALL_STATE_INVITE_SENT) ||
                    TextUtils.equals(callState, IMXCall.CALL_STATE_RINGING);
        }
    }

    /**
     * Start a call for the given session call manager in the given room
     *
     * @param callsManager
     * @param roomId       roomId in which the call should be made
     * @param withVideo    whether it is audio only or video
     * @param callback
     */
    public void startCall(final MXCallsManager callsManager, final String roomId,
                          final boolean withVideo, final OnStartCallListener callback) {
        // Make sure it is registered
        addMXCallsManager(callsManager);
        // Create the call object
        callsManager.createCallInRoom(roomId, new ApiCallback<IMXCall>() {
            @Override
            public void onSuccess(final IMXCall call) {
                Log.d(LOG_TAG, "## startIpCall(): onSuccess");
                call.setIsVideo(withVideo);
                call.setIsIncoming(false);
                setCurrentCall(call);

                callback.onStartCallSuccess(call);
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## startIpCall(): onNetworkError Msg=" + e.getMessage());
                callback.onStartCallFailed(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## startIpCall(): onMatrixError Msg=" + e.getLocalizedMessage());
                callback.onStartCallFailed(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## startIpCall(): onUnexpectedError Msg=" + e.getLocalizedMessage());
                callback.onStartCallFailed(e.getLocalizedMessage());
            }
        });
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /**
     * Clear current call data so a new call can be taken later
     */
    private void clearCall() {
        VectorCallSoundManager.releaseAudioFocus();
        EventStreamService.getInstance().hideCallNotifications();
        if (mCall != null) {
            mCall.removeListener(this);
            mCall = null;
        }
        mIsAwaitingAnswer = false;
        mIsIncoming = false;
    }

    /**
     * Handle the incoming call and display the appropriate screen to the user
     *
     * @param call
     */
    private void handleIncomingCall(final IMXCall call) {
        setCurrentCall(call);
        mIsAwaitingAnswer = true;

        // Open incoming call screen
        VectorHomeActivity homeActivity = VectorHomeActivity.getInstance();
        if (null == homeActivity) {
            // if the home activity does not exist : the application has been woken up by a notification)
            Log.d(LOG_TAG, "onIncomingCall : the home activity does not exist -> launch it");

            // clear the activity stack to home activity

            final Context context = VectorApp.getInstance();
            Intent intent = new Intent(context, VectorHomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(VectorHomeActivity.EXTRA_CALL_SESSION_ID, call.getSession().getMyUserId());
            intent.putExtra(VectorHomeActivity.EXTRA_CALL_ID, call.getCallId());
            context.startActivity(intent);
        } else {
            Log.d(LOG_TAG, "onIncomingCall : the home activity exists : but permissions have to be checked before");
            // check incoming call required permissions, before allowing the call..
            homeActivity.startCall(call.getSession().getMyUserId(), call.getCallId());
        }
    }

    /*
     * *********************************************************************************************
     * CallManager listener
     * *********************************************************************************************
     */

    @Override
    public void onIncomingCall(IMXCall call) {
        if (call != null) {
            Log.e(LOG_TAG, "onIncomingCall for " + call.getSession().getMyUserId());
            if (mCall != null) {
                // Already in call, directly hang up the new call
                call.hangup(null);
            } else {
                // Call can be taken
                handleIncomingCall(call);
            }
        }
    }

    @Override
    public void onCallHangUp(final IMXCall call) {
        Log.e(LOG_TAG, "onCallHangUp " + call.getCallState());

        final VectorHomeActivity homeActivity = VectorHomeActivity.getInstance();
        if (null != homeActivity) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, "onCallHangUp : onCallHangUp");
                    homeActivity.onCallEnd();
                }
            });
        }
    }

    @Override
    public void onVoipConferenceStarted(String roomId) {
        Log.e(LOG_TAG, "onVoipConferenceStarted for roomId " + roomId);
    }

    @Override
    public void onVoipConferenceFinished(String roomId) {
        Log.e(LOG_TAG, "onVoipConferenceFinished for roomId " + roomId);
    }

    /*
     * *********************************************************************************************
     * Call listener
     * *********************************************************************************************
     */

    @Override
    public void onStateDidChange(String callState) {
        Log.e(LOG_TAG, "onStateDidChange state " + callState);

        switch (callState) {
            case IMXCall.CALL_STATE_RINGING:
                mIsAwaitingAnswer = true;
                if (!mCall.isIncoming()) {
                    VectorCallSoundManager.startRingBackSound(mCall.isVideo());
                }
                // Ringing for incoming call is managed in EventStreamService
                break;
            case IMXCall.CALL_STATE_CREATING_CALL_VIEW:
            case IMXCall.CALL_STATE_CREATE_ANSWER:
                if (mCall.isIncoming()) {
                    VectorCallSoundManager.stopRinging();
                    EventStreamService.getInstance().hideCallNotifications();
                }
                break;
            case IMXCall.CALL_STATE_CONNECTED:
                mIsAwaitingAnswer = false;
                VectorCallSoundManager.stopRinging();
                VectorCallSoundManager.requestAudioFocus();
                if (null != VectorCallViewActivity.getInstance()) {
                    VectorCallViewActivity.getInstance().refreshSpeakerButton();
                }
                break;
        }
    }

    @Override
    public void onCallError(String error) {
        Log.e(LOG_TAG, "onCallError " + error);
        VectorCallSoundManager.stopRinging();

        switch (error) {
            case IMXCall.CALL_ERROR_USER_NOT_RESPONDING:
                VectorCallSoundManager.startBusyCallSound();
                break;
            case IMXCall.CALL_ERROR_ICE_FAILED:
            case IMXCall.CALL_ERROR_CAMERA_INIT_FAILED:
            default:
                VectorCallSoundManager.restoreAudioConfig();
                break;
        }

        final String errorMsg = CallUtilities.getUserFriendlyError(VectorApp.getInstance(), error);
        CommonActivityUtils.displayToastOnUiThread(VectorApp.getCurrentActivity(), errorMsg);
        clearCall();
    }

    @Override
    public void onViewLoading(View callView) {
        Log.e(LOG_TAG, "onViewLoading");
    }

    @Override
    public void onViewReady() {
        Log.e(LOG_TAG, "onViewReady");
    }

    @Override
    public void onCallAnsweredElsewhere() {
        Log.e(LOG_TAG, "onCallAnsweredElsewhere");

        VectorCallSoundManager.stopRinging();
        VectorCallSoundManager.restoreAudioConfig();
        CommonActivityUtils.displayToastOnUiThread(VectorApp.getCurrentActivity(),
                VectorApp.getInstance().getString(R.string.call_error_answered_elsewhere));
        clearCall();
    }

    @Override
    public void onCallEnd(int aReasonId) {
        VectorCallSoundManager.stopRinging();

        switch (aReasonId) {
            case IMXCall.END_CALL_REASON_PEER_HANG_UP:
                Log.e(LOG_TAG, "onCallEnd: END_CALL_REASON_PEER_HANG_UP incoming:" + mIsIncoming + " awaitingAnswer:" + mIsAwaitingAnswer);
                if (mIsAwaitingAnswer) {
                    if (mIsIncoming) {
                        // Caller cancelled his call before we took it
                        VectorCallSoundManager.restoreAudioConfig();
                        CommonActivityUtils.displayToastOnUiThread(VectorApp.getCurrentActivity(),
                                VectorApp.getInstance().getString(R.string.call_error_peer_cancelled_call));
                    } else {
                        // Callee declined our call
                        VectorCallSoundManager.startBusyCallSound();
                        CommonActivityUtils.displayToastOnUiThread(VectorApp.getCurrentActivity(),
                                VectorApp.getInstance().getString(R.string.call_error_peer_hangup));
                    }
                } else {
                    // Peers have been connected but the peer hung up
                    VectorCallSoundManager.startEndCallSound();
                    CommonActivityUtils.displayToastOnUiThread(VectorApp.getCurrentActivity(),
                            VectorApp.getInstance().getString(R.string.call_error_peer_hangup));
                }
                break;
            case IMXCall.END_CALL_REASON_PEER_HANG_UP_ELSEWHERE:
                Log.e(LOG_TAG, "onCallEnd: END_CALL_REASON_PEER_HANG_UP_ELSEWHERE");
                VectorCallSoundManager.restoreAudioConfig();
                CommonActivityUtils.displayToastOnUiThread(VectorApp.getCurrentActivity(),
                        VectorApp.getInstance().getString(R.string.call_error_peer_hangup_elsewhere));
                break;
            case IMXCall.END_CALL_REASON_USER_HIMSELF:
                Log.e(LOG_TAG, "onCallEnd: END_CALL_REASON_USER_HIMSELF");
                if (!mIsAwaitingAnswer || !mIsIncoming) {
                    VectorCallSoundManager.startEndCallSound();
                } else {
                    VectorCallSoundManager.restoreAudioConfig();
                }
                break;
            case IMXCall.END_CALL_REASON_UNDEFINED:
            default:
                Log.e(LOG_TAG, "onCallEnd: END_CALL_REASON_UNDEFINED");
                VectorCallSoundManager.startEndCallSound();
                break;
        }

        clearCall();
    }

    @Override
    public void onPreviewSizeChanged(int width, int height) {
        Log.e(LOG_TAG, "onPreviewSizeChanged");
    }

    /*
     * *********************************************************************************************
     * Inner classes
     * *********************************************************************************************
     */

    public interface OnStartCallListener {
        void onStartCallSuccess(final IMXCall call);

        void onStartCallFailed(final String errorMessage);
    }
}
