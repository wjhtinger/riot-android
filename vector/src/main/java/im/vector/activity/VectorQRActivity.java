/* 
 * Copyright 2016 OpenMarket Ltd
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

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.vondear.rxtools.view.RxBarCode;
import com.vondear.rxtools.view.RxQRCode;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.util.Log;

import im.vector.Matrix;
import im.vector.R;
import im.vector.fragments.VectorSettingsPreferencesFragment;
import im.vector.util.VectorUtils;

/**
 * Displays the client settings.
 */
public class VectorQRActivity extends MXCActionBarActivity {
    // session
    private MXSession mSession;
    private ImageView mIvQrCode;
    // the UI items

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mSession = getSession(this, intent);

        if (null == mSession) {
            mSession = Matrix.getInstance(VectorQRActivity.this).getDefaultSession();
        }

        if (mSession == null) {
            finish();
            return;
        }

        String userID = mSession.getMyUserId();
        setContentView(R.layout.activity_qr_settings);

        ImageView mainAvatarView = (ImageView)findViewById(R.id.iv_qr_main_avatar);
        if (null != mainAvatarView) {
            VectorUtils.loadUserAvatar(this, mSession, mainAvatarView, mSession.getMyUser());
            mainAvatarView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final Intent settingsIntent = new Intent(VectorQRActivity.this, VectorSettingsActivity.class);
                    settingsIntent.putExtra(MXCActionBarActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                    VectorQRActivity.this.startActivity(settingsIntent);
                }
            });
        }
        TextView displayNameTextView = (TextView) findViewById(R.id.iv_qr_main_displayname);
        if (null != displayNameTextView) {
            displayNameTextView.setText(mSession.getMyUser().displayname);
        }


        mIvQrCode = (ImageView) findViewById(R.id.iv_qr_code);

        RxQRCode.builder(userID).
                backColor(getResources().getColor(R.color.white)).
                codeColor(getResources().getColor(android.R.color.black)).
                codeSide(600).
                into(mIvQrCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // pass the result to the fragment
    }

    @Override
    public void onRequestPermissionsResult(int aRequestCode,@NonNull String[] aPermissions, @NonNull int[] aGrantResults) {
        if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_TAKE_PHOTO) {

        }
    }
}