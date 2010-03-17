package com.mp3tunes.android.player.service;

import java.io.IOException;

import com.binaryelysium.mp3tunes.api.InvalidSessionException;
import com.binaryelysium.mp3tunes.api.Locker;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.binaryelysium.mp3tunes.api.RemoteMethod;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.util.AddTrackToMediaStore;
import com.mp3tunes.android.player.util.RefreshSessionTask;

import android.app.Service;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;

public class MediaPlayerTrack
{
    private final Track       mTrack;
    
    private MediaPlayer mMp;
    private boolean     mIsInitialized;
    private boolean     mErroredOut;
    private boolean     mBuffered;
    private boolean     mPreCaching;
    private boolean     mPreparing;
    private int         mPercent;
    private Service     mService;
    private Context     mContext;
    
    private int mErrorCode;
    private int mErrorValue;
    
    private OnBufferingUpdateListener mOnBufferingUpdateListener;
    private OnCompletionListener      mOnCompletionListener;
    private OnErrorListener           mOnErrorListener;
    private OnInfoListener            mOnInfoListener;
    private OnPreparedListener        mOnPreparedListener;
    private TrackFinishedHandler      mTrackFinishedHandler;
    private BufferedCallback          mBufferedCallback;
    
    public MediaPlayerTrack(Track track, Service service, Context context)
    {
        mMp            = null;
        mTrack         = track;
        mIsInitialized = false;
        mErroredOut    = false;
        mBuffered      = false;
        mPreparing     = false;
        mPercent       = 0;
        mService       = service;
        mContext       = context;
        mErrorCode     = 0;
        mErrorValue    = 0;
        mPreCaching    = true;
        
        mOnBufferingUpdateListener = new MyOnBufferingUpdateListener();
        mOnCompletionListener      = new MyOnCompletionListener(this);
        mOnErrorListener           = new MyOnErrorListener();
        mOnPreparedListener        = new MyOnPreparedListener();
        mOnInfoListener            = new MyOnInfoListener();
    }
    
    public Track getTrack()
    {
        return mTrack;
    }
    
    synchronized public void setPreCaching(boolean b)
    {
        mPreCaching = b;
    }
   
    synchronized public boolean play()
    {
        if (!mIsInitialized) {
            
            if (mPreparing) {
                mPreCaching = false;
                return true;
          //state preparing
            } else {
                if (!prepare(true))
                  //state error
                    return false;
            }
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
        if (mIsInitialized || mPreparing) {
            //state stopped
            mIsInitialized = false;
            mPreparing     = false;
            mMp.stop();
            return true;
        }
        return true;
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
        return prepare(true);
    }
    
    synchronized private boolean start()
    {
        Logger.log("trying to start track: " + mTrack.getTitle());
        if (mIsInitialized) {
            Logger.log("starting track: " + mTrack.getTitle());
            mMp.start();
            return true;
        }
        return false;
    }
    
    synchronized private boolean prepare(boolean async)
    {
        Logger.log("preparing track: " + mTrack.getTitle());
        Logger.log("Artist:          " + mTrack.getArtistName());
        Logger.log("Album:           " + mTrack.getArtistName());
        
        try {
            String url = mTrack.getPlayUrl(Bitrate.getBitrate(mService, mContext));
            if (LockerId.class.isInstance(mTrack.getId())) {
                Logger.log("checking local store");
                if (AddTrackToMediaStore.isInStore(mTrack, mContext)) {
                    mOnBufferingUpdateListener.onBufferingUpdate(mMp, 100);
                    url = AddTrackToMediaStore.getTrackUrl(mTrack, mContext);
                }
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
            
        
            if (async) {
                //State preparing
                mPreparing = true;
                mMp.setOnPreparedListener(mOnPreparedListener);
                mMp.prepareAsync();
            } else {
                //State prepared
                mMp.prepare();
                Logger.log("MediaPlayer prepared");
                mIsInitialized = true;
            }
           
            //make sure volume is up
            mMp.setVolume(1.0f, 1.0f);
            
            return true;
        } catch (IllegalStateException e) {
            mErroredOut = true;
            e.printStackTrace();
        } catch (IOException e) {
            mErroredOut = true;
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
        mMp.setOnInfoListener(mOnInfoListener);
    }
    
    private class MyOnCompletionListener implements MediaPlayer.OnCompletionListener 
    {    
        private MediaPlayerTrack mMediaPlayerTrack;
        
        MyOnCompletionListener(MediaPlayerTrack track)
        {
            mMediaPlayerTrack = track;
        }
        
        public void onCompletion(MediaPlayer mp)
        {
            //If we are still in the initialized state then that means that we
            //completed our play back without an error
            Logger.log("MediaPlayer completed track");
            synchronized (mMediaPlayerTrack) {
                if (mTrackFinishedHandler == null) {
                    Logger.log("MediaPlayer in onCompletionHandler without a finished handler.  What do we do?");
                    return;
                }
                if (mIsInitialized) {
                    mTrackFinishedHandler.trackSucceeded(mMediaPlayerTrack);
                } else {
                    mTrackFinishedHandler.trackFailed(mMediaPlayerTrack);
                }
            }
        }
    };
   
    private class MyOnPreparedListener implements MediaPlayer.OnPreparedListener
    {

        public void onPrepared(MediaPlayer mp)
        {
            Logger.log("Prepared track: " + mTrack.getTitle());
            synchronized (MediaPlayerTrack.this) {
                mIsInitialized = true;
                if (!mPreCaching)
                    start();
            }
        }   
    };
    
    private class MyOnBufferingUpdateListener implements MediaPlayer.OnBufferingUpdateListener
    {
        long mOldPosition   = 0;
        int  mCount         = 0;
        boolean mBuffering = true;
        public void onBufferingUpdate(MediaPlayer mp, int percent)
        {
            synchronized (MediaPlayerTrack.this) {
                try {
                    if ((percent % 10) == 0) Logger.log(mTrack.getTitle() + ": Buffering: " + percent);
                    checkForEndlessBuffering(percent);
                    mPercent = percent;
                    tryRunBufferedCallback(percent);
                } catch (Exception e) {
                    //No error class this should be fatal
                    Logger.log(e);
                }
            }
        }
        
        public void runBufferedCallback()
        {
            mBuffered = true;
            if (mBufferedCallback != null) {
                mBufferedCallback.run();
            }
        }

        private void tryRunBufferedCallback(int percent)
        {
            if (mBuffered) return;
            if (percent == 100) {
                Logger.log("Buffering Got to 100% before buffered condition was met");
                runBufferedCallback();
            }
            long duration = getDuration();            
            if (duration <= 0) return;
            
            long bufferingPos = (duration * percent) / 100;
            long pos = getPosition();
            
            //try to start buffering the next song once we get one minute
            //ahead of our play back position
            if ((bufferingPos - pos) > 60000) {
                Logger.log("Buffering condition was met");
                runBufferedCallback();
            }
        }
        
        private void checkForEndlessBuffering(int percent)
        {
            mBuffering = setBuffering(mPercent, percent);
            if (mBuffering) {
                mCount++;
                Logger.log("buffering count set to: " + Integer.toString(mCount));
                if (mCount > 15 && !mMp.isPlaying()) {
                    Logger.log("Looks like we lost our network connection and the MediaPlayer does not realize it");
                    MediaPlayerTrack.this.stop();
                    mErroredOut = true;
                    mIsInitialized = false;
                    MediaPlayerTrack.this.mOnCompletionListener.onCompletion(mMp);
                }
            } else {
                mCount = 0;
            }
        }
        
        //This function tries to determine if we have stopped playback to buffer.
        //It is not even close to perfect, but hopefully it will give us some good
        //to help make a decision as to whether we are stuck.
        private boolean setBuffering(int oldPercent, int newPercent)
        {
            if (newPercent == 100) return false;
            long pos = MediaPlayerTrack.this.getPosition();
            if (oldPercent == newPercent) {
                if (mOldPosition == pos) {
                    if (positionCloseToPercent()) {
                        return true;
                    }
                }        
            }
            mOldPosition = pos;
            return false;
        }
        
        //It would be best if the player could tell us if we have reached
        //the end of the buffered content but it looks like we have to do this
        private boolean positionCloseToPercent()
        {
            long duration = MediaPlayerTrack.this.getDuration();
            if (duration == 0) return false;
            long playbackPercent = (mOldPosition * 100) / duration;
            if (Math.abs(mPercent - playbackPercent) <= 3) return true;
            return false;
        }
    };
    
    private class MyOnErrorListener implements MediaPlayer.OnErrorListener
    {  
        private boolean refreshAndTryAgain()
        {
            Logger.log("Trying to refresh and play again");
            RefreshSessionTask task = new RefreshSessionTask(mContext);
            if (task.doInForground()) {
                if (prepare(false)) {
                    if (play())
                        return true;
                }
            }
            return false;
        }
        
        private boolean handleUnknownErrors(int extra)
        {
            if (mPreCaching) return false; 
            mErroredOut = PlaybackErrorCodes.isFatalError(extra);
            
            //Error 26 is an authentication error it most likely means that the session
            //for the user has expired.  Here we will want to try to refresh the session
            //and try the song again.
            if (extra == -26 && !mIsInitialized) {
                return refreshAndTryAgain();
            } else if (extra == -11){ 
                mErroredOut = false;
                return false;
            } else if (extra == -1) {
                Locker l = new Locker();
                if (!l.testSession()) {
                    return refreshAndTryAgain();
                }
            }
            return false;
        }
        
        public boolean onError(MediaPlayer mp, int what, int extra)
        {
            Logger.log("MediaPlayer got error name: " + mTrack.getTitle());
            //State error
            synchronized (MediaPlayerTrack.this) {
                if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN) {
                    if (handleUnknownErrors(extra)) return true;
                }
            
                mIsInitialized = false;
                mPreparing     = false;
                mErroredOut    = true;
                mErrorCode     = what;
                mErrorValue    = extra;
            
                //state idle
                mMp.reset();
                //returning false will call OnCompletionHandler
                return false;
            }
        }
    };
    
    private class MyOnInfoListener implements MediaPlayer.OnInfoListener
    {  
        public boolean onInfo(MediaPlayer mp, int what, int extra)
        {
            if (what == 1)
                Logger.log(PlaybackErrorCodes.getInfo(extra));
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
        if (mIsInitialized) {
            return mMp.getDuration();
        }
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
    
    public boolean erroredOut()
    {
        return mErroredOut;
    }

    public int getErrorValue()
    {
        return mErrorValue;
    }

    public int getErrorCode()
    {
        return mErrorCode;
    }

    synchronized public void seekTo(int i)
    {
        if (mIsInitialized)
            mMp.seekTo(i);
        
    }
    
}
