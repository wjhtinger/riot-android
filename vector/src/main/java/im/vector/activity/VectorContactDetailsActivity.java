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
package im.vector.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.Log;

import java.util.HashMap;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnLongClick;
import im.vector.R;
import im.vector.contacts.Contact;
import im.vector.util.VectorUtils;

/**
 * VectorContactDetailsActivity displays the contact information and allows to perform some dedicated actions.
 */
public class VectorContactDetailsActivity extends MXCActionBarActivity {
    private static final String LOG_TAG = VectorContactDetailsActivity.class.getSimpleName();

    private static final String AVATAR_FULLSCREEN_MODE = "AVATAR_FULLSCREEN_MODE";

    public static final String EXTRA_CONTACT = "EXTRA_CONTACT";

    // internal info
    private MXSession mSession;
    private Contact mContact;
    private String mMemberId;

    // UI widgets
    @BindView(R.id.avatar_img)
    ImageView mContactAvatar;

    @BindView(R.id.contact_details_name)
    TextView mContactName;

    // Full screen avatar
    @BindView(R.id.fullscreen_avatar_view)
    View mFullContactAvatarLayout;

    @BindView(R.id.fullscreen_avatar_image_view)
    ImageView mFullContactAvatarImageView;

    @BindView(R.id.progress_bar)
    View mProgressBar;

     /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    public static void start(final Context context, final Contact contact) {
        final Intent intent = new Intent(context, VectorContactDetailsActivity.class);
        intent.putExtra(EXTRA_CONTACT, contact);
        context.startActivity(intent);
    }

    /*
     * *********************************************************************************************
     * Activity lifecycle
     * *********************************************************************************************
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "Restart the application");
            CommonActivityUtils.restartApp(this);
            return;
        }

        if (CommonActivityUtils.isGoingToSplash(this)) {
            Log.d(LOG_TAG, "onCreate : Going to splash screen");
            return;
        }

        // retrieve the parameters contained extras and setup other
        // internal state values such as the; session, room..
        if (!initContextStateValues()) {
            // init failed, just return
            Log.e(LOG_TAG, "## onCreate(): Parameters init failure");
            finish();
        } else {
            // setup UI view and bind the widgets
            setContentView(R.layout.activity_contact_details);
            ButterKnife.bind(this);

            // use a toolbar instead of the actionbar
            // to be able to display a large header
            android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.contact_details_toolbar);
            this.setSupportActionBar(toolbar);

            if (null != getSupportActionBar()) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                // do not display the activity name in the action bar
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }

            // TODO algo to determine that value (email, phone, from intent, etc.)
            mMemberId = mContact.getEmails().get(0);

            // update the UI
            updateUi();

            if (null != savedInstanceState && savedInstanceState.getBoolean(AVATAR_FULLSCREEN_MODE, false)) {
                displayFullScreenAvatar();
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(AVATAR_FULLSCREEN_MODE, View.VISIBLE == mFullContactAvatarLayout.getVisibility());
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        if (savedInstanceState.getBoolean(AVATAR_FULLSCREEN_MODE, false)) {
            displayFullScreenAvatar();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (KeyEvent.KEYCODE_BACK == keyCode && View.VISIBLE == mFullContactAvatarLayout.getVisibility()) {
            hideFullScreenAvatar();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    private void updateUi() {
        if (!mSession.isAlive()) {
            Log.e(LOG_TAG, "updateUi : the session is not anymore valid");
            return;
        }

        mContactName.setText(mMemberId);

        // UI updates
        final Bitmap avatar = mContact.getThumbnail(this);
        if (avatar != null) {
            mContactAvatar.setImageBitmap(avatar);
        } else {
            VectorUtils.loadUserAvatar(this, mSession, mContactAvatar, null, mMemberId, mMemberId);
        }
    }

    private void displayProgressBar(final boolean mustBeShown) {
        mProgressBar.setVisibility(mustBeShown ? View.VISIBLE : View.GONE);
    }

    /*
     * *********************************************************************************************
     * User actions management
     * *********************************************************************************************
     */

    @OnClick(R.id.avatar_img)
    public void displayFullScreenAvatar() {
        if (mFullContactAvatarImageView.getDrawable() == null) {
            // Load avatar
            final Bitmap avatar = mContact.getAvatar(this);
            if (avatar != null) {
                mFullContactAvatarImageView.setImageBitmap(avatar);
                mFullContactAvatarLayout.setVisibility(View.VISIBLE);
            }
        } else {
            mFullContactAvatarLayout.setVisibility(View.VISIBLE);
        }
    }

    @OnClick(R.id.fullscreen_avatar_view)
    public void hideFullScreenAvatar() {
        mFullContactAvatarLayout.setVisibility(View.GONE);
    }

    @OnLongClick(R.id.contact_details_name)
    public boolean onUsernameClick() {
        VectorUtils.copyToClipboard(VectorContactDetailsActivity.this, mContactName.getText());
        return true;
    }

    @OnClick(R.id.start_new_chat_action)
    public void onStartChatClick() {
        inviteContact();
    }

    /*
     * *********************************************************************************************
     * Utils
     * *********************************************************************************************
     */

    /**
     * Retrieve all the state values required to run the activity.
     * If values are not provided in the intent or are some are
     * null, then the activity can not continue to run and must be finished
     *
     * @return true if init succeed, false otherwise
     */
    private boolean initContextStateValues() {
        Intent intent = getIntent();
        boolean isParamInitSucceed = false;

        if (null != intent) {
            mContact = (Contact) intent.getSerializableExtra(EXTRA_CONTACT);
            isParamInitSucceed = mContact != null;
        }

        if (null == (mSession = getSession(this, intent))) {
            isParamInitSucceed = false;
        }

        return isParamInitSucceed;
    }

    private void inviteContact() {
        Log.d(LOG_TAG, "## performItemAction(): Start new room - start chat");
        displayProgressBar(true);
        mSession.createRoomDirectMessage(mMemberId, mCreateDirectMessageCallBack);
    }

    /*
     * *********************************************************************************************
     * Listeners
     * *********************************************************************************************
     */

    // direct message
    /**
     * callback for the creation of the direct message room
     **/
    private final ApiCallback<String> mCreateDirectMessageCallBack = new ApiCallback<String>() {
        @Override
        public void onSuccess(String roomId) {
            HashMap<String, Object> params = new HashMap<>();
            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
            params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
            params.put(VectorRoomActivity.EXTRA_EXPAND_ROOM_HEADER, true);

            Log.d(LOG_TAG, "## mCreateDirectMessageCallBack: onSuccess - start goToRoomPage");
            CommonActivityUtils.goToRoomPage(VectorContactDetailsActivity.this, mSession, params);
            displayProgressBar(false);
        }

        @Override
        public void onMatrixError(MatrixError e) {
            Log.d(LOG_TAG, "## mCreateDirectMessageCallBack: onMatrixError Msg=" + e.getLocalizedMessage());
            displayProgressBar(false);
        }

        @Override
        public void onNetworkError(Exception e) {
            Log.d(LOG_TAG, "## mCreateDirectMessageCallBack: onNetworkError Msg=" + e.getLocalizedMessage());
            displayProgressBar(false);
        }

        @Override
        public void onUnexpectedError(Exception e) {
            Log.d(LOG_TAG, "## mCreateDirectMessageCallBack: onUnexpectedError Msg=" + e.getLocalizedMessage());
            displayProgressBar(false);
        }
    };

}
