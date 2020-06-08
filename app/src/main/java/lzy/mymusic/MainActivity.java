package lzy.mymusic;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.os.Handler;

import java.io.File;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;



public class MainActivity extends AppCompatActivity {
    private Boolean service_first_start = false;
    private Boolean get_permission = false;
    private Boolean unbind = false;
    //服务相关
    public MyService myService;
    private MyConnection connection;
    //组件
    private SeekBar seekBar;
    private TextView songinfo, now_text, all_text;
    private ListView musicListView;
    private Button btn_mode;
    private MyAdapter adapter;
    private List<String> songs;
    //线程
    private static Handler handler;
    private Thread thread;
    //音频焦点
    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;
    private AudioAttributes playbackAttributes;
    private AudioFocusRequest focusRequest;
    final Object focusLock = new Object();
    boolean playbackDelayed = false;
    boolean resumeOnFocusGain = false;
    //播放模式
    int CIRCULATE = 1;
    int SINGEL = 2;
    int RAMDOM = 3;
    String CIRCULATE_S = "列表循环";
    String SINGEL_S = "单曲循环";
    String RAMDOM_S = "随机播放";

    int UPDATE = 0x101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i("TAG", "CREATE");
        //开启服务
        Intent intent = new Intent(this, MyService.class);
        startService(intent);
        service_first_start = true;
        //检查权限
        if(ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.
                permission.READ_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
            //申请权限
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},1);
        }else {
            get_permission = true;
        }
        //列表
        musicListView = (ListView)findViewById(R.id.musiclist);
        musicListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //点击列表项，切换歌曲
                if (myService.mediaPlayer != null) {
                    if (myService.songIndex != position) {
                        try {
                            if(request()) {
                                myService.changeSong(position);
                                setPlaysong();
                            }
                        } catch (Exception e) {
                            Log.i("TAG", e.getMessage());
                        }
                    }
                }
            }
        });

        //开始/暂停按钮
        Button btn_playstop = (Button) findViewById(R.id.btn_playstop);
        btn_playstop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (myService.mediaPlayer.isPlaying()) {
                        //暂停
                        myService.pause();
                    } else if (!myService.mediaPlayer.isPlaying()) {
                        if(request()) {
                            //继续播放
                            myService.continuePlay();
                        }
                    }
                } catch (Exception e) {
                    Log.i("TAG", e.getMessage());
                }
            }
        });

        //上一首按钮
        Button btn_prev = (Button) findViewById(R.id.btn_prev);
        btn_prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    if(request()){
                        myService.previous();
                        setPlaysong();
                    }
                } catch (Exception e) {
                    Log.i("TAG", e.getMessage());
                }

            }
        });

        //下一首按钮
        Button btn_next = (Button) findViewById(R.id.btn_next);
        btn_next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    int mode = myService.getPlayMode();
                    if(mode == SINGEL){
                        //单曲循环时，下一首按照列表循环规则的下一首
                        if(request()) {
                            myService.singel_next();
                            setPlaysong();
                        }
                    }else{
                        if(request()){
                            myService.next();
                            setPlaysong();
                        }
                    }
                } catch (Exception e) {
                    Log.i("TAG", e.getMessage());
                }
            }
        });

        //播放模式按钮
        btn_mode = (Button) findViewById(R.id.btn_mode);
        btn_mode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    //改变播放模式
                    myService.changePlayMode();
                    int mode = myService.getPlayMode();
                    btn_mode.setText(setMode(mode));
                } catch (Exception e) {
                    Log.i("TAG", e.getMessage());
                }
            }
        });

        //Text
        songinfo = (TextView) findViewById(R.id.songinfo);
        now_text = (TextView) findViewById(R.id.nowtime);
        all_text = (TextView) findViewById(R.id.alltime);

        //进度条
        seekBar = (SeekBar) findViewById(R.id.seekbar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser){
                    if(myService.musicList.size() > 0){
                        //用户拖动进度条，改变当前时间值
                        int all_time = myService.mediaPlayer.getDuration();
                        int seekbar_max = seekBar.getMax();
                        now_text.setText(setNowtime((all_time * progress / seekbar_max) / 1000));
                    }
                }
                else {
                    if (myService.mediaPlayer.isPlaying())
                        if (progress == seekBar.getMin()) {
                            //切换歌曲时，设置进度条最大值、更新当前播放歌曲的样式
                            int all_time = myService.mediaPlayer.getDuration();
                            seekBar.setMax(all_time / 1000);
                            setPlaysong();
                            Log.i("TAG", all_time + "");
                        }
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                //停止拖动时，播放器调整进度，继续播放
                int seekbar_now = seekBar.getProgress();
                int all_time = myService.mediaPlayer.getDuration();
                int seekbar_max = seekBar.getMax();
                myService.mediaPlayer.seekTo(all_time * seekbar_now / seekbar_max);
            }
        });

        //音频焦点管理
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        afChangeListener = new AudioManager.OnAudioFocusChangeListener(){
            public void onAudioFocusChange(int focusChange){
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_GAIN:      //获得焦点
                        if (playbackDelayed || resumeOnFocusGain) {
                            synchronized(focusLock) {
                                playbackDelayed = false;
                                resumeOnFocusGain = false;
                            }
                            Log.i("TAG", "GAIN");
                            //若当前没有播放，则继续播放
                            if(!myService.mediaPlayer.isPlaying())
                                myService.continuePlay();
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS:      //失去焦点
                        Log.i("TAG", "LOSS");
                        synchronized(focusLock) {
                            resumeOnFocusGain = false;
                            playbackDelayed = false;
                        }
                        //暂停播放
                        if(myService.mediaPlayer.isPlaying())
                            myService.pause();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:    //暂时失去焦点
                        Log.i("TAG", "LOSS_TRANSIENT");
                        synchronized(focusLock) {
                            resumeOnFocusGain = true;
                            playbackDelayed = false;
                        }
                        if(myService.mediaPlayer.isPlaying())
                            myService.pause();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:   //暂时失去
                        Log.i("TAG", "LOSS_TRANSIENT_CAN_DUCK");
                        // ... pausing or ducking depends on your app
                        //API26以上，系统自动降低音量
                        break;
                }
            }
        };

    }


    //申请音频焦点
    boolean request() {
        if(focusRequest == null){
            if (playbackAttributes == null) {
                playbackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
            }
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(afChangeListener)
                    .build();
        }

        int res = audioManager.requestAudioFocus(focusRequest);
        synchronized(focusLock) {
            if (res == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                return false;
            } else if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.i("TAG", "GET FOCUS");
                return true;
                //playbackNow();
            } else if (res == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                playbackDelayed = true;
                Log.i("TAG", "GET FOCUS DEELAYED");
                return false;
            }
        }
        return false;
    }

    //放弃音频焦点
    boolean abandon(){
        int res = audioManager.abandonAudioFocusRequest(focusRequest);
        synchronized(focusLock) {
            if(res == AudioManager.AUDIOFOCUS_REQUEST_FAILED){
                return false;
            }else if(res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
                Log.i("TAG", "ABANDON");
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i("TAG", "START");

    }

    @Override
    protected void onResume(){
        super.onResume();
        Log.i("TAG", "RESUME");
        //绑定服务
        Intent intent = new Intent(this, MyService.class);
        connection = new MyConnection();
        bindService(intent, connection, BIND_AUTO_CREATE);

    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i("TAG", "STOP");
        myService.savePlaySong();   //保存播放信息
        unbindService(connection);  //解绑服务
        unbind = true;
        Log.i("TAG", "UNBIND");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Intent intent = new Intent(this, MyService.class);
        stopService(intent);    //停止服务
        service_first_start = false;
        while (!abandon())      //放弃音频焦点
        Log.i("TAG", "DES");
    }

    //自定义Thread类
    class MyThread extends Thread{
        //线程进入等待状态
        private void onThreadWait() {
            try {
                synchronized (this) {
                    this.wait();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //Run方法
        @Override
        public void run() {
            int now_time, all_time, seekbar_max;
            while (!Thread.currentThread().isInterrupted()) {
                if(unbind){
                    //未绑定服务，说明当前APP不在前台
                    //就没有必要继续刷新UI
                    Log.i("TAG", "pause");
                    onThreadWait();
                }else {
                    //已绑定服务，说明当前APP在交互
                    //唤醒线程，刷新UI
                    //Log.i("TAG", "go");
                    //没有播放时不刷新
                    if (myService.mediaPlayer != null && myService.mediaPlayer.isPlaying()) {
                        now_time = myService.mediaPlayer.getCurrentPosition();
                        all_time = myService.mediaPlayer.getDuration();
                        seekbar_max = seekBar.getMax();
                        Message msg = Message.obtain();
                        msg.what = UPDATE;
                        msg.arg1 = seekbar_max * now_time / all_time;
                        msg.arg2 = now_time;
                        //发送消息
                        handler.sendMessage(msg);
                    }
                    try {
                        //每 1 秒刷新一次
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Log.i("TAG", e.getMessage());
                    }
                }
            }
        }
    }

    //唤醒线程
    public void onThreadResume(Thread t) {
        try {
            synchronized(this) {
                t.notify();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    //实现Connection
    private class MyConnection implements ServiceConnection{
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            MyService.MyBinder myBinder = (MyService.MyBinder) service;
            unbind = false;
            //服务第一次开启，第一次绑定
            if(service_first_start){
                Log.i("TAG", "BIND");
                //获取Service实例
                myService = myBinder.getService();
                //传递当前Context到Service
                myService.getContext(getApplicationContext());
                //获得权限后，进行初始化
                if(get_permission) {
                    int num = myService.scanMusic();
                    initMusicList();
                    if(num >= 0)
                        initPlayInfo();
                }
                //定义handler
                handler = new Handler(new Handler.Callback() {
                    @Override
                    public boolean handleMessage(Message msg) {
                        int all_time = myService.mediaPlayer.getDuration();
                        String song_name = songs.get(myService.songIndex);
                        if (msg.what == UPDATE) {
                            try {
                                //更新UI
                                seekBar.setProgress(msg.arg1);
                                songinfo.setText(song_name);
                                now_text.setText(setNowtime(msg.arg2 / 1000));
                                all_text.setText(setAlltime(all_time / 1000));
                                return true;
                            } catch (Exception e) {
                                Log.i("TAG", e.getMessage());
                                return false;
                            }
                        }
                        return false;
                    }
                });
                //启动线程
                thread = new MyThread();
                thread.start();

                service_first_start = false;
            }else{
                //非首次绑定
                //唤醒线程
                onThreadResume(thread);
                //设置当前列表播放项的样式
                setPlaysong();
                Log.i("TAG", "REBIND");
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    }

    //设置歌曲当前时间
    private String setNowtime(int now_time){
        int now_minute = 0;
        while (now_time >= 60){
            now_minute += 1;
            now_time -= 60;
        }
        return (now_minute >= 10 ? now_minute:"0" + now_minute) + ":" +
                (now_time >= 10 ? now_time : "0"+now_time);
    }

    //设置歌曲的时长
    private String setAlltime(int all_time){
        int  all_minute = 0;
        while (all_time >= 60){
            all_minute += 1;
            all_time -= 60;
        }
        return (all_minute >= 10 ? all_minute:"0" + all_minute) + ":" +
                (all_time >= 10 ? all_time : "0"+all_time);
    }

    //设置播放模式
    private String setMode(int mode){
        if(mode == CIRCULATE){
            return CIRCULATE_S;
        }else if(mode == SINGEL){
            return SINGEL_S;
        }else if(mode == RAMDOM){
            return RAMDOM_S;
        }
        return CIRCULATE_S;
    }

    //设置当前播放的列表项的样式
    private void setPlaysong(){
        if(myService.musicList.size() > 0) {
            adapter.setPlay(myService.songIndex);
            adapter.notifyDataSetChanged();
        }
    }

    //初始化列表
    private void initMusicList() {
        songs = new ArrayList<>();
        for (int i = 0; i < myService.musicList.size(); i++) {
            String path = myService.musicList.get(i).getUrl();
            File file = new File(path);
            String name = file.getName();
            int index = name.lastIndexOf('.');
            //截取歌名，加入List
            songs.add(name.substring(0, index));
        }
        //Adapter
        adapter = new MyAdapter(this, songs);
        musicListView.setAdapter(adapter);
        Log.i("TAG", "init list finsh");
    }

    //初始化播放信息
    private void initPlayInfo(){
        //播放模式
        int mode = myService.getPlayMode();
        btn_mode.setText(setMode(mode));
        //歌名
        myService.getPlaySong();
        String song_name = songs.get(myService.songIndex);
        songinfo.setText(song_name);
        //列表
        setPlaysong();
        musicListView.setSelection(myService.songIndex);
        //时间
        int all_time = myService.getSongDuration();
        all_text.setText(setAlltime(all_time / 1000));
        int now_time = myService.getSongNowtime();
        now_text.setText(setNowtime(now_time / 1000));
        //进度条
        if(all_time == 0){
            seekBar.setProgress(0);
            all_text.setText(setAlltime(myService.musicList.get(myService.songIndex).getDuration()/1000));
        }else {
            seekBar.setMax(all_time/1000);
            int seekbar_max = seekBar.getMax();
            seekBar.setProgress(seekbar_max * now_time / all_time);
            //Log.i("TAG", "set");
        }
        //播放器
        myService.prepare_media();
        myService.mediaPlayer.seekTo(now_time);
        Log.i("TAG", "init info finish");
    }

    //申请读取存储的权限的回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //获得权限
                    get_permission = true;
                    int num = myService.scanMusic();    //扫描歌曲
                    initMusicList();    //初始化列表
                    if(num >= 0)
                        initPlayInfo(); //初始化播放信息
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    //request again!
                    //ActivityCompat.requestPermissions(MainActivity.this,
                     //       new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},1);
                }
            }
        }
    }
}
