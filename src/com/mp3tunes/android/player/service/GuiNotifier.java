package com.mp3tunes.android.player.service;

import com.binaryelysium.mp3tunes.api.Track;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;

public class GuiNotifier
{
    NotificationHandler mNotifier;
    Service             mService;
    Context             mContext;
    
    //This stuff belongs in a separate file to decouple this file from the files that react to 
    //these intents
    public static final String META_CHANGED           = "com.mp3tunes.android.player.metachanged";
    public static final String QUEUE_CHANGED          = "com.mp3tunes.android.player.queuechanged";
    public static final String PLAYBACK_FINISHED      = "com.mp3tunes.android.player.playbackcomplete";
    public static final String PLAYBACK_STATE_CHANGED = "com.mp3tunes.android.player.playstatechanged";
    public static final String PLAYBACK_ERROR         = "com.mp3tunes.android.player.playbackerror";
    public static final String DATABASE_ERROR         = "com.mp3tunes.android.player.databaseerror";
    public static final String UNKNOWN                = "com.mp3tunes.android.player.unknown";
    
    GuiNotifier(Service service, Context context)
    {
        mService  = service;
        mContext  = context;
        mNotifier = new NotificationHandler(service, context);
    }
    
    public void prevTrack(Track t)
    {
        mNotifier.play(t);
        send(META_CHANGED, t);
    }
    
    public void nextTrack(Track t)
    {
        mNotifier.play(t);
        send(META_CHANGED, t);
    }
    
    public void play(Track t)
    {
        mNotifier.play(t);
        send(META_CHANGED, t);
    }
    
    public void pause(Track t)
    {
        mNotifier.pause(t);
        send(PLAYBACK_STATE_CHANGED, t);
    }
    
    public void stop(Track t)
    {
        mNotifier.stop();
        send(PLAYBACK_FINISHED, t);
    }
    
    public void sendPlaybackError(Track t, int errorType, int errorCode)
    {
        String errorMessage = "Unknown Error";
        if (errorType == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
            errorMessage = "Internal Android media server died";
        }
        if (errorType == MediaPlayer.MEDIA_ERROR_UNKNOWN) {
            if (errorCode < 0) {
                errorMessage = PlaybackErrorCodes.getError(errorCode);
            }
        }
        
        sendPlaybackError(t, errorMessage);
    }
    
    public void sendPlaybackError(Track t, String error)
    {
        mNotifier.error(t, error);
        send(PLAYBACK_ERROR, t);
    }
    
    public void sendDatabaseError()
    {
        send(DATABASE_ERROR, null);
    }
    
    private void send(String what, Track track)
    {
        Logger.log("Sending notification: " + what);
        Intent i = new Intent(what);
        if (track != null) {
            Logger.log("track: " + track.getTitle());
            i.putExtra("artist", track.getArtistName());
            i.putExtra("album", track.getAlbumTitle());
            i.putExtra("track", track.getTitle());
            i.putExtra("duration", track.getDuration());
            i.putExtra("id", track.getId());
        }
        mService.sendBroadcast(i);
    }
}
