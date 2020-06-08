package lzy.mymusic;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;

/**
 * Created by lzyest on 2020/6/6.
 */

public class AudioFocusManager implements AudioManager.OnAudioFocusChangeListener {
    public AudioManager audioManager;
    public AudioAttributes playbackAttributes;
    public AudioFocusRequest focusRequest;
    final Object focusLock = new Object();

    boolean playbackDelayed = false;
    boolean playbackNowAuthorized = false;
    boolean resumeOnFocusGain = false;

    public AudioFocusManager(Context context){
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void request(){
        playbackAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        focusRequest= new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build();

        int res = audioManager.requestAudioFocus(focusRequest);
        synchronized(focusLock) {
            if (res == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                playbackNowAuthorized = false;
            } else if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                playbackNowAuthorized = true;
                //playbackNow();
            } else if (res == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                playbackDelayed = true;
                playbackNowAuthorized = false;
            }
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange){
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                if (playbackDelayed || resumeOnFocusGain) {
                    synchronized(focusLock) {
                        playbackDelayed = false;
                        resumeOnFocusGain = false;
                    }
                    //playbackNow();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                synchronized(focusLock) {
                    resumeOnFocusGain = false;
                    playbackDelayed = false;
                }
                //pausePlayback();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                synchronized(focusLock) {
                    resumeOnFocusGain = true;
                    playbackDelayed = false;
                }
                //pausePlayback();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // ... pausing or ducking depends on your app
                break;
        }
    }
}



