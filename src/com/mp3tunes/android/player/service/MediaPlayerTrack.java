package com.mp3tunes.android.player.service;

import java.io.IOException;

import com.binaryelysium.mp3tunes.api.RemoteMethod;
import com.binaryelysium.mp3tunes.api.Track;

import android.app.Service;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;

public class MediaPlayerTrack
{
    private MediaPlayer mMp;
    private Track       mTrack;
    private boolean     mIsInitialized;
    private boolean     mBuffered;
    private int         mPercent;
    private Service     mService;
    private Context     mContext;
    
    private OnBufferingUpdateListener mOnBufferingUpdateListener;
    private OnCompletionListener      mOnCompletionListener;
    private OnErrorListener           mOnErrorListener;
    private OnPreparedListener        mOnPreparedListener;
    private TrackFinishedHandler      mTrackFinishedHandler;
    private BufferedCallback          mBufferedCallback;
    
    public MediaPlayerTrack(Track track, Service service, Context context)
    {
        mMp            = null;
        mTrack         = track;
        mIsInitialized = false;
        mBuffered      = false;
        mPercent       = 0;
        mService       = service;
        mContext       = context;
        
        mOnBufferingUpdateListener = new MyOnBufferingUpdateListener();
        mOnCompletionListener      = new MyOnCompletionListener(this);
        mOnErrorListener           = new MyOnErrorListener();
        mOnPreparedListener        = new MyOnPreparedListener();
    }
    
    synchronized public Track getTrack()
    {
        return mTrack;
    }
   
    synchronized public boolean play()
    {
        if (!mIsInitialized) {
          //state preparing
            if (!prepare(true))
                //state error
                return false;
        } else {
            start();
        }
        return true;
    }
    
    synchronized public boolean pause()
    {
        if (mIsInitialized) {
            if (mMp.isPlaying()) {
                //state paused
                mMp.pause();
                return true;
            }
        }
        return false;
    }
    
    synchronized public boolean unpause()
    {
        if (mIsInitialized) {
            if (!mMp.isPlaying()) {
                //state paused
                mMp.start();
                return true;
            }
        }
        return false;
    }
    
    synchronized public boolean stop()
    {
        if (mIsInitialized) {
            //state stopped
            mIsInitialized = false;
            mMp.stop();
            return true;
        }
        return false;
    }
    
    synchronized public boolean isPlaying() 
    {
        if (mIsInitialized) 
            return mMp.isPlaying();
        return false;
    }
    
    synchronized public boolean isBuffered()
    {
        return mBuffered;
    }
    
    synchronized public int getBufferPercent()
    {
        return mPercent;
    }
    
    synchronized public boolean requestPreload()
    {   
        return prepare(false);
    }
    
    synchronized private boolean start()
    {
        if (mIsInitialized) {
            mMp.start();
            return true;
        }
        return false;
    }
    
    synchronized private boolean prepare(boolean async)
    {
        try {
            RemoteMethod method 
                = new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_PLAY)
                    .addFileKey(mTrack.getFileKey())
                    .addParam("fileformat", "mp3")
                    .addParam("bitrate", Integer.toString(Bitrate.getBitrate(mService, mContext)))
                    .create();
            
            //State Idle
            mMp = new MediaPlayer();
            mMp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            
            setListeners();
        
            //State Initialized
            Logger.log("playing: " + method.getCall());
            mMp.setDataSource(method.getCall());
            
        
            if (async) {
                //State preparing
                mMp.setOnPreparedListener(mOnPreparedListener);
                mMp.prepareAsync();
            } else {
                //State prepared
                mMp.prepare();
                mIsInitialized = true;
            }
           
            //make sure volume is up
            mMp.setVolume(1.0f, 1.0f);
            
            return true;
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mIsInitialized = false;
        return false;
    }
    
    synchronized private void setListeners()
    {
        mMp.setOnCompletionListener(mOnCompletionListener);
        mMp.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
        mMp.setOnErrorListener(mOnErrorListener);
    }
    
    private class MyOnCompletionListener implements MediaPlayer.OnCompletionListener 
    {    
        private MediaPlayerTrack mMediaPlayerTrack;
        
        MyOnCompletionListener(MediaPlayerTrack track)
        {
            mMediaPlayerTrack = track;
        }
        
        synchronized public void onCompletion(MediaPlayer mp)
        {
            //If we are still in the initialized state then that means that we
            //completed our play back without an error
            if (mIsInitialized) {
                mTrackFinishedHandler.trackSucceeded(mMediaPlayerTrack);
            } else {
                mTrackFinishedHandler.trackFailed(mMediaPlayerTrack);
            }
        }
    };
    
    private class MyOnPreparedListener implements MediaPlayer.OnPreparedListener
    {

        synchronized public void onPrepared(MediaPlayer mp)
        {
            //state prepared
            mIsInitialized = true;
            start();
        }
        
    };
    
    private class MyOnBufferingUpdateListener implements MediaPlayer.OnBufferingUpdateListener
    {
        synchronized public void onBufferingUpdate(MediaPlayer mp, int percent)
        {
            mPercent = percent;
            if ((percent % 25) == 0) Logger.log("Buffering: " + percent);
            if (percent == 100) {
                if (mBufferedCallback != null)
                    mBuffered = true;
                    mBufferedCallback.run();
            }
        }
    };

    private class MyOnErrorListener implements MediaPlayer.OnErrorListener
    {
        synchronized public boolean onError(MediaPlayer mp, int what, int extra)
        {
            //State error
            mIsInitialized = false;
            
            
            //state idle
            mMp.reset();
            //returning false will call OnCompletionHandler
            return false;
        }
    };
    
    public interface TrackFinishedHandler
    {
        public void trackSucceeded(MediaPlayerTrack track);
        public void trackFailed(MediaPlayerTrack track);
    }
    
    public interface BufferedCallback
    {
        void run();
    }

    synchronized public long getDuration()
    {
        if (mIsInitialized)
            return mMp.getDuration();
        return 0;
    }

    synchronized public long getPosition()
    {
        if (mIsInitialized)
            return mMp.getCurrentPosition();
        return 0;
    }

    synchronized public boolean isPaused()
    {
        return mIsInitialized && !isPlaying();
    }
    
    synchronized public void setTrackFinishedHandler(TrackFinishedHandler handler)
    {
        mTrackFinishedHandler = handler;
    }
    
    synchronized public void setBufferedCallback(BufferedCallback callback)
    {
        mBufferedCallback = callback;
    }
        
    synchronized public boolean clean()
    {
        boolean ret = false;
        if (mMp != null) {
            Logger.log("MediaPlayer marked for GC");
            mMp = null;
            ret = true;
        }
        mIsInitialized = false;
        mPercent       = 0;
        return ret;
    }

    public void setVolume(float leftVolume, float rightVolume)
    {
        if (mIsInitialized)
            mMp.setVolume(leftVolume, rightVolume);
    }
}
