package com.mp3tunes.android.player.service;

import java.util.Timer;
import java.util.TimerTask;



/**
 * Class responsible for fading in/out volume, for instance when a phone
 * call arrives
 * 
 * @author Lukasz Wisniewski
 * 
 *         TODO if volume is not at 1.0 or 0.0 when this starts (eg. old
 *         fade task didn't finish) then this sounds broken. Hard to fix
 *         though as you have to recalculate the fade duration etc.
 * 
 *         TODO setVolume is not logarithmic, and the ear is. We need a
 *         natural log scale see:
 *         http://stackoverflow.com/questions/207016/how
 *         -to-fade-out-volume-naturally see:
 *         http://code.google.com/android/
 *         reference/android/media/MediaPlayer
 *         .html#setVolume(float,%20float)
 */
public abstract class FadeVolumeTask extends TimerTask
{

    public static final int FADE_IN = 0;
    public static final int FADE_OUT = 1;

    private int mCurrentStep = 0;
    private int mSteps;
    private int mMode;
    
    private PlaybackHandler mPlaybackHandler;

    /**
     * Constructor, launches timer immediately
     * 
     * @param mode
     *            Volume fade mode <code>FADE_IN</code> or
     *            <code>FADE_OUT</code>
     * @param millis
     *            Time the fade process should take
     * @param steps
     *            Number of volume gradations within given fade time
     */
    public FadeVolumeTask(PlaybackHandler player, int mode, int millis)
    {
        mPlaybackHandler = player;
        this.mMode = mode;
        this.mSteps = millis / 20; // 20 times per second
        this.onPreExecute();
        new Timer().scheduleAtFixedRate(this, 0, millis / mSteps);
    }

    @Override
    public void run()
    {
        float volumeValue = 1.0f;

        if (mMode == FADE_OUT) {
            volumeValue *= (float) (mSteps - mCurrentStep) / (float) mSteps;
        } else {
            volumeValue *= (float) (mCurrentStep) / (float) mSteps;
        }

        mPlaybackHandler.setVolume(volumeValue, volumeValue);

        if (mCurrentStep >= mSteps) {
            this.onPostExecute();
            this.cancel();
        }

        mCurrentStep++;
    }

    /**
     * Task executed before launching timer
     */
    public abstract void onPreExecute();

    /**
     * Task executer after timer finished working
     */
    public abstract void onPostExecute();
}

