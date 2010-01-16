package com.mp3tunes.android.player.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;

import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.activity.Player;

public class NotificationHandler
{
    private NotificationManager  mNm;
    private Service              sService;
    private static final int NOTIFY_ID = 10911251; // mp3 in ascii
    
    NotificationHandler(Service s, Context context)
    {
        ContextWrapper wrapper = new ContextWrapper(context);
        mNm = (NotificationManager) wrapper.getSystemService(Context.NOTIFICATION_SERVICE);
        sService = s;
    }
    
    private Notification build(String name, String title, String text)
    {
        Notification  notification  = new Notification(R.drawable.logo_statusbar, text, System.currentTimeMillis());
        Intent        intent        = new Intent(sService, Player.class);
        PendingIntent contentIntent = PendingIntent.getActivity(sService, 0, intent, 0);
        notification.setLatestEventInfo(sService, name, title, contentIntent);
        return notification;
    }
    
    public void play(Track track)
    {
        if (track == null)
            return;
        
        String text = "Playing: " + track.getTitle() + " by " + track.getArtistName();
        Notification notification = build(track.getArtistName(), track.getTitle(), text);
        notification.flags |= Notification.FLAG_ONGOING_EVENT;

        mNm.cancel(NOTIFY_ID);
        mNm.notify(NOTIFY_ID, notification);
    }
    
    public void pause(Track track)
    {
        String name  = "Paused";
        String title = "";
        if (track != null) {
            name  = track.getArtistName();
            title = track.getTitle();
        }
        String text = "MP3tunes Paused";
        
        Notification  notification  = build(name, title, text);
        
        mNm.cancel(NOTIFY_ID);
        mNm.notify(NOTIFY_ID, notification);
    }
    
    public void stop()
    {
        mNm.cancel(NOTIFY_ID);
    }

}
