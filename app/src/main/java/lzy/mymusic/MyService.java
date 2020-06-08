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
    //public String songName;
    public AudioFocusManager audioFocusManager;
    public int songIndex = 0;
    int CIRCULATE = 1;
    int SINGEL = 2;
    int RAMDOM = 3;
    private  Context activity_context;


    public void getContext(Context context){
        try {
            activity_context = context;
        }catch (Exception e) {
            Log.i("TAG", e.getMessage());
        }

        //SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_APPEND);
    }

    public void scanMusic(){
        try {
            musicList.clear();

            Cursor cursor = activity_context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null,null,null,MediaStore.Audio.AudioColumns.IS_MUSIC);
            if(null == cursor){
                Log.i("TAG", "null");
                return;
            }
            if(cursor.moveToFirst()){
                while(!cursor.isAfterLast()) {
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));

                    String title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));

                    String album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));
                    int albumID = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));

                    String artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));

                    String url = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));

                    int duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));

                    Long size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE));


                    if(size > 1024*800) {
                        Music music = new Music();
                        music.setID(id);
                        music.setTitle(title);
                        music.setAlbum(album);
                        music.setAlbumID(albumID);
                        music.setArtist(artist);
                        music.setUrl(url);
                        music.setDuration(duration);
                        music.setSize(size);

                        musicList.add(music);
                    }
                    cursor.moveToNext();
                }
                cursor.close();
                Log.i("TAG", "scan finish");
            }
        }catch (Exception e){
            Log.i("TAG", e.getMessage());
        }
    }


    public void play(){
        try {
            mediaPlayer.reset();
            String dataSourse = musicList.get(songIndex).getUrl();
            mediaPlayer.setDataSource(dataSourse);
            mediaPlayer.prepare();
            mediaPlayer.start();
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
            //Log.i("TAG", Integer.toString(songIndex)+"");
        }catch (Exception e){
            Log.i("TAG", e.getMessage());
        }
    }

    public void next(){
        int mode = getPlayMode();
        if(mode == CIRCULATE)
            songIndex = (songIndex == musicList.size()-1 ? 0: songIndex+1);
        else if(mode == RAMDOM){
            Random random = new Random();
            songIndex = random.nextInt(musicList.size());
        }
        //Log.i("TAG", Integer.toString(songIndex)+"");
        play();
    }
    public void singel_next(){
        int mode = getPlayMode();
        if(mode == SINGEL){
            songIndex = (songIndex == musicList.size()-1 ? 0: songIndex+1);
        }
        play();
    }
    public void pause(){
        if(mediaPlayer.isPlaying()){
            mediaPlayer.pause();
        }
    }

    public void continuePlay(){
        int position = mediaPlayer.getCurrentPosition();
        mediaPlayer.seekTo(position);
        mediaPlayer.start();
    }

    public void previous(){
        songIndex = (songIndex == 0 ? musicList.size()-1 : songIndex-1);
        //Log.i("TAG", Integer.toString(songIndex)+"");
        play();
    }

    public int CurrentPosition(){
        if(mediaPlayer != null){
            return mediaPlayer.getCurrentPosition();
        }
        else{
            return 0;
        }
    }
    public void changeSong(int position){
        songIndex = position;
        play();
    }

    public void savePlayMode(int mode){
        try {
            SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences("data", Context.MODE_APPEND);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt("playmode", mode);
            editor.apply();
        }catch (Exception e){
            Log.i("TAG", e.getMessage());
        }

    }

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

    public int getPlayMode(){
        try {
            SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences("data", Context.MODE_APPEND);
            return sharedPreferences.getInt("playmode", CIRCULATE);
        }catch (Exception e){
            Log.i("TAG", e.getMessage());
        }
        return CIRCULATE;
    }

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

    public void getPlaySong(){
        try {
            SharedPreferences sharedPreferences = activity_context.getSharedPreferences("data", Context.MODE_APPEND);
            songIndex = sharedPreferences.getInt("playsong", 0);
        }catch (Exception e){
            Log.i("TAG", e.getMessage());
        }
    }

    public int getSongDuration(){
        try {
            SharedPreferences sharedPreferences = activity_context.getSharedPreferences("data", Context.MODE_APPEND);
            return sharedPreferences.getInt("songDuration", 0);
        }catch (Exception e){
            Log.i("TAG", e.getMessage());
        }
        return 0;
    }

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
        musicList = new ArrayList<Music>();
        mediaPlayer.reset();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // The service is starting, due to a call to startService()
        Log.i("TAG", "SER_START");
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
        startForeground(123456,notification);

        return Service. START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        Log.i("TAG", "SER_BIND");
        return new MyBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // All clients have unbound with unbindService()
        Log.i("TAG", "SER_UNBIND");
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
        savePlaySong();
        if(mediaPlayer != null)
            mediaPlayer.release();
        mediaPlayer = null;
        Log.i("TAG", "SER_DES");
    }


    public class MyBinder extends Binder {
        MyService getService() {
            return MyService.this;
        }
    }
}

