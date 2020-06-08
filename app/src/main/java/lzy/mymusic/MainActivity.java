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
    public MyService myService;
    private MyConnection connection;
    private SeekBar seekBar;
    private TextView songinfo, now_text, all_text;
    private ListView musicListView;
    private Button btn_mode;
    private static Handler handler;
    private Thread thread;
    private String[] songs;

    public AudioManager audioManager;
    public AudioManager.OnAudioFocusChangeListener afChangeListener;
    public AudioAttributes playbackAttributes;
    public AudioFocusRequest focusRequest;
    final Object focusLock = new Object();

    boolean playbackDelayed = false;
    boolean playbackNowAuthorized = false;
    boolean resumeOnFocusGain = false;

    int CIRCULATE = 1;
    int SINGEL = 2;
    int RAMDOM = 3;

    String CIRCULATE_S = "列表循环";
    String SINGEL_S = "单曲循环";
    String RAMDOM_S = "随机播放";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i("TAG", "CREATE");
        Intent intent = new Intent(this, MyService.class);
        startService(intent);
        service_first_start = true;
        //bindService(intent, connection, BIND_AUTO_CREATE);

        if(ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.
                permission.READ_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},1);
        }else {
            get_permission = true;
        }

        musicListView = (ListView)findViewById(R.id.musiclist);
        musicListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //String songName = songs[position];
                if (myService.mediaPlayer != null) {
                    if (myService.songIndex != position) {
                        try {
                            if(request())
                                myService.changeSong(position);
                        } catch (Exception e) {
                            Log.i("TAG", e.getMessage());
                        }
                    }
                }
            }
        });


        Button btn_playstop = (Button) findViewById(R.id.btn_playstop);
        btn_playstop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (myService.mediaPlayer.isPlaying()) {
                        myService.pause();
                    } else if (!myService.mediaPlayer.isPlaying()) {
                        if(request())
                            myService.continuePlay();
                    }
                } catch (Exception e) {
                    Log.i("TAG", e.getMessage());
                }
            }
        });

        Button btn_prev = (Button) findViewById(R.id.btn_prev);
        btn_prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    if(request())
                        myService.previous();
                } catch (Exception e) {
                    Log.i("TAG", e.getMessage());
                }

            }
        });

        Button btn_next = (Button) findViewById(R.id.btn_next);
        btn_next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    int mode = myService.getPlayMode();
                    if(mode == SINGEL){
                        if(request())
                            myService.singel_next();
                    }else{
                        if(request())
                            myService.next();
                    }

                } catch (Exception e) {
                    Log.i("TAG", e.getMessage());
                }
            }
        });

        btn_mode = (Button) findViewById(R.id.btn_mode);


        btn_mode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    myService.changePlayMode();
                    int mode = myService.getPlayMode();
                    btn_mode.setText(setMode(mode));
                } catch (Exception e) {
                    Log.i("TAG", e.getMessage());
                }
            }
        });

        songinfo = (TextView) findViewById(R.id.songinfo);
        now_text = (TextView) findViewById(R.id.nowtime);
        all_text = (TextView) findViewById(R.id.alltime);


        seekBar = (SeekBar) findViewById(R.id.seekbar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser){
                    int all_time = myService.mediaPlayer.getDuration();
                    int seekbar_max = seekBar.getMax();
                    now_text.setText(setNowtime((all_time * progress / seekbar_max) / 1000));
                }
                else{
                    if(myService.mediaPlayer.isPlaying())
                        if(progress == seekBar.getMin()){
                            //切换歌曲
                            int all_time = myService.mediaPlayer.getDuration();
                            seekBar.setMax(all_time/1000);
                            //Log.i("TAG", all_time+"");
                        }
                }


            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int seekbar_now = seekBar.getProgress();
                int all_time = myService.mediaPlayer.getDuration();
                int seekbar_max = seekBar.getMax();
                myService.mediaPlayer.seekTo(all_time * seekbar_now / seekbar_max);
            }
        });


        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        afChangeListener = new AudioManager.OnAudioFocusChangeListener(){
            public void onAudioFocusChange(int focusChange){
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_GAIN:
                        if (playbackDelayed || resumeOnFocusGain) {
                            synchronized(focusLock) {
                                playbackDelayed = false;
                                resumeOnFocusGain = false;
                            }
                            Log.i("TAG", "GAIN");
                            if(!myService.mediaPlayer.isPlaying())
                                myService.continuePlay();
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS:
                        Log.i("TAG", "LOSS");
                        synchronized(focusLock) {
                            resumeOnFocusGain = false;
                            playbackDelayed = false;
                        }
                        if(myService.mediaPlayer.isPlaying())
                            myService.pause();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        Log.i("TAG", "LOSS_TRANSIENT");
                        synchronized(focusLock) {
                            resumeOnFocusGain = true;
                            playbackDelayed = false;
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        Log.i("TAG", "LOSS_TRANSIENT_CAN_DUCK");
                        // ... pausing or ducking depends on your app
                        if(myService.mediaPlayer.isPlaying())
                            myService.pause();
                        break;
                }
            }
        };

    }


    boolean request() {
        if(focusRequest == null){
            if (playbackAttributes == null) {
                playbackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
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
        Intent intent = new Intent(this, MyService.class);
        connection = new MyConnection();
        bindService(intent, connection, BIND_AUTO_CREATE);

    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i("TAG", "STOP");
        myService.savePlaySong();
        unbindService(connection);
        unbind = true;
        Log.i("TAG", "UNBIND");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //unbindService(connection);
        Intent intent = new Intent(this, MyService.class);
        stopService(intent);
        service_first_start = false;
        while (!abandon())
        Log.i("TAG", "DES");
    }


    public class MyThread extends Thread{

        private void onThreadWait() {
            try {
                synchronized (this) {
                    this.wait();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            int now_time, all_time, seekbar_max;
            while (!Thread.currentThread().isInterrupted()) {
                if(unbind){
                    Log.i("TAG", "pause");
                    onThreadWait();
                }else {
                    //Log.i("TAG", "go");
                    if (myService.mediaPlayer != null && myService.mediaPlayer.isPlaying()) {
                        now_time = myService.mediaPlayer.getCurrentPosition();
                        all_time = myService.mediaPlayer.getDuration();
                        seekbar_max = seekBar.getMax();
                        Message msg = Message.obtain();
                        msg.what = 123;
                        msg.arg1 = seekbar_max * now_time / all_time;
                        msg.arg2 = now_time;
                        handler.sendMessage(msg);
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Log.i("TAG", e.getMessage());
                    }
                }
            }
        }
    }

    public void onThreadResume(Thread t) {
        try {
            synchronized(t) {
                t.notify();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private class MyConnection implements ServiceConnection{
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            MyService.MyBinder myBinder = (MyService.MyBinder) service;
            unbind = false;
            if(service_first_start){
                Log.i("TAG", "BIND");
                myService = myBinder.getService();
                myService.getContext(getApplicationContext());

                if(get_permission) {
                    myService.scanMusic();
                    initMusicList();
                    initPlayInfo();
                }

                handler = new Handler(new Handler.Callback() {
                    @Override
                    public boolean handleMessage(Message msg) {
                        int all_time = myService.mediaPlayer.getDuration();
                        String song_name = songs[myService.songIndex];
                        if (msg.what == 123) {
                            try {
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

                thread = new MyThread();
                thread.start();
                service_first_start = false;

            }else{
                //thread.start();
                onThreadResume(thread);
                Log.i("TAG", "REBIND");
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    }



    private String setNowtime(int now_time){
        int now_minute = 0;
        while (now_time >= 60){
            now_minute += 1;
            now_time -= 60;
        }
        return (now_minute >= 10 ? now_minute:"0" + now_minute) + ":" +
                (now_time >= 10 ? now_time : "0"+now_time);
    }
    private String setAlltime(int all_time){
        int  all_minute = 0;
        while (all_time >= 60){
            all_minute += 1;
            all_time -= 60;
        }
        return (all_minute >= 10 ? all_minute:"0" + all_minute) + ":" +
                (all_time >= 10 ? all_time : "0"+all_time);
    }
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
    private void initMusicList() {
        songs = new String[myService.musicList.size()];
        for (int i = 0; i < myService.musicList.size(); i++) {
            String path = myService.musicList.get(i).getUrl();
            File file = new File(path);
            String name = file.getName();
            int index = name.lastIndexOf('.');
            songs[i] = name.substring(0, index);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, songs);
        musicListView.setAdapter(adapter);
        Log.i("TAG", "init list finsh");
    }
    private void initPlayInfo(){
        int mode = myService.getPlayMode();
        btn_mode.setText(setMode(mode));
        myService.getPlaySong();
        String song_name = songs[myService.songIndex];
        songinfo.setText(song_name);

        int all_time = myService.getSongDuration();
        all_text.setText(setAlltime(all_time / 1000));
        seekBar.setMax(all_time/1000);
        int now_time = myService.getSongNowtime();
        now_text.setText(setNowtime(now_time / 1000));
        int seekbar_max = seekBar.getMax();
        if(all_time == 0){
            seekBar.setProgress(0);
        }else {
            seekBar.setProgress(seekbar_max * now_time / all_time);
        }
        myService.prepare_media();
        myService.mediaPlayer.seekTo(now_time);
        Log.i("TAG", "init info finish");
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //myService.getContext(getApplicationContext());
                    get_permission = true;
                    myService.scanMusic();
                    initMusicList();
                    initPlayInfo();



                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    //request again!
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},1);
                }
            }
        }
    }
}
