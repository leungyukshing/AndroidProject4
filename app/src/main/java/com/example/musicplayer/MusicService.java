package com.example.musicplayer;

import android.app.Service;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

public class MusicService extends Service {
    public static MediaPlayer mediaPlayer;
    public static MediaMetadataRetriever mediaMetadataRetriever;
    public MusicService() {}

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new myBinder();
    }

    @Override
    public void onCreate() {
        System.out.println("Create Service");
        super.onCreate();
        if (mediaPlayer == null) {
            try {
                // 默认导入山高水长
                System.out.println("Load Music");
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource("/data/data/com.example.musicplayer/cache/data/山高水长.mp3");
                mediaPlayer.prepare();
                mediaPlayer.setLooping(false);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        // 用于获取歌曲信息
        mediaMetadataRetriever = new MediaMetadataRetriever();
        mediaMetadataRetriever.setDataSource("/data/data/com.example.musicplayer/cache/data/山高水长.mp3");

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                System.out.println("Play Finish!");
                mediaPlayer.pause();
                mediaPlayer.seekTo(0);
                EventBus.getDefault().post(new MessageEvent(true));
            }
        });
    }

    // 根据请求码相应用户请求
    public class myBinder extends Binder {
        @Override
        protected boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case 101:
                    // 开始、暂停播放
                    System.out.println(mediaPlayer.isPlaying());
                    if(mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                    }
                    else {
                        mediaPlayer.start();
                    }
                    break;
                case 102:
                    // 停止重置
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                    }
                    mediaPlayer.seekTo(0);
                    break;
                case 103:
                    // 退出服务
                    mediaPlayer.stop();
                    mediaPlayer.release();
                    mediaMetadataRetriever.release();
                    break;
                case 104:
                    // 返回播放进度
                    reply.writeInt(mediaPlayer.getCurrentPosition());
                    break;
                case 105:
                    // 根据用户调节的滑动条设置播放进度
                    mediaPlayer.seekTo(data.readInt());
                    break;
                case 106:
                    // 返回歌曲信息
                    System.out.println("Get Info");
                    Bundle bundle = new Bundle();
                    if (mediaMetadataRetriever.getEmbeddedPicture() == null) {
                        System.out.println("No Photo");
                    }
                    bundle.putByteArray("photo", mediaMetadataRetriever.getEmbeddedPicture());
                    bundle.putInt("time", mediaPlayer.getDuration());
                    bundle.putString("title", mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
                    bundle.putString("artist", mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST));
                    reply.writeBundle(bundle);
                    break;
                case 107:
                    try {
                        mediaPlayer.reset();
                        String s = data.readString();
                        System.out.println(s);
                        mediaPlayer.setDataSource(s);
                        mediaPlayer.prepare();
                        // 重新设置路径
                        mediaMetadataRetriever.setDataSource(s);
                        // 返回歌曲信息
                        Bundle b = new Bundle();
                        // 获取唱片封面
                        if (mediaMetadataRetriever.getEmbeddedPicture() == null) {
                            System.out.println("No Photo");
                        }
                        b.putByteArray("photo", mediaMetadataRetriever.getEmbeddedPicture());
                        b.putInt("time", mediaPlayer.getDuration());
                        b.putString("title", mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
                        b.putString("artist", mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST));
                        reply.writeBundle(b);

                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case 108:
                    if(mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                    }
                    break;
                    default:break;
            }
            return super.onTransact(code, data, reply, flags);
        }
    };

}
