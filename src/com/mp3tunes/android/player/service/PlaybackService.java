package com.mp3tunes.android.player.service;

import com.binaryelysium.mp3tunes.api.ConcreteTrack;
import com.binaryelysium.mp3tunes.api.Locker;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.IdParcel;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.ParcelableTrack;
import com.mp3tunes.android.player.service.IPlaybackService;
import com.mp3tunes.android.player.util.RefreshSessionTask;
import com.mp3tunes.android.player.util.Timer;

import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.TelephonyManager;
import android.util.Log;


public class PlaybackService extends Service
{
    private MusicPlayStateLocker mPlayStateLocker;
    private PlaybackQueue        mPlaybackQueue;
    private PlaybackHandler      mPlaybackHandler;
    private HttpServer           mServer;
    private TrackDownloader      mDownloader;
    private GuiNotifier          mNotifier;
    private int                  mErrorCount;
    private Object               mChangingTrackLock;
    
    private Mp3TunesPhoneStateListener mPhoneStateListener;
    private TelephonyManager           mTelephonyManager;
    
    AsyncTask<Void, Void, Boolean> mChangingTrackAction;
    boolean                        mIsStarting;
    
    MyOnErrorListener mErrorListener;
    
    private Timer mTimer;
    
    @Override
    public void onCreate()
    {
        super.onCreate();

        mTimer = new Timer("Starting track");
        Timer timer  = new Timer("Playback Service onCreate");
        //we don't want the service to be killed while playing
        //later we need to determine a persistence strategy so that
        //can only set us in the foreground when we are actually playing
        //music
        setForeground(true);
        
        mErrorListener     = new MyOnErrorListener();
        mChangingTrackLock = new Object();
        mPlayStateLocker = new MusicPlayStateLocker(getBaseContext());
        mPlayStateLocker.lock();
        mDownloader      = new TrackDownloader(this, mChangingTrackLock, mErrorListener);
        mPlaybackQueue   = new PlaybackQueue(this, getBaseContext(), mDownloader);
        mPlaybackHandler = new PlaybackHandler(getBaseContext(), new MyOnInfoListener(), mErrorListener, new MyOnCompletionListener());
        mServer          = HttpServer.startServer(mPlaybackQueue);
        mNotifier        = new GuiNotifier(this, getBaseContext());
        
        mPhoneStateListener = new Mp3TunesPhoneStateListener(mPlaybackHandler);
        ContextWrapper cw = new ContextWrapper(getBaseContext());
        mTelephonyManager = (TelephonyManager)cw.getSystemService(Context.TELEPHONY_SERVICE);
        mTelephonyManager.listen(mPhoneStateListener, Mp3TunesPhoneStateListener.LISTEN_CALL_STATE);
        
        mIsStarting = true;
        timer.push();
    }

    @Override
    public void onDestroy()
    {
        Logger.log("destroying music service");
        mPlayStateLocker.release();
        mPlayStateLocker = null;
    }

    @Override
    public IBinder onBind(Intent arg0)
    {
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent)
    {
        mDeferredStopHandler.cancelStopSelf();
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        //if (mPlayerHandler.getTrack().isPlaying())
        //    return true;

        Logger.log("Unbinding music service");
        mDeferredStopHandler.deferredStopSelf();
        return true;
    }
    
    /**
     * Deferred stop implementation from the five music player for android:
     * http://code.google.com/p/five/ (C) 2008 jasta00
     */
    private final DeferredStopHandler mDeferredStopHandler = new DeferredStopHandler();

    private class DeferredStopHandler extends Handler
    {

        /* Wait 1 minute before vanishing. */
        public static final long DEFERRAL_DELAY = 1 * (60 * 1000);

        private static final int DEFERRED_STOP = 0;

        public void handleMessage(Message msg)
        {

            switch (msg.what) {
                case DEFERRED_STOP:
                    stopSelf();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }

        public void deferredStopSelf()
        {
            Logger.log(this, "deferredStopSelf", "Service stop scheduled "
                    + (DEFERRAL_DELAY / 1000 / 60) + " minutes from now.");
            sendMessageDelayed(obtainMessage(DEFERRED_STOP), DEFERRAL_DELAY);
        }

        public void cancelStopSelf()
        {

            if (hasMessages(DEFERRED_STOP) == true) {
                Logger.log(this, "cancelStopSelf", "Service stop cancelled.");
                removeMessages(DEFERRED_STOP);
            }
        }
    };

    private abstract class ChangingTrackAction extends AsyncTask<Void, Void, Boolean>
    {
        @Override
        protected void onPostExecute(Boolean ret)
        {
            mChangingTrackAction = null;
            mIsStarting          = false;
            if (ret == false) {
                mNotifier.stop(null);
            }
        }
    }
    
    private class NextTrackAction extends ChangingTrackAction
    {
        @Override
        protected Boolean doInBackground(Void... params)
        {
            Timer timer = new  Timer("Playback service next");
            Logger.log("next() waiting on lock");
            synchronized (mChangingTrackLock) {
                Logger.log("next() obtained lock");
                try {
                    CachedTrack t = mPlaybackQueue.nextPlaybackTrack();
                    Logger.log("next() playing: " + t.getFileKey());
                    if (!mPlaybackHandler.play(t)) {
                      //playback failure
                        return false;
                    }
                    mNotifier.nextTrack(t);
                } finally {
                    mPlaybackQueue.cleanFailures();
                    Logger.log("next() giving up lock");
                }
            }
            timer.push();
            return true;
        }
    };
    
    private class PreviousTrackAction extends ChangingTrackAction
    {
        @Override
        protected Boolean doInBackground(Void... params)
        {
            synchronized (mChangingTrackLock) {
                try {
                    Logger.log("changing to previous track");
                    CachedTrack t = mPlaybackQueue.previousPlaybackTrack();
                    //out of range
                    if (t == null) return false;
                    if (!mPlaybackHandler.play(t)) {
                      //playback failure
                        return false;
                    }
                    mNotifier.prevTrack(t);
                } finally {
                    mPlaybackQueue.cleanFailures();
                }
            }
            return true;
        }
    };
    
    private class PlayTrackAction extends ChangingTrackAction
    {
        int mPos; 
        public PlayTrackAction(int pos)
        {
            mPos = pos;
        }

        @Override
        protected Boolean doInBackground(Void... params)
        {
            
            Timer timer  = new Timer("Playback Service starting");
            synchronized (mChangingTrackLock) {
                try {
                    if (!mPlaybackQueue.setPlaybackPosition(mPos)) {
                        //out of range
                        return false;
                    }
                    CachedTrack t = mPlaybackQueue.getPlaybackTrack();
                    if (!mPlaybackHandler.play(t)) {
                        //playback failure
                        return false;
                    }
                    mNotifier.play(t);
                } finally {
                    mPlaybackQueue.cleanFailures();
                }   
            }
            return true;
        }
    };
    
    private final IPlaybackService.Stub mBinder = new IPlaybackService.Stub() {

        public void addToPlaybackList(IdParcel[] trackIds)
                throws RemoteException
        {
            mPlaybackQueue.addToPlaybackQueue(trackIds);
        }

        public void createPlaybackList(IdParcel[] trackIds)
                throws RemoteException
        {
            mPlaybackQueue.setPlaybackQueue(trackIds);
        }

        public int getBufferPercent() throws RemoteException
        {
            return mPlaybackQueue.getBufferPercent();
        }

        public long getDuration() throws RemoteException
        {
            try {
                return mPlaybackHandler.getDuration();
            } catch (NullPointerException e) {
                throw new NoCurrentTrackException();
            } catch (Exception e) {
                Logger.log(e);
            }
            return 0;
        }

        public long getPosition() throws RemoteException
        {
            try {
                return mPlaybackHandler.getPosition();
            } catch (NullPointerException e) {
                throw new NoCurrentTrackException();
            } catch (Exception e) {
                Logger.log(e);
            }
            return 0;
        }

        public int getQueuePosition() throws RemoteException
        {
            return mPlaybackQueue.getPlaybackPosition();
        }

        public ParcelableTrack getTrack() throws RemoteException
        {
            Track t;
            try {
                t = mPlaybackQueue.getPlaybackTrack();
            } catch (Exception e) {
                throw new NoCurrentTrackException();
            }
            return new ParcelableTrack(t);
        }

        public IdParcel[] getTrackIds() throws RemoteException
        {
            return mPlaybackQueue.getTrackIds();
        }

        public boolean isPaused() throws RemoteException
        {
            try {
                return mPlaybackHandler.isPaused();
            } catch (NullPointerException e) {
                throw new NoCurrentTrackException();
            } catch (Exception e) {
                throw new RemoteException();
            }
        }

        public boolean isPlaying() throws RemoteException
        {
            try {
                return mPlaybackHandler.isPlaying();
            } catch (NullPointerException e) {
                throw new NoCurrentTrackException();
            } catch (Exception e) {
                Logger.log(e);
            }
            return false;
        }

        public void next() throws RemoteException
        {
            if (mChangingTrackAction == null) {
                mChangingTrackAction = new NextTrackAction();
                mChangingTrackAction.execute((Void[])null);
            }
        }

        public ParcelableTrack nextTrack() throws RemoteException
        {
            return new ParcelableTrack(mPlaybackQueue.peekNextPlaybackTrack());
        }

        public void pause() throws RemoteException
        {
            mPlaybackHandler.pause();
            mNotifier.pause(mPlaybackQueue.getPlaybackTrack());
        }

        public void prev() throws RemoteException
        {
            if (mChangingTrackAction == null) {
                mChangingTrackAction = new PreviousTrackAction();
                mChangingTrackAction.execute((Void[])null);
            }
        }

        public boolean setPosition(int msec) throws RemoteException
        {
            // TODO Auto-generated method stub
            return false;
        }

        public void start() throws RemoteException
        {
            startAt(0);
        }

        public void startAt(int pos) throws RemoteException
        {
            if (mChangingTrackAction == null) {
                mChangingTrackAction = new PlayTrackAction(pos);
                mChangingTrackAction.execute((Void[])null);
            }
        }

        public void stop() throws RemoteException
        {
            finish();
            mNotifier.stop(null);
        }

        public void togglePlayback() throws RemoteException
        {
            CachedTrack t = mPlaybackQueue.getPlaybackTrack();
            if (mPlaybackHandler.isPlaying()) {
                mPlaybackHandler.pause();
                mNotifier.pause(t);
            } else {
                mPlaybackHandler.unpause();
                mNotifier.play(t);
            }
        }

        public PlaybackState getPlaybackState() throws RemoteException
        {
            long duration       = getDuration();
            long pos            = getPosition();
            int state           = getState(pos, duration);
            int buffpercent     = getBufferPercent();
            long remaining      = 1000 - (pos % 1000);
            long currentTime    = pos / 1000;
            long totalTime      = duration / 1000;
            int  bufferProgress = buffpercent * 10;
            
            int playbackProgress = 0;
            if (duration != 0)
                playbackProgress = (int)(1000 * pos / duration);
            
            return new PlaybackState(state, playbackProgress, bufferProgress, currentTime, totalTime, remaining);
        }
    };
    
    int getState(long pos, long duration)
    {
        if (mIsStarting) {
            return PlaybackState.State.STARTING;
        } else if (mChangingTrackAction != null) {
            return PlaybackState.State.CHANGING_TRACK;
        } else if (!(pos > 0 && duration > 0 && pos <= duration)) {
            return PlaybackState.State.BUFFERING;
        }
        return PlaybackState.State.PLAYING;
    }
    
    void finish()
    {
        synchronized (mChangingTrackLock) {
            Logger.log("calling finish");
            mPlaybackHandler.stop();
            mPlaybackHandler.finish();
            mPlaybackQueue.clear();
            mDownloader.clear();
        }
    }
    
    class MyOnErrorListener implements MediaPlayer.OnErrorListener
    {  
        public boolean onError(MediaPlayer mp, int what, int extra)
        {
            synchronized (this) {
            Logger.log("In Error handler");
            synchronized (mPlaybackHandler) {
                CachedTrack track = mPlaybackQueue.getPlaybackTrack();
                Logger.log("MediaPlayer got error name: " + track.getTitle());
                if (track.getStatus() == CachedTrack.Status.failed) {
                    mNotifier.sendPlaybackError(track, track.getErrorMessage());
                    finish();
                    return true;
                } else if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN) {
                    mPlaybackHandler.finish();
                    if (extra == -11) {
                        mNotifier.sendPlaybackError(track, "Timeout downloading track");
                        finish();
                        return true;
                    //} else if (extra == -1004) {
                    //    mPlaybackHandler.play(track);
                    //    return true;
                    } else {
                        mNotifier.sendPlaybackError(track, PlaybackErrorCodes.getError(extra));
                        finish();
                        return true;
                    }
                } else if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                    mPlaybackHandler.finish();
                    if (mErrorCount > 5) {
                        mNotifier.sendPlaybackError(track, "Unable to restart failed media server");
                        finish();
                        return true;
                    }
                    mPlaybackHandler.play(track);
                }
        
                mPlaybackHandler.finish();
                //returning false will call OnCompletionHandler
                return false;
            }
            }
        }
        
        synchronized public void onTrackFailed(MediaPlayer mp, int percent)
        {
            Logger.log("Track download failed at: " + percent);
            mPlaybackHandler.finish();
            synchronized (mChangingTrackLock) {
                CachedTrack t = mPlaybackQueue.nextPlaybackTrack();
                if (t == null) {
                    mNotifier.stop(null);
                    return;
                }
                try {
                    mNotifier.nextTrack(t);
                    if (!mPlaybackHandler.play(t)) {
                        mNotifier.sendPlaybackError(t, "Unable to play: " + t.getAlbumTitle() + " by: " + t.getArtistName());
                        return;
                    }
                } finally {
                    mPlaybackQueue.cleanFailures();
                }
            }
        }
        
        synchronized public void onTrackDownloadFailed(CachedTrack track)
        {
            try {
                CachedTrack current = mPlaybackQueue.getPlaybackTrack();
                if (track.getFileKey().equals(current.getFileKey())) {
                    mNotifier.sendPlaybackError(track, "Downloading track failed at " + track.mProgress.mProgress + " percent complete");
                    finish();
                }
            } catch (Exception e) {
                e.printStackTrace();
                mNotifier.sendPlaybackError(track, "Downloading track failed: " + track.mProgress.mProgress + " percent complete");
                finish();
            }
        }
    };
    
    private class MyOnInfoListener implements MediaPlayer.OnInfoListener
    {   
        public boolean onInfo(MediaPlayer mp, int what, int extra)
        {
            if (what == MediaPlayer.MEDIA_INFO_UNKNOWN)
                Logger.log("got info: " + PlaybackErrorCodes.getInfo(extra));
            else 
                Logger.log("Got unknown info");
            return false;
        }
    };
    
    class MyOnCompletionListener implements MediaPlayer.OnCompletionListener 
    {    
        public void onCompletion(MediaPlayer mp)
        {
            Logger.log("Track complete");
            mPlaybackHandler.finish();
            synchronized (mChangingTrackLock) {
                CachedTrack t = mPlaybackQueue.nextPlaybackTrack();
                if (t == null) {
                    mNotifier.stop(null);
                    return;
                }
                try {
                    mNotifier.nextTrack(t);
                    if (!mPlaybackHandler.play(t)) {
                        mNotifier.sendPlaybackError(t, "Unable to play: " + t.getAlbumTitle() + " by: " + t.getArtistName());
                        return;
                    }
                } finally {
                    mPlaybackQueue.cleanFailures();
                }
            }
        }
    };
    
    public class NoCurrentTrackException extends RemoteException
    {
        private static final long serialVersionUID = 1311021989686428501L;
    }
    
    public class PlaybackNotStartedExcpetion extends RemoteException
    {
        private static final long serialVersionUID = -3304117817714100228L;
    }
    
    public class PlaybackFailedExcpetion extends RemoteException
    {
        private static final long serialVersionUID = -3122092055316684884L;
    }
    
    public class PlaybackOutOfRangeException extends RemoteException
    {
        private static final long serialVersionUID = 5219878088325741490L;
    }
}
