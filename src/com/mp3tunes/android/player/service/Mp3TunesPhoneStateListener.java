package com.mp3tunes.android.player.service;

import java.util.concurrent.locks.ReentrantLock;

import com.mp3tunes.android.player.serviceold.MediaPlayerTrack;
import com.mp3tunes.android.player.serviceold.PlayerHandler;

import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;


public class Mp3TunesPhoneStateListener extends PhoneStateListener 
{
    private FadeVolumeTask  mFadeVolumeTask = null;
    private PlaybackHandler mPlaybackHandler;
    private boolean         mFadingIn  = false;   
    private boolean         mFadingOut = false;
    private boolean         mFadedOut  = false;
    private ReentrantLock   mLock      = new ReentrantLock();
    
    static boolean sLogging = true;
    static boolean sDebugLocking = true;
    
    public Mp3TunesPhoneStateListener(PlaybackHandler player)
    {
        mPlaybackHandler = player;
    }

    static private void log(String message) 
    {
        if (sLogging)
            Logger.log("Mp3TunesPhoneStateListener: " + message);
    }
    
    private void lock(String caller)
    {
        if (sDebugLocking) Logger.log("Mp3TunesPhoneStateListener: " + caller + ": trying to lock");
        mLock.lock();
        if (sDebugLocking) Logger.log("Mp3TunesPhoneStateListener: " + caller + ": locked");
    }
    
    private void unlock(String caller)
    {
        if (sDebugLocking) Logger.log("Mp3TunesPhoneStateListener: " + caller + ": trying to unlock");
        mLock.unlock();
        if (sDebugLocking) Logger.log("Mp3TunesPhoneStateListener: " + caller + ": unlocked");
    }
    
    @Override
    synchronized public void onCallStateChanged(int state, String incomingNumber)
    {
        
        lock("onCallStateChanged");
        printState();
        try {
        log("Call state changed");
        if (mFadeVolumeTask != null) {
            log("Cancelling fade volume");
            mFadeVolumeTask.cancel();
        }

        if (state == TelephonyManager.CALL_STATE_IDLE)
        {
            log("Call State idle");
            if (!mFadedOut || mFadingIn) {
                log("mFadedOut: " + mFadedOut + " mFadingIn: " + mFadingIn);
                return;
            }
            
            mFadeVolumeTask = new FadeVolumeTask(mPlaybackHandler, FadeVolumeTask.FADE_IN, 5000, mLock) {
                @Override
                public void onPreExecute()
                {
                    mPlaybackHandler.unpause();
                }

                @Override
                public void onPostExecute()
                {
                    mFadingIn       = false;
                    mFadedOut       = false;
                    mFadingOut      = false;
                    mFadeVolumeTask = null;
                }
            };
        } else {
            log("Call State not idle");
            //Check to see if the current track is already paused
            //if it is do nothing
            if (check("Playback Handler null", mPlaybackHandler == null)) return;
            if (check("Playback is paused", mPlaybackHandler.isPaused())) return; 
            if (check("already fading out", mFadingOut))                  return;
            mFadingOut = true;

            // fade out faster if making a call, this feels more natural
            int duration = state == TelephonyManager.CALL_STATE_RINGING ? 3000
                    : 1500;

            mFadeVolumeTask = new FadeVolumeTask(mPlaybackHandler, FadeVolumeTask.FADE_OUT, duration, mLock) {
                @Override public void onPostExecute()
                {
                    mPlaybackHandler.pause();
                    mFadedOut       = true;
                    mFadeVolumeTask = null;
                }
            };
        }
        } finally {
            unlock("onCallStateChanged");
        }
        super.onCallStateChanged(state, incomingNumber);
    }
    
    private void printState()
    {
        if (sLogging) {
            Logger.log("mFadeVolumeTask:  " + mFadeVolumeTask);
            Logger.log("mPlaybackHandler: " + mPlaybackHandler);
            Logger.log("mFadingIn:        " + mFadingIn);   
            Logger.log("mFadingOut:       " + mFadingOut);
            Logger.log("mFadedOut:        " + mFadedOut);
            Logger.log("mLock:            " + mLock);
        }
    }

    boolean check(String message, boolean condition)
    {
        if (condition) return true;
        log(message);
        return false;
    }
};
