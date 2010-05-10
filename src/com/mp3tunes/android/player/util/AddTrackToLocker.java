package com.mp3tunes.android.player.util;
 
import java.io.IOException;

import org.json.JSONException;

import com.binaryelysium.mp3tunes.api.HttpClientCaller;
import com.binaryelysium.mp3tunes.api.InvalidSessionException;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.RemoteMethod;
import com.binaryelysium.mp3tunes.api.Track;
import com.binaryelysium.mp3tunes.api.Session.LoginException;
import com.mp3tunes.android.player.LocalId;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.activity.Player;
import com.mp3tunes.android.player.content.DbKeys;
import com.mp3tunes.android.player.content.LockerDb;
import com.mp3tunes.android.player.content.LockerDb.RefreshSearchTask.DbSearchQuery;
import com.mp3tunes.android.player.content.Queries.MakeQueryException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.RemoteViews;

public class AddTrackToLocker extends AsyncTask<Void, Void, Boolean>
{
    private Track   mTrack;
    private Context mContext;
    
    private static final int NOTIFY_ID = 10911252; // mp3 + 1 in ascii

    class Progress implements HttpClientCaller.Progress
    {
        int mProgress = 0;
        
        public void run(long progress, long total)
        {
            int p = (int)progress;
            mProgress = p;
            sendStartedNotification(mTrack, true, p, 100);
        }
        
    }
    
    public AddTrackToLocker(Track track, Context context)
    {
        mTrack   = track;
        mContext = context;
    }

    @Override
    protected Boolean doInBackground(Void... params)
    {
        if (!LocalId.class.isInstance(mTrack.getId())) {
            sendStartedNotification(mTrack, false, 0, 0);
            return true;
        }
        sendStartedNotification(mTrack, true, 0, 0);
        String path = mTrack.getPlayUrl(0);
        Log.w("Mp3Tunes", "Trying to upload: " + path + " to locker");
        
        try {
            RemoteMethod method = new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_PUT)
                .addFileKey("")
                .create();
            if (HttpClientCaller.getInstance().put(method, path, new Progress())) {
                LockerDb.RefreshSearchTask task = new LockerDb.RefreshSearchTask(Music.getDb(mContext), 
                                         new DbSearchQuery(mTrack.getTitle(), true, false, true), null, null);
                task.refresh();
                //Music.getDb(mContext).refreshSearch(mTrack.getTitle());
                sendFinishedNotification(mTrack, true);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidSessionException e) {
            e.printStackTrace();
        } catch (SQLiteException e) {
            e.printStackTrace();
        } catch (LockerException e) {
            e.printStackTrace();
        } catch (MakeQueryException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (LoginException e) {
            e.printStackTrace();
        }
        sendFinishedNotification(mTrack, false);
        return false;
    }
    
    private void sendFinishedNotification(Track t, boolean status)
    {
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager nm = (NotificationManager)mContext.getSystemService(ns);
        
        int icon = R.drawable.up2;
        long when = System.currentTimeMillis();
        CharSequence tickerText;
        
        if (status)
            tickerText = t.getTitle() + " added to locker";
        else
            tickerText = "Failed to add " + t.getTitle() + " to locker";
        
        Notification notification = new Notification(icon, tickerText, when);
        Intent        intent        = new Intent(mContext, Player.class);
        PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
        notification.setLatestEventInfo(mContext, "Mp3Tunes", tickerText, contentIntent);
        
        nm.notify(NOTIFY_ID, notification);
        nm.cancel(NOTIFY_ID);
    }
    
    private void sendStartedNotification(Track t, boolean status, int progress, int total)
    {
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager nm = (NotificationManager)mContext.getSystemService(ns);
        
        int icon = R.drawable.up2;
        long when = System.currentTimeMillis();
        CharSequence tickerText;
        
        if (status)
            tickerText = "Adding " + t.getTitle() + " to locker";
        else 
            tickerText = t.getTitle() + " is already in your locker";
        
        Notification notification = new Notification(icon, tickerText, when);
        RemoteViews contentView   = new RemoteViews(mContext.getPackageName(), R.layout.progress_notification_view);
        contentView.setImageViewResource(R.id.notification_image, R.drawable.logo_statusbar);
        contentView.setTextViewText(R.id.notification_text, "Uploading " + mTrack.getTitle());
        contentView.setProgressBar(R.id.notification_progress_bar, total, progress, (total == 0 && progress == 0));
        notification.contentView = contentView;
        Intent        intent        = new Intent(mContext, Player.class);
        PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
        notification.contentIntent = contentIntent;
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        //notification.setLatestEventInfo(mContext, "Mp3Tunes", tickerText, contentIntent);
        
        nm.notify(NOTIFY_ID, notification);
        if (!status) nm.cancel(NOTIFY_ID);
    }
}
