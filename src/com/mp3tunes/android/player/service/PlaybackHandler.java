package com.mp3tunes.android.player.service;

import java.io.IOException;

import com.binaryelysium.mp3tunes.api.LockerId;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.service.PlaybackService.MyOnCompletionListener;
import com.mp3tunes.android.player.service.PlaybackService.MyOnErrorListener;
import com.mp3tunes.android.player.util.AddTrackToMediaStore;
import com.mp3tunes.android.player.util.Timer;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.AsyncTask;

public class PlaybackHandler
{
    private MediaPlayer mMp;
    private CachedTrack mTrack;
    private boolean     mPrepared;
    private Context     mContext;
    private Timer       mTimer;
    
    private PrepareTask mPrepareTask;
    
    private long        mDuration;
    
    private OnCompletionListener      mOnCompletionListener;
    private MyOnErrorListener         mOnErrorListener;
    private OnInfoListener            mOnInfoListener;
    private OnPreparedListener        mOnPreparedListener;
    private OnBufferingUpdateListener mOnBufferingUpdateListener;
    
    public PlaybackHandler(Context context, OnInfoListener info, MyOnErrorListener error, MyOnCompletionListener comp)
    {
        mContext = context;
        
        mOnPreparedListener   = new MyOnPreparedListener();
        mOnInfoListener       = info;
        mOnErrorListener      = error;
        mOnCompletionListener = comp;
        mPrepared             = false;
        
        mOnBufferingUpdateListener = new MyOnBufferingUpdateListener();
    }
    
    synchronized public boolean play(CachedTrack t)
    {
        if (t.getStatus() == CachedTrack.Status.failed) return false;
        if (mMp != null && mPrepared) {
            mMp.release();
            mPrepared = false;
        }
        mTrack = t;
        mTimer = new Timer("Waiting to prepare");
        mPrepareTask = new PrepareTask();
        if (mPrepareTask.runAsync()) {
            Logger.log("Running prepare async");
            mPrepareTask.execute((Void[])null);
            return true;
        }
        return mPrepareTask.prepare();
    }
    
    synchronized public void pause()
    {
        if (mPrepared) mMp.pause();
    }

    synchronized public void unpause()
    {
        if (mPrepared)
            mMp.start();
    }
    
    synchronized public void stop()
    {
        if (mPrepared) mMp.stop();
    }
    
    synchronized public boolean isPlaying() 
    {
        if (mPrepared)
            return mMp.isPlaying();
        return false;
    }
    
    synchronized public void setVolume(float leftVolume, float rightVolume)
    {
        mMp.setVolume(leftVolume, rightVolume);
    }
    
    synchronized public long getDuration()
    {
        if (mPrepared) {
            long duration = mMp.getDuration();
            if (duration > 0)
                return duration;
            if (mDuration <= 0) {
               Logger.log("FIXME: mMp.getDuration() and mDuration both less than 0 this should not happen"); 
            }
            return mDuration;
        }
        return 0;
    }

    synchronized public long getPosition()
    {
        if (mPrepared)
            return mMp.getCurrentPosition();
        return 0;
    }

    synchronized public boolean isPaused()
    {
        if (mPrepared)
            return !mMp.isPlaying();
        return true;
    }
    
    synchronized public void finish()
    {
        if (mPrepared) {
            mPrepared = false;
            if (mMp != null)
                mMp.release();
            mMp = null;
            if (mTrack != null)
                mTrack.deleteTmpCopy();
        }
    }
    
    class PrepareTask extends AsyncTask<Void, Void, Boolean>
    {
        String mUrl = null;
        boolean runAsync()
        {
            Logger.log("preparing track: " + mTrack.getTitle());
            Logger.log("Artist:          " + mTrack.getArtistName());
            Logger.log("Album:           " + mTrack.getArtistName());
            
            mUrl = mTrack.getCachedUrl();
            if (LockerId.class.isInstance(mTrack.getId())) {
                Logger.log("checking local store");
                if (AddTrackToMediaStore.isInStore(mTrack, mContext)) {
                    mOnBufferingUpdateListener.onBufferingUpdate(mMp, 100);
                    mUrl = AddTrackToMediaStore.getTrackUrl(mTrack, mContext);
                } else {
                    int status = mTrack.getStatus();
                    assert(status != CachedTrack.Status.failed);
                    createMediaPlayer();
                    if (status == CachedTrack.Status.finished)
                        return false;
                    return true;
                }
            } else {
                mOnBufferingUpdateListener.onBufferingUpdate(mMp, 100);
            }
            createMediaPlayer();
            return false;
        }
        
        void createMediaPlayer()
        {
            //State Idle
            mMp = new MediaPlayer();
            mMp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            
            setListeners();
        }
        
        boolean prepare()
        {
            try {
                //State Initialized
                Logger.log("playing: " + mUrl);
                mMp.setOnPreparedListener(mOnPreparedListener);

                mMp.setDataSource(mUrl);
                if (mTimer != null) mTimer.push();
                mTimer = new Timer("preparing");
                mMp.prepareAsync();
            
                //make sure volume is up
                mMp.setVolume(1.0f, 1.0f);
                return true;
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }
        
        boolean waitForDownloadToBegin()
        {
            Logger.log("Waiting on download to begin");
            int index = 0;
            while (mTrack.getContentLength() == 0) {
                if (((index++ % 100000) == 0) && (mTrack.getStatus() == CachedTrack.Status.failed)) {
                    return false;
                }
            }
            mDuration = mTrack.getContentLength() * 8000 / mTrack.mBitrate;
            Logger.log("Duration set to: " + mDuration);
            return true;
        }
        
        @Override
        protected void onPreExecute()
        {
        }
        
        @Override
        protected Boolean doInBackground(Void... params)
        {
            if (waitForDownloadToBegin())
                return prepare();
            return false;
        }
        
        @Override
        protected void onPostExecute(Boolean ret)
        {
            if (!ret) {
                mOnErrorListener.onTrackFailed(mMp, 0);
            }
        }
        
    };
    
    private void setListeners()
    {
        mMp.setOnCompletionListener(mOnCompletionListener);
        mMp.setOnErrorListener(mOnErrorListener);
        mMp.setOnInfoListener(mOnInfoListener);
        mMp.setOnPreparedListener(mOnPreparedListener);
        //mMp.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
    }
    
    private class MyOnPreparedListener implements MediaPlayer.OnPreparedListener
    {
        public void onPrepared(MediaPlayer mp)
        {
            synchronized (PlaybackHandler.this) {
                if (mTimer != null) mTimer.push();
                mTimer = null;
                if (mMp != null) {
                    mMp.start();
                    mPrepared = true;
                }
            }
        }
    }
    
    private class MyOnBufferingUpdateListener implements MediaPlayer.OnBufferingUpdateListener
    {

        public void onBufferingUpdate(MediaPlayer mp, int percent)
        {
            if (mTrack.getStatus() == CachedTrack.Status.failed)
                mOnErrorListener.onTrackFailed(mp, percent);
        }
        
    }

}
