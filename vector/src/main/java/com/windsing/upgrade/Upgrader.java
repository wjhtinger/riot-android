package com.windsing.upgrade;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Vector;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.SplashActivity;

import com.lidroid.xutils.HttpUtils;
import com.lidroid.xutils.exception.HttpException;
import com.lidroid.xutils.http.ResponseInfo;
import com.lidroid.xutils.http.callback.RequestCallBack;

/**
 * Created by New on 2017/2/27.
 */

public class Upgrader {
    private static final String LOG_TAG = "MediaUploader";
    private Context mContext;

    private AlertDialog mUpgradeDialog;
    private ProgressBar mProgressBar;
    private TextView mProgressText;
    private CheckBox mnoNotifyCheck;
    private View mDialogView;

    private String mVersionName;  // 版本名
    private int mVersionCode;     // 版本号
    private String mDesc;         // 版本描述
    private String mDownloadUrl;  // 下载地址
    private HttpUtils mUtils;


    private void dialogFieldSet(DialogInterface dialog, boolean en){
        try {
            Field field = dialog.getClass().getSuperclass().getDeclaredField("mShowing");
            field.setAccessible(true);
            field.set(mUpgradeDialog, en);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void download() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            mProgressBar = (ProgressBar)mDialogView.findViewById(R.id.upgrade_progressBar);
            mProgressText = (TextView) mDialogView.findViewById(R.id.upgrade_progress_text);
            mnoNotifyCheck = (CheckBox) mDialogView.findViewById(R.id.upgrade_nonotify_check);
            mProgressBar.setVisibility(View.VISIBLE);
            //mProgressText.setVisibility(View.VISIBLE);
            mnoNotifyCheck.setVisibility(View.GONE);

            String target = Environment.getExternalStorageDirectory() + "/update.apk";
            mUtils = new HttpUtils();
            mUtils.download(mDownloadUrl, target, new RequestCallBack<File>() {
                @Override
                public void onLoading(long total, long current,boolean isUploading) {
                    super.onLoading(total, current, isUploading);

                    int percent = (int)(current * 100 / total);
                    mProgressBar.setProgress(percent);
                    //mProgressText.setText(percent + "%");
                    Log.d(LOG_TAG, "Down percent:" + percent);
                }

                @Override
                public void onSuccess(ResponseInfo<File> arg0) {
                    Log.d(LOG_TAG, "升级文件下载成功");
                    //删除升级对话框
                    dialogFieldSet(mUpgradeDialog, true);
                    mUpgradeDialog.dismiss();

                    // 跳转到系统下载页面
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);   //非Activity的context中startActivity()
                    intent.addCategory(Intent.CATEGORY_DEFAULT);
                    intent.setDataAndType(Uri.fromFile(arg0.result), "application/vnd.android.package-archive");
                    mContext.startActivity(intent);
                    //startActivityForResult(intent, 0);   //如果用户取消安装的话,会返回结果,回调方法onActivityResult
                }

                @Override
                public void onFailure(HttpException arg0, String arg1) {
                    Toast.makeText(mContext, mContext.getResources().getString(R.string.upgrade_downlaod_fail) + ":" + arg1,Toast.LENGTH_SHORT).show();
                    dialogFieldSet(mUpgradeDialog, true);
                    mUpgradeDialog.dismiss();
                }
            });
        } else {
            Toast.makeText(mContext, mContext.getResources().getString(R.string.upgrade_nofind_sdcard),  Toast.LENGTH_SHORT).show();
            dialogFieldSet(mUpgradeDialog, true);
            mUpgradeDialog.dismiss();
        }
    }


    private void showUpgradeDialog() {
        Log.d(LOG_TAG, "showUpgradeDialog");
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        mDialogView = View.inflate(mContext, R.layout.dialog_upgrade, null);
        builder.setView(mDialogView);
        builder.setTitle(mContext.getResources().getString(R.string.upgrade_newversion) + mVersionName);
        builder.setMessage(mDesc);
        builder.setPositiveButton(mContext.getResources().getString(R.string.upgrade_yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialogFieldSet(dialog, false);

                mUpgradeDialog.setTitle(mContext.getResources().getString(R.string.upgrade_downlaod_file));
                mUpgradeDialog.setMessage("");
                mUpgradeDialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);
                //mUpgradeDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(View.GONE);
                mUpgradeDialog.setCancelable(false);

                download();
            }
        });

        builder.setNegativeButton(mContext.getResources().getString(R.string.upgrade_No), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mnoNotifyCheck = (CheckBox) mDialogView.findViewById(R.id.upgrade_nonotify_check);
                if(mnoNotifyCheck.isChecked()){

                }

                dialogFieldSet(dialog, true);
                mUpgradeDialog.dismiss();
                //mUtils.release();
            }
        });

        mUpgradeDialog = builder.create();
        mUpgradeDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        mUpgradeDialog.show();
    }


    private int getVersionCode() {
        PackageManager packageManager = mContext.getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(mContext.getPackageName(), 0);// 获取包的信息
            int versionCode = packageInfo.versionCode;
            return versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return -1;
    }

    private String readFromStream(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len = 0;
        byte[] buffer = new byte[1024];

        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }

        String result = out.toString();
        in.close();
        out.close();
        return result;
    }

    private class NullHostNameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            Log.i("RestUtilImpl", "Approving certificate for " + hostname);
            return true;
        }
    }

    public Upgrader(Context context){
        mContext = context;
    }

    public void checkVerson() {

        new Thread() {
            @Override
            public void run() {
                Message msg = Message.obtain();
                HttpURLConnection conn = null;

                TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager(){
                    public X509Certificate[] getAcceptedIssuers(){return null;}
                    public void checkClientTrusted(X509Certificate[] certs, String authType){}
                    public void checkServerTrusted(X509Certificate[] certs, String authType){}
                }};

                try {
                    HttpsURLConnection.setDefaultHostnameVerifier(new NullHostNameVerifier());
                    SSLContext sc = SSLContext.getInstance("TLS");
                    sc.init(null, trustAllCerts, new SecureRandom());
                    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

                    // 本机地址用localhost, 但是如果用模拟器加载本机的地址时,可以用ip(10.0.2.2)来替换
                    URL url = new URL(mContext.getResources().getString(R.string.upgrade_url));
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");// 设置请求方法
                    conn.setConnectTimeout(5000);// 设置连接超时
                    conn.setReadTimeout(5000);// 设置响应超时, 连接上了,但服务器迟迟不给响应
                    conn.connect();// 连接服务器

                    int responseCode = conn.getResponseCode();// 获取响应码
                    Log.e(LOG_TAG, "version check responseCode:"+ responseCode);
                    if (responseCode == 200) {
                        InputStream inputStream = conn.getInputStream();
                        String result = readFromStream(inputStream);

                        // 解析json
                        JSONObject jo = new JSONObject(result);
                        mVersionName = jo.getString("versionName");
                        mVersionCode = jo.getInt("versionCode");
                        mDesc = jo.getString("description");
                        mDownloadUrl = jo.getString("downloadUrl");

                        Log.d(LOG_TAG, String.format("NewVersionCode[%d], OldVersionCode[%d], mDownloadUrl[%s]", mVersionCode, getVersionCode(), mDownloadUrl));
                        if (mVersionCode > getVersionCode()) {
                            Looper.prepare();
                            showUpgradeDialog();
                            Looper.loop();
//                            mContext.runOnUiThread(new Runnable() {
//                                @Override
//                                public void run() {
//                                    showUpgradeDialog();
//                                }
//                            });
                        }
                    }

                } catch (MalformedURLException e) {
                    // url错误的异常
                    Log.d(LOG_TAG, "url exception!");
                    e.printStackTrace();
                } catch (IOException e) {
                    // 网络错误异常
                    Log.d(LOG_TAG, "network exception!");
                    e.printStackTrace();
                } catch (JSONException e) {
                    // json解析失败
                    Log.d(LOG_TAG, "Json read exception!");
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    Log.d(LOG_TAG, "NoSuchAlgorithmException exception!");
                    e.printStackTrace();
                } catch (KeyManagementException e) {
                    Log.d(LOG_TAG, "KeyManagementException exception!");
                    e.printStackTrace();
                } finally {

                    if (conn != null) {
                        conn.disconnect();// 关闭网络连接
                    }
                }
            }
        }.start();
    }
}
