package com.mp3tunes.android.player.service;

import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;


public class Mp3TunesPhoneStateListener extends PhoneStateListener 
{
    private FadeVolumeTask mFadeVolumeTask = null;
    private PlayerHandler  mPlayerHandler;
    
    public Mp3TunesPhoneStateListener(PlayerHandler player)
    {
        mPlayerHandler = player;
    }

    @Override
    public void onCallStateChanged(int state, String incomingNumber)
    {
        if (mFadeVolumeTask != null)
            mFadeVolumeTask.cancel();

        if (state == TelephonyManager.CALL_STATE_IDLE) // fade music in to
        // 100%
        {
            mFadeVolumeTask = new FadeVolumeTask(mPlayerHandler, FadeVolumeTask.FADE_IN,
                    5000) {
                @Override
                public void onPreExecute()
                {
                    if (mPlayerHandler.isSystemPaused())
                        mPlayerHandler.pause();
                }

                @Override
                public void onPostExecute()
                {
                    mFadeVolumeTask = null;
                }
            };
        } else { // fade music out to
            // silence
            if (mPlayerHandler.isPaused()) {
                // this particular state of affairs should be impossible,
                // seeing as we are the only component that dares the pause
                // the radio. But we cater to it just in case
                mPlayerHandler.setVolume(0.0f, 0.0f);
                return;
            }

            // fade out faster if making a call, this feels more natural
            int duration = state == TelephonyManager.CALL_STATE_RINGING ? 3000
                    : 1500;

            mFadeVolumeTask = new FadeVolumeTask(mPlayerHandler, FadeVolumeTask.FADE_OUT,
                    duration) {

                @Override
                public void onPreExecute()
                {
                }

                @Override
                public void onPostExecute()
                {
                    mPlayerHandler.systemPause();
                    mFadeVolumeTask = null;
                }
            };
        }
        super.onCallStateChanged(state, incomingNumber);
    }
};
