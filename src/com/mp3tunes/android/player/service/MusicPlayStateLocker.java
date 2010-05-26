package com.mp3tunes.android.player.service;

import android.content.Context;
import android.content.ContextWrapper;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

public class MusicPlayStateLocker
{
    private WifiLock            mWifiLock;
    private WakeLock            mWakeLock;
    
    MusicPlayStateLocker(Context context)
    {
        ContextWrapper wrapper = new ContextWrapper(context);
        // We want to keep the wifi enabled while playing, because
        // all our playing is done via streaming
        PowerManager pm = (PowerManager) wrapper.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MP3tunes Player");

        WifiManager wm = (WifiManager) wrapper.getSystemService(Context.WIFI_SERVICE);
        mWifiLock = wm.createWifiLock("MP3tunes Player");
        
    }
    
    public void lock()
    {
        mWakeLock.acquire();
        mWifiLock.acquire();
    }
    
    public void release()
    {
        if (mWakeLock.isHeld())
            mWakeLock.release();

        if (mWifiLock.isHeld())
            mWifiLock.release();
    }
}
