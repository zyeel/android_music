package lzy.mymusic;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore;

import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MyService extends Service {
    public MediaPlayer mediaPlayer;
    public List<Music> musicList;
    public int songIndex = 0;
    //播放模式
    int CIRCULATE = 1;
    int SINGEL = 2;
    int RAMDOM = 3;
    private  Context activity_context;

    //获取Context
    public void getContext(Context context){
        try {
            activity_context = context;
        }catch (Exception e) {
            Log.i("TAG", e.getMessage());
        }
    }

    //扫描手机的歌曲，返回歌曲数量
    public int scanMusic(){
        int num = -1;
        try {
            //清空
            musicList.clear();
            //获取cursor
            Cursor cursor = activity_context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null,null,null,MediaStore.Audio.AudioColumns.IS_MUSIC);
            if(null == cursor){
                Log.i("TAG", "null");
                return num;
            }
            //开始扫描
            if(cursor.moveToFirst()){
                while(!cursor.isAfterLast()) {
                    String url = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
                    int duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
                    Long size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE));
                    if(size > 1024*800) {
                        //加入musicList
                        Music music = new Music();
                        music.setUrl(url);
                        music.setDuration(duration);
                        musicList.add(music);
                        num++;
                    }
                    cursor.moveToNext();
                }
                cursor.close();
                Log.i("TAG", "scan finish");
            }
        }catch (Exception e){
            Log.i("TAG", e.getMessage());
        }
        return num;
    }

    //播放歌曲
    public void play(){
        try {
            mediaPlayer.reset();
            String dataSourse = musicList.get(songIndex).getUrl();
            mediaPlayer.setDataSource(dataSourse);
            mediaPlayer.prepare();
            mediaPlayer.start();
            //完成后自动下一首
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer m) {
                    next();
                }
            });
            //Log.i("TAG", Integer.toString(songIndex)+"");
        }catch (Exception e){
            Log.i("TAG", e.getMessage());
        }
    }

    //准备播放器，用于初始化
    public void prepare_media(){
        try {
            mediaPlayer.reset();
            String dataSourse = musicList.get(songIndex).getUrl();
            mediaPlayer.setDataSource(dataSourse);
            mediaPlayer.prepare();
            //mediaPlayer.start();
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer m) {
                    next();
                }
            });
        }catch (Exception e){
            Log.i("TAG", e.getMessage());
        }
    }

    //下一首
    public void next(){
        //根据不同播放模式设置index
        int mode = getPlayMode();
        if(mode == CIRCULATE)   //循环
            songIndex = (songIndex == musicList.size()-1 ? 0: songIndex+1);
        else if(mode == RAMDOM){    //随机
            Random random = new Random();
            songIndex = random.nextInt(musicList.size());
        }
        //单曲时，index不变
        play();
    }

    //单曲循环时，按下下一首的按钮
    public void singel_next(){
        int mode = getPlayMode();
        if(mode == SINGEL){
            songIndex = (songIndex == musicList.size()-1 ? 0: songIndex+1);
        }
        play();
    }

    //暂停
    public void pause(){
        if(mediaPlayer.isPlaying()){
            mediaPlayer.pause();
        }
    }

    //继续播放
    public void continuePlay(){
        int position = mediaPlayer.getCurrentPosition();
        mediaPlayer.seekTo(position);
        mediaPlayer.start();
    }

    //上一首，全部按列表循环的规则
    public void previous(){
        songIndex = (songIndex == 0 ? musicList.size()-1 : songIndex-1);
        play();
    }

    //播放器当前进度
    public int CurrentPosition(){
        if(mediaPlayer != null){
            return mediaPlayer.getCurrentPosition();
        }
        else{
            return 0;
        }
    }

    //点击列表改变歌曲
    public void changeSong(int position){
        songIndex = position;
        play();
    }

    //改变播放模式
    public void changePlayMode(){
        try {
            SharedPreferences sharedPreferences = activity_context.getSharedPreferences("data", Context.MODE_APPEND);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            int mode = sharedPreferences.getInt("playmode", CIRCULATE);
            mode = mode ==  RAMDOM ? CIRCULATE : mode+1;
            editor.putInt("playmode", mode);
            editor.apply();
        }catch (Exception e){
            Log.i("TAG", e.getMessage());
        }

    }

    //获取播放模式
    public int getPlayMode(){
        try {
            SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences("data", Context.MODE_APPEND);
            return sharedPreferences.getInt("playmode", CIRCULATE);
        }catch (Exception e){
            Log.i("TAG", e.getMessage());
        }
        return CIRCULATE;
    }

    //保存当前播放信息
    public void savePlaySong(){
        try {
            SharedPreferences sharedPreferences = activity_context.getSharedPreferences("data", Context.MODE_APPEND);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt("playsong", songIndex);
            editor.putInt("songDuration", musicList.get(songIndex).getDuration());
            editor.putInt("stopTime", CurrentPosition());
            editor.apply();
        }catch (Exception e){
            Log.i("TAG", e.getMessage());
        }
    }

    //获取保存的播放的歌曲index
    public void getPlaySong(){
        try {
            SharedPreferences sharedPreferences = activity_context.getSharedPreferences("data", Context.MODE_APPEND);
            songIndex = sharedPreferences.getInt("playsong", 0);
        }catch (Exception e){
            Log.i("TAG", e.getMessage());
        }
    }

    //获取保存的歌曲的时长
    public int getSongDuration(){
        try {
            SharedPreferences sharedPreferences = activity_context.getSharedPreferences("data", Context.MODE_APPEND);
            return sharedPreferences.getInt("songDuration", 0);
        }catch (Exception e){
            Log.i("TAG", e.getMessage());
        }
        return 0;
    }

    //获取保存的歌曲的进度
    public int getSongNowtime(){
        try {
            SharedPreferences sharedPreferences = activity_context.getSharedPreferences("data", Context.MODE_APPEND);
            return sharedPreferences.getInt("stopTime", 0);
        }catch (Exception e){
            Log.i("TAG", e.getMessage());
        }
        return 0;
    }


    @Override
    public void onCreate() {
        // The service is being created
        super.onCreate();
        Log.i("TAG", "SER_CREATE");
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        musicList = new ArrayList<>();
        mediaPlayer.reset();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // The service is starting, due to a call to startService()
        Log.i("TAG", "SER_START");
        //创建前台通知，防止服务被系统关闭
        // （虽然好像通知栏显示不了

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification notification =
                new NotificationCompat.Builder(this)
                        .setContentTitle(getText(R.string.app_name))
                        .setContentText(getText(R.string.notification_message))
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentIntent(pendingIntent)
                        .setTicker(getText(R.string.ticker_text))
                        .build();
        startForeground(123456, notification);
        return Service. START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        Log.i("TAG", "SER_BIND");
        //返回实例
        return new MyBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // All clients have unbound with unbindService()
        Log.i("TAG", "SER_UNBIND");
        //允许rebind
        return true;
    }
    @Override
    public void onRebind(Intent intent) {
        // A client is binding to the service with bindService(),
        // after onUnbind() has already been called
        Log.i("TAG", "SER_REBIND");
    }
    @Override
    public void onDestroy() {
        // The service is no longer used and is being destroyed
        super.onDestroy();
        //保存信息，释放播放器
        savePlaySong();
        if(mediaPlayer != null)
            mediaPlayer.release();
        mediaPlayer = null;
        Log.i("TAG", "SER_DES");
    }


    public class MyBinder extends Binder {
        //返回服务实例，用于调用服务的函数
        MyService getService() {
            return MyService.this;
        }
    }
}

