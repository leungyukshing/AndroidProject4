package com.example.musicplayer;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcel;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatSeekBar;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.text.SimpleDateFormat;
import java.util.Calendar;


import de.hdodenhof.circleimageview.CircleImageView;
import rx.Observable;
import rx.Subscriber;

public class MainActivity extends AppCompatActivity {
    private CircleImageView image;
    private AppCompatSeekBar seekBar;
    private TextView playTime;
    private TextView remainTime;
    private ImageView file;
    private ImageView playBtn;
    private ImageView stopBtn;
    private ImageView back;
    private TextView title;
    private TextView artist;

    IBinder mBinder;
    Thread thread;
    boolean isPlay = false;
    boolean isThreadRunning = false;
    // 播放总时间
    int grossTime;
    String songTitle;
    String songArtist;
    String path;
    int currentProgress;
    // 时间格式化工具
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("mm:ss");
    // 自定义四个状态
    private enum STATE {playing, pausing, stopping, waiting};
    private STATE state;

    private ObjectAnimator objectAnimator;

    // 连接后台服务
    ServiceConnection sc = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            System.out.println("Service Conneted!");
            mBinder = iBinder;
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                // 第一次建立服务时需要读取音乐总时长（相关信息）
                mBinder.transact(106, data, reply, 0);
                //grossTime = reply.readInt();
                Bundle receive = reply.readBundle();
                grossTime = receive.getInt("time");
                songTitle = receive.getString("title");
                songArtist = receive.getString("artist");

                // 显示音乐总时长
                remainTime.setText(toFormatStr(grossTime));
                title.setText(songTitle);
                artist.setText(songArtist);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            // 线程启动
            isThreadRunning = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            sc = null;
        }
    };


    // 新建被观察者Observable
    Observable operationObservable =  Observable.create(new rx.Observable.OnSubscribe<Integer>() {
        @Override
        public void call(Subscriber<? super Integer> subscriber) {
            // 向服务端请求播放进度数据
            if (state == STATE.playing) {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    mBinder.transact(104, data, reply, 0);
                    currentProgress = reply.readInt();
                    System.out.println("Time:" + currentProgress);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }

            subscriber.onNext(currentProgress);
            subscriber.onCompleted();
        }
    });



    // 用于控制进度条mHandler，与后台服务交互
    /*
    final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 101:
                    if (state == STATE.playing) {
                        Parcel data = Parcel.obtain();
                        Parcel reply = Parcel.obtain();
                        try {
                            mBinder.transact(104, data, reply, 0);
                            int currentProgress = reply.readInt();
                            playTime.setText(toFormatStr(currentProgress));
                            seekBar.setProgress((int)(currentProgress / (double)grossTime * 100));
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    break;
            }
        }
    };
    */

    @Override
    public void onBackPressed() {
        moveTaskToBack(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 获取页面元素
        image = (CircleImageView)findViewById(R.id.image);
        seekBar = (AppCompatSeekBar)findViewById(R.id.seekBar);
        playTime = (TextView)findViewById(R.id.playTime);
        remainTime = (TextView)findViewById(R.id.remainTime);
        file = (ImageView)findViewById(R.id.file);
        playBtn = (ImageView)findViewById(R.id.playBtn);
        stopBtn = (ImageView)findViewById(R.id.stopBtn);
        back = (ImageView)findViewById(R.id.back);
        title = (TextView)findViewById(R.id.title) ;
        artist = (TextView)findViewById(R.id.artist);

        Intent intent = new Intent(this, MusicService.class);
        startService(intent);
        bindService(intent, sc, BIND_AUTO_CREATE);

        // 播放按钮点击事件
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int requestCode = 101;
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    mBinder.transact(requestCode, data, reply, 0);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                if (isPlay) {
                    setState(STATE.pausing);
                }
                else {
                    setState(STATE.playing);
                }
            }
        });

        // 停止按钮点击事件
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int requestCode = 102;
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    mBinder.transact(requestCode, data, reply, 0);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                setState(STATE.stopping);
            }
        });

        // 退出按钮
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                unbindService(sc);
                try {
                    MainActivity.this.finish();
                    System.exit(0);
                }
                catch (Exception e){
                    e.printStackTrace();
                }
            }
        });

        // 进图条点击事件处理
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            // 记录点击前的状态，用于恢复
            private STATE oldState;
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (b) {
                    int timeAfterChange = (int)(grossTime * (double)(i / 100.0));
                    playTime.setText(toFormatStr(timeAfterChange));

                    Parcel data = Parcel.obtain();
                    Parcel reply = Parcel.obtain();
                    data.writeInt(timeAfterChange);
                    try {
                        mBinder.transact(105, data, reply, 0);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                oldState = state;
                state = STATE.waiting;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                state = oldState;
            }
        });

        // 选择手机音乐
        file.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 选择文件默认暂停
                setState(STATE.pausing);
                int requestCode = 108;
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    mBinder.transact(requestCode, data, reply, 0);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                // 在本地选取音乐文件
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("audio/*"); // 选择音频
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, 1);
            }
        });

        // 新建线程，用于给observer处理页面UI
        thread = new Thread() {
            @Override
            public void run() {
                super.run();
                while (true) {
                    try {
                        Thread.sleep(300);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (sc != null && isThreadRunning == true) {
                        //mHandler.obtainMessage(101).sendToTarget();
                        operationObservable.subscribe(new Subscriber() {
                            @Override
                            public void onCompleted() {
                                ;
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                ;
                            }

                            @Override
                            public void onNext(Object o) {
                                if (isPlay) {
                                    playTime.setText(toFormatStr((int)o));
                                    seekBar.setProgress((int)((int)o / (double)grossTime * 100));
                                }

                            }
                        });
                    }
                }
            }
        };
        //  启动线程
        thread.start();

        // 图片旋转的动画
        objectAnimator = ObjectAnimator.ofFloat(image, "rotation", 0, 360);
        objectAnimator.setDuration(20000);
        objectAnimator.setInterpolator(new LinearInterpolator());
        objectAnimator.setRepeatCount(ValueAnimator.INFINITE);

        // Register Event Bus
        EventBus.getDefault().register(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent event) {
        if (event.isFinish == true) {
            System.out.println("Play Finish! in UI");
            setState(STATE.stopping);
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (data != null) {
            Uri uri = data.getData();
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                //4.4以后
                path = getPath(this, uri);
            } else {
                //4.4以下下系统调用方法
                path = getRealPathFromURI(uri);
            }
            System.out.println("Path: " + path);
            if (path != null) {
                System.out.println("Change song!");
                // 请求服务
                try {
                    //
                    setState(STATE.stopping);
                    // 告知后台服务歌曲路径
                    Parcel d = Parcel.obtain();
                    Parcel reply = Parcel.obtain();
                    d.writeString(path);
                    mBinder.transact(107, d, reply, 0);
                    // 获取歌曲信息
                    Bundle receive = reply.readBundle();
                    byte[] pic = receive.getByteArray("photo");
                    Bitmap bitmap;
                    // 处理封面，若为空，使用默认提供的图片
                    if (pic!=null) {
                        System.out.println("Update Pic");
                        bitmap = BitmapFactory.decodeByteArray(pic, 0, pic.length);
                    }
                    else {
                        System.out.println("Use default pic");
                        bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.img);
                    }
                    currentProgress = 0;
                    grossTime = receive.getInt("time");
                    songTitle = receive.getString("title");
                    songArtist = receive.getString("artist");
                    System.out.println(songTitle + " " + songArtist);
                    // 渲染页面
                    image.setImageBitmap(bitmap);
                    remainTime.setText(toFormatStr(grossTime));
                    if(songArtist == null) {
                        artist.setText("");
                    }
                    else {
                        artist.setText(songArtist);
                    }
                    title.setText(songTitle);

                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }
    }

    public String getRealPathFromURI(Uri contentUri) {
        String res = null;
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
        if(null!=cursor&&cursor.moveToFirst()){;
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            res = cursor.getString(column_index);
            cursor.close();
        }
        return res;
    }

    /**
     * 专为Android4.4设计的从Uri获取文件绝对路径，以前的方法已不好使
     */
    @SuppressLint("NewApi")
    public String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{split[1]};

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public String getDataColumn(Context context, Uri uri, String selection,
                                String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }


    // 将时间格式化输出为字符串
    private String toFormatStr(int time) {
        // time是秒数ms
        Calendar calendar = Calendar.getInstance();
        calendar.set(0, 0, 0, 0, time / (1000 * 60), ((time %(1000 * 60)) / 1000));
        return simpleDateFormat.format(calendar.getTime());
    }

    // 改变播放器的状态，处理动画事件和进度条
    private void setState(STATE state) {
        this.state = state;
        System.out.println("Set State: " + state);
        switch (state) {
            case playing:
                isPlay = true;
                playBtn.setImageResource(R.mipmap.pause);
                if (!objectAnimator.isStarted()) {
                    objectAnimator.start();
                }
                objectAnimator.resume();
                break;
            case pausing:
                isPlay = false;
                playBtn.setImageResource(R.mipmap.play);
                objectAnimator.pause();
                break;
            case stopping:
                isPlay = false;
                playBtn.setImageResource(R.mipmap.play);
                playTime.setText(toFormatStr(0));
                objectAnimator.end();
                seekBar.setProgress(0);
                break;
                default:break;
        }
    }
}
