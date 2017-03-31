package com.windsing.common;

/**
 * Created by wangjha on 2017/3/30.
 */

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class SFTPUtil {
    private static final String TAG = "SFTPUtil";
    public static int SUCC = 0;
    public static int FAIL = -1;

    //连接sftp服务器
    public static ChannelSftp connect(String host, int port, String username, String password) {
        ChannelSftp sftp = null;
        try {
            JSch jsch = new JSch();
            jsch.getSession(username, host, port);
            Session session = jsch.getSession(username, host, port);
            session.setPassword(password);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            Channel channel = session.openChannel("sftp");
            channel.connect();
            sftp = (ChannelSftp) channel;
            Log.d(TAG, "Connected to " + host + ".");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sftp;
    }

    //上传文件
    public static String upload(ChannelSftp sftp, String directory, String uploadFile) {
        try {
            sftp.cd(directory);
            File file = new File(uploadFile);
            sftp.put(new FileInputStream(file), file.getName());
            Log.d(TAG, "Upload succ. " + uploadFile);
            return "SUCC";
        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

    //下载文件
    public static int download(ChannelSftp sftp, String directory, String downloadFile, String saveFile) {
        try {
            sftp.cd(directory);
            File file = new File(saveFile);
            sftp.get(downloadFile, new FileOutputStream(file));
            Log.d(TAG, "Download succ. " + downloadFile);
            return SUCC;
        } catch (Exception e) {
            e.printStackTrace();
            return FAIL;
        }
    }

    //删除文件
    public static int delete(ChannelSftp sftp, String directory, String deleteFile) {
        try {
            sftp.cd(directory);
            sftp.rm(deleteFile);
            Log.d(TAG, "Delete succ. " + deleteFile);
            return SUCC;
        } catch (Exception e) {
            e.printStackTrace();
            return FAIL;
        }
    }

    //列出目录下的文件
//    public static Vector<!--?--> listFiles(ChannelSftp sftp, String directory) throws SftpException {
//        //Vector容器内部保存的是LsEntry类型对象。
//        return sftp.ls(directory);
//    }

}
