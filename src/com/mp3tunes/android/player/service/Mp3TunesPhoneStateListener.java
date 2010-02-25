package com.mp3tunes.android.player.service;

import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;


public class Mp3TunesPhoneStateListener extends PhoneStateListener 
{
    private FadeVolumeTask mFadeVolumeTask = null;
    private PlayerHandler  mPlayerHandler;
    private boolean        mFadingIn  = false;   
    private boolean        mFadingOut = false;
    private boolean        mFadedOut  = false;
    
    public Mp3TunesPhoneStateListener(PlayerHandler player)
    {
        mPlayerHandler = player;
    }

    
    
    @Override
    synchronized public void onCallStateChanged(int state, String incomingNumber)
    {
        if (mFadeVolumeTask != null)
            mFadeVolumeTask.cancel();

        if (state == TelephonyManager.CALL_STATE_IDLE)
        {
            if (!mFadedOut || mFadingIn) {
                return;
            }
            mFadeVolumeTask = new FadeVolumeTask(mPlayerHandler, FadeVolumeTask.FADE_IN,
                    5000) {
                @Override
                public void onPreExecute()
                {
                    mPlayerHandler.unpause();
                }

                @Override
                public void onPostExecute()
                {
                    mFadingIn       = false;
                    mFadedOut       = false;
                    mFadeVolumeTask = null;
                }
            };
        } else {
            //Check to see if the current track is already paused
            //if it is do nothing
            if (mPlayerHandler == null) return;
            MediaPlayerTrack t = mPlayerHandler.getMediaPlayerTrack();
            if (t == null)    return;
            if (t.isPaused()) return;
            if (mFadingOut)   return;
            mFadingOut = true;

            // fade out faster if making a call, this feels more natural
            int duration = state == TelephonyManager.CALL_STATE_RINGING ? 3000
                    : 1500;

            mFadeVolumeTask = new FadeVolumeTask(mPlayerHandler, FadeVolumeTask.FADE_OUT,
                    duration) {
                @Override public void onPostExecute()
                {
                    mPlayerHandler.pause();
                    mFadedOut       = true;
                    mFadeVolumeTask = null;
                }
                @Override public void onPreExecute() {}
            };
        }
        super.onCallStateChanged(state, incomingNumber);
    }
};
