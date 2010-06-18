package com.mp3tunes.android.player.util;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class LifetimeLoggingActivity extends Activity
{
    @Override
    protected void onCreate(Bundle savedState)
    {
        Log.w("Mp3Tunes", "onCreate called for " + getLocalClassName());
        super.onCreate(savedState);
    }
    
    @Override
    protected void onStart()
    {
        Log.w("Mp3Tunes", "onStart called for " + getLocalClassName());
        super.onStart();
    }
    
    @Override
    protected void onRestart()
    {
        Log.w("Mp3Tunes", "onRestart called for " + getLocalClassName());
        super.onRestart();
    }
    
    @Override
    protected void onResume()
    {
        Log.w("Mp3Tunes", "onResume called for " + getLocalClassName());
        super.onResume();
    }
    
    @Override
    protected void onPause()
    {
        Log.w("Mp3Tunes", "onPause called for " + getLocalClassName());
        super.onPause();
    }
    
    @Override
    protected void onStop()
    {
        Log.w("Mp3Tunes", "onStop called for " + getLocalClassName());
        super.onStop();
    }
    
    @Override
    protected void onDestroy()
    {
        Log.w("Mp3Tunes", "onDestroy called for " + getLocalClassName());
        super.onDestroy();
    }
    
    
}
