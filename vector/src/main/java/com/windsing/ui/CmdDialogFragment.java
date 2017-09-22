/*
 * Copyright 2015 OpenMarket Ltd
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

package com.windsing.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.provider.MediaStore;
import android.support.v4.app.DialogFragment;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.windsing.DetectManager;

import im.vector.R;

import java.util.ArrayList;
import java.util.Arrays;

import static im.vector.R.string.view;

/**
 * A dialog fragment showing a list of icon + text entry
 */
public class CmdDialogFragment extends DialogFragment {

    private static final String LOG_TAG = "CmdDialogFragment";

    // params
    public static final String ARG_ICONS_LIST_ID = "org.matrix.androidsdk.fragments.CmdDialogFragment.ARG_ICONS_LIST_ID";
    public static final String ARG_TEXTS_LIST_ID = "org.matrix.androidsdk.fragments.CmdDialogFragment.ARG_TEXTS_LIST_ID";
    public static final String ARG_BACKGROUND_COLOR = "org.matrix.androidsdk.fragments.CmdDialogFragment.ARG_BACKGROUND_COLOR";
    public static final String ARG_TEXT_COLOR = "org.matrix.androidsdk.fragments.CmdDialogFragment.ARG_TEXT_COLOR";
    public static final String ARG_DETECT_TYPE = "org.matrix.androidsdk.fragments.CmdDialogFragment.ARG_DETECT_TYPE";

    private static Boolean intervalOncCheck = false;
    private View view;


    /**
     * Interface definition for a callback to be invoked when an item in this
     * AdapterView has been clicked.
     */
    public interface OnItemClickListener {
        /**
         * Callback method to be invoked when an item is clicked.
         * @param dialogFragment the dialog.
         * @param param
         */
        void onItemClick(CmdDialogFragment dialogFragment, int sel, int[] param);
    }

    private ListView mListView;

    private ArrayList<Integer> mIconResourcesList;
    private ArrayList<Integer> mTextResourcesList;
    private Integer mBackgroundColor = null;
    private Integer mTextColor = null;

    private OnItemClickListener mOnItemClickListener;
    private int mDetectType;


    public static CmdDialogFragment newInstance(DetectManager.detectType detectType)  {
        return CmdDialogFragment.newInstance(detectType, null, null);
    }

    public static CmdDialogFragment newInstance(DetectManager.detectType detectType, Integer backgroundColor, Integer textColor)  {
        CmdDialogFragment f = new CmdDialogFragment();
        Bundle args = new Bundle();

        args.putInt(ARG_DETECT_TYPE, detectType.ordinal());


        if (null != backgroundColor) {
            args.putInt(ARG_BACKGROUND_COLOR, backgroundColor);
        }

        if (null != textColor) {
            args.putInt(ARG_TEXT_COLOR, textColor);
        }

        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDetectType = getArguments().getInt(ARG_DETECT_TYPE);

        if (getArguments().containsKey(ARG_BACKGROUND_COLOR)) {
            mBackgroundColor = getArguments().getInt(ARG_BACKGROUND_COLOR);
        }

        if (getArguments().containsKey(ARG_TEXT_COLOR)) {
            mTextColor = getArguments().getInt(ARG_TEXT_COLOR);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        view = getActivity().getLayoutInflater().inflate(R.layout.fragment_dialog_detect, null);
        builder.setView(view);

        builder.setPositiveButton(getResources().getString(R.string.detect_dialog_start), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int[] param = new int[3];

                int radioId = ((RadioGroup)view.findViewById(R.id.radioGroup0)).getCheckedRadioButtonId();
                if(radioId == R.id.radioButton0){
                    param[0] = 0;
                }else if(radioId == R.id.radioButton1){
                    param[0] = 1;
                }

                radioId = ((RadioGroup)view.findViewById(R.id.radioGroup1)).getCheckedRadioButtonId();
                if(radioId == R.id.radioButton2){
                    param[1] = 0;
                }else if(radioId == R.id.radioButton3){
                    param[1] = 1;
                }

                if(intervalOncCheck){
                    param[2] = 0;
                }else{
                    param[2] = Integer.parseInt(((EditText)view.findViewById(R.id.editText)).getText().toString());
                }
                mOnItemClickListener.onItemClick(CmdDialogFragment.this, 0, param);
            }
        });
        builder.setNegativeButton(getResources().getString(R.string.detect_dialog_stop), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int[] param = new int[3];
                int radioId = ((RadioGroup)view.findViewById(R.id.radioGroup0)).getCheckedRadioButtonId();
                if(radioId == R.id.radioButton0){
                    param[0] = 0;
                }else if(radioId == R.id.radioButton1){
                    param[0] = 1;
                }

                mOnItemClickListener.onItemClick(CmdDialogFragment.this, 1, param);
            }
        });

        final EditText editText = (EditText)view.findViewById(R.id.editText);
        CheckBox checkBox = (CheckBox) view.findViewById(R.id.checkBoxOnce);
        checkBox.setChecked(intervalOncCheck);
        editText.setEnabled(!intervalOncCheck);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    editText.setEnabled(false);
                    intervalOncCheck = true;
                }else {
                    editText.setEnabled(true);
                    intervalOncCheck = false;
                }
            }
        });


        switch (mDetectType){
            case 0:
                view.findViewById(R.id.table_row2).setVisibility(View.GONE);
                ((RadioButton)view.findViewById(R.id.radioButton2)).setText("回传图片");
                ((RadioButton)view.findViewById(R.id.radioButton3)).setText("回传视频文件");
                break;
            case 1:
                //view.findViewById(R.id.table_row0).setVisibility(View.GONE);
                view.findViewById(R.id.table_row2).setVisibility(View.GONE);
                ((RadioButton)view.findViewById(R.id.radioButton2)).setText("回传音频文件");
                ((RadioButton)view.findViewById(R.id.radioButton3)).setText("回传视频文件");
                break;
            case 2:
                //view.findViewById(R.id.table_row2).setVisibility(View.GONE);
                ((RadioButton)view.findViewById(R.id.radioButton2)).setText("回传图片");
                ((RadioButton)view.findViewById(R.id.radioButton3)).setText("回传视频文件");
                break;
            case 3:
                view.findViewById(R.id.table_row2).setVisibility(View.GONE);
                ((RadioButton)view.findViewById(R.id.radioButton0)).setChecked(true);
                ((RadioButton)view.findViewById(R.id.radioButton1)).setEnabled(false);  //人脸识别只允许用前摄像头
                ((RadioButton)view.findViewById(R.id.radioButton1)).setChecked(false);
                ((TextView)view.findViewById(R.id.textView1)).setText("识别处理");
                ((RadioButton)view.findViewById(R.id.radioButton2)).setText("发起视频通话");
                ((RadioButton)view.findViewById(R.id.radioButton3)).setText("回传视频文件");
                break;
            default:
                Log.e(LOG_TAG, "onCreateDialog mDetectType error:" + mDetectType);
                break;
        }

        return builder.create();
    }


    /**
     * Register a callback to be invoked when this view is clicked.
     *
     */
    public void setOnClickListener(OnItemClickListener l) {
        mOnItemClickListener = l;
    }

}
