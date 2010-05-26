package com.mp3tunes.android.player.serviceold;

import com.binaryelysium.mp3tunes.api.Track;

import android.util.Log;

public class Logger
{
    private static final String TAG = "Mp3Tunes Playback Service";
    
    public static void log(String message)
    {
        Log.w(TAG, message);
    }
    
    public static void log(Object obj, String meth, String message)
    {
        Log.w(TAG, getClassMethMsgString(obj, meth, message));
    }
    
    public static void logTrack(int pos, Track t)
    {
        Log.w(TAG, getTrackString(pos, t));
    }
    
    public static void logTrack(Object obj, String meth, int pos, Track t)
    {
        Log.w(TAG, getClassMethMsgString(obj, meth, getTrackString(pos, t)));
    }
    
    public static void log(Throwable tr)
    {
        Log.e(TAG, "", tr);
    }
    
    public static void log(Throwable tr, String message)
    {
        Log.e(TAG, message, tr);
    }
    
    private static String getTrackString(int pos, Track t)
    {
        try {
            StringBuilder b = new StringBuilder("Track Info:\n");
            
            b.append("Position in playback queue: ");
            if (pos < 0)
                b.append("Undefined");
            else
                b.append(pos);
            b.append("\n");
            
            if (t != null)
                b.append(t.toString());
            else
                b.append("null");
            
            return b.toString();
        } catch (Exception e) {}
        return "";
    }
    
    private static String getClassMethMsgString(Object obj, String meth, String message)
    {
        return new StringBuilder(obj.getClass().getName()).append(":\n").append(meth).append(":\n")
        .append(message).append("\n").toString();
    }
}
