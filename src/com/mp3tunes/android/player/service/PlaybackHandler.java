package com.mp3tunes.android.player.service;

import java.io.IOException;

import com.binaryelysium.mp3tunes.api.LockerId;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.service.PlaybackService.MyOnCompletionListener;
import com.mp3tunes.android.player.service.PlaybackService.MyOnErrorListener;
import com.mp3tunes.android.player.util.AddTrackToMediaStore;

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
            return !isPlaying();
        return true;
    }
    
    synchronized public void finish()
    {
        if (mPrepared) {
            mPrepared = false;
            if (mMp != null)
                mMp.release();
            mMp = null;
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
        
        void waitForDownloadToBegin()
        {
            Logger.log("Waiting on download to begin");
            while (mTrack.getContentLength() == 0) {}
            mDuration = mTrack.getContentLength() * 8000 / mTrack.mBitrate;
            Logger.log("Duration set to: " + mDuration);
        }
        
        @Override
        protected void onPreExecute()
        {
        }
        
        @Override
        protected Boolean doInBackground(Void... params)
        {
            waitForDownloadToBegin();
            return prepare();
        }
        
        @Override
        protected void onPostExecute(Boolean ret)
        {
            if (!ret) {
                mOnErrorListener.onTrackFailed(mMp, 0);
            }
        }
        
    };
    
  //This function must only be called by functions that hold both the mState lock and the mPreCaching locks
    private boolean prepare()
    {
        Logger.log("preparing track: " + mTrack.getTitle());
        Logger.log("Artist:          " + mTrack.getArtistName());
        Logger.log("Album:           " + mTrack.getArtistName());
        
        try {
            String url = mTrack.getCachedUrl();
            if (LockerId.class.isInstance(mTrack.getId())) {
                Logger.log("checking local store");
                if (AddTrackToMediaStore.isInStore(mTrack, mContext)) {
                    mOnBufferingUpdateListener.onBufferingUpdate(mMp, 100);
                    url = AddTrackToMediaStore.getTrackUrl(mTrack, mContext);
                }
                while (mTrack.getContentLength() == 0) {}
                mDuration = mTrack.getContentLength() * 8000 / mTrack.mBitrate;
            } else {
                mOnBufferingUpdateListener.onBufferingUpdate(mMp, 100);
            }
            
            //State Idle
            mMp = new MediaPlayer();
            mMp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            
            setListeners();
        
            //State Initialized
            Logger.log("playing: " + url);
            mMp.setDataSource(url);
            mMp.setOnPreparedListener(mOnPreparedListener);
            mMp.prepareAsync();
           
            //make sure volume is up
            mMp.setVolume(1.0f, 1.0f);
            
            return true;
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
    
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
