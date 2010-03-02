package com.mp3tunes.android.player.util;

import android.util.Log;

public class Timer
{
    long   mT1;
    long   mT2;
    String mText;
    
    public Timer(String text)
    {
        mText = text;
        mT1 = System.currentTimeMillis();
    }
    
    void start(String text)
    {
        mText = text;
        mT1 = System.currentTimeMillis();
    }
    
    public void push() 
    {
        mT2 = System.currentTimeMillis();
        Log.w("Mp3Tunes", mText + " done: " + Long.toString(mT2 - mT1) + " elapsed");
    }
}
