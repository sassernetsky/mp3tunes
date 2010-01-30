package com.mp3tunes.android.player.service;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.binaryelysium.mp3tunes.api.Locker;
import com.binaryelysium.mp3tunes.api.LockerContext;
import com.binaryelysium.mp3tunes.api.Session;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.CurrentPlaylist;
import com.mp3tunes.android.player.MP3tunesApplication;
import com.mp3tunes.android.player.util.RefreshSessionTask;

public class PlayerHandlerOld
{
    private MediaPlayer          mMp = new MediaPlayer();
    private Locker               mLocker;
    private Service              mService;
    private Context              mContext;
    
    private int     mServiceState = STATE.STOPPED;
    private boolean mActive       = false;
    private int     mBufferPercent;
    
    private CurrentPlaylist mCp;
    
    private int     mCurrentPosition = 0;
    private Track   mCurrentTrack    = null;
    
    private TrackSkipper mSkipper    = null;
    private TrackRetrier mRetrier    = null;
    
    private Prefetcher   mPrefetcher = null;
    
    private boolean      mLastTrackSuccessful   = true;
    private int          mLastTrackServiceState = STATE.STOPPED;
    
    Mp3TunesPhoneStateListener mPhoneStateListener;
    private TelephonyManager   mTelephonyManager;
    
    private OnCompletionListener      mOnCompletionListener;
    private OnBufferingUpdateListener mOnBufferingUpdateListener;
    private OnPreparedListener        mOnPreparedListener;
    private OnErrorListener           mOnErrorListener;
    
    public static final String UNKNOWN = "com.mp3tunes.android.player.unknown";

    public static class ERROR_ACTION
    {
        public final static int SKIPPING = 0;
        public final static int RETRYING = 1;
        public final static int STOPPING = 2;
    }
    
    private static class STATE
    {
        private final static int STOPPED     = 0;
        private final static int PREPARING   = 1;
        private final static int PLAYING     = 2;
        private final static int SKIPPING    = 3;
        private final static int PAUSED      = 4;
        private final static int SYSTEMPAUSE = 5;
        private final static int IDLE        = 6;
        
        static private String toString(int state) 
        {
            switch (state) {
                case PREPARING:
                    return "PREPARING";
                case  PLAYING:
                    return "PLAYING";
                case  IDLE:
                    return "IDLE";
                case  PAUSED:
                    return "PAUSED";
                case  SKIPPING:
                    return "SKIPPING";
                case  STOPPED:
                    return "STOPPED";
                case  SYSTEMPAUSE:
                    return "SYSTEMPAUSE";
                default:
                    return "UNKNOWN";
            }
        }
    }
    
    private static class MyTelephonyManager {
        private final static int NETWORK_TYPE_1xRTT  = 7;
        private final static int NETWORK_TYPE_CDMA   = 4;
        private final static int NETWORK_TYPE_EVDO_0 = 5;
        private final static int NETWORK_TYPE_EVDO_A = 6;
        private final static int NETWORK_TYPE_HSDPA  = 8;
        private final static int NETWORK_TYPE_HSPA   = 10;
        private final static int NETWORK_TYPE_HSUPA  = 9;
        
    }
    
    public PlayerHandlerOld(Service service, Context context) throws PlaybackException
    {
        mService = service;
        mContext = context;
        
        mLocker = MP3tunesApplication.getInstance().getLocker();
        
        //mPhoneStateListener = new Mp3TunesPhoneStateListener(this);
        
        ContextWrapper cw = new ContextWrapper(mContext);
        mTelephonyManager = (TelephonyManager) cw.getSystemService(Context.TELEPHONY_SERVICE);
        mTelephonyManager.listen(mPhoneStateListener, Mp3TunesPhoneStateListener.LISTEN_CALL_STATE);

        // establish a connection with the database
        try {
            mCp = new CurrentPlaylist(context);
        } catch (Exception e) {
            // database connection failed.
            // Show an error and exit gracefully.
            Logger.log(e);
            mServiceState = STATE.STOPPED;
            throw new PlaybackException("Unable to get current playlist db");
        }
        mPrefetcher = new Prefetcher();
    }
    
    public void destroy()
    {
        mCp.close();
        mMp.stop();
        mMp.release();
    }
    
    public void setOnCompletionListener(OnCompletionListener listener)
    {
        mOnCompletionListener = listener;
    }
    
    public void setOnBufferingUpdateListener(OnBufferingUpdateListener listener)
    {
        mOnBufferingUpdateListener = listener;
    }
    
    public void setOnPreparedListener(OnPreparedListener listener)
    {
        mOnPreparedListener = listener;
    }
    
    public void setOnErrorListener(OnErrorListener listener)
    {
        mOnErrorListener = listener;
    }
    
    //Player control methods
    
    synchronized public Track start(MediaPlayer mp) throws PlaybackException
    {
        if (mp == mMp) {
            if (mServiceState == STATE.PREPARING) {
                mp.start();
                
                //If we are paused and the last track ended with an error
                //we will either try to replay it or move to the next track
                //automatically.  In both of these cases we want to make sure
                //that we respect the pause request.
                if (!mLastTrackSuccessful) {
                    if (mLastTrackServiceState == STATE.SYSTEMPAUSE) {
                        mServiceState = STATE.SYSTEMPAUSE;
                        Logger.log("paused");
                        mp.pause();
                    } else if (mLastTrackServiceState == STATE.PAUSED) {
                        mServiceState = STATE.PAUSED;
                        Logger.log("paused");
                        mp.pause();
                    } else 
                        mServiceState = STATE.PLAYING;
                } else 
                    mServiceState = STATE.PLAYING;
                return mCurrentTrack;
            } else {
                throw new PlaybackException("tried to start playback from an illegal state");
            }
        }
        return null;
    }

    synchronized public Track prepareTrack(int index) throws PlaybackException
    {
        if (mCp.getQueueSize() <= 0 || mCp.getQueueSize() < index)
            throw new PlaybackException("Queue is empty");
        
        return prepareTrack(mCp.getTrackQueue(index), index, mMp);
    }
    
    synchronized private Track prepareTrack(Track track, int playlist_index, MediaPlayer p) throws PlaybackException
    {
        try {
            if (mServiceState == STATE.PREPARING || track == null)
                return null;

            mCurrentTrack    = track;
            mCurrentPosition = playlist_index;
            Logger.log(this, "prepareTrack", "Current position in prepare track: " + Integer.toString(mCurrentPosition));

            int bitrate = chooseBitrate();
            String url = track.getPlayUrl() + "&fileformat=mp3" + "&bitrate=" + bitrate;
            Logger.log(this, "prepareTrack", "Playing: " + track.getTitle());
            p.reset();
            url = updateUrlSession(url);
            p.setAudioStreamType(AudioManager.STREAM_MUSIC);
            p.setDataSource(url);
            p.setOnCompletionListener(mOnCompletionListener);
            p.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
            p.setOnPreparedListener(mOnPreparedListener);
            p.setOnErrorListener(mOnErrorListener);
          
            // We do this because there has been bugs in our phonecall fade code
            // that resulted in the music never becoming audible again after a
            // call.
            // Leave this precaution here please.
            p.setVolume(1.0f, 1.0f);

            if (p == mMp)
                mServiceState = STATE.PREPARING;
            p.prepareAsync();
        } catch (IllegalStateException e) {
            Logger.log(e);
            throw new PlaybackException("Tried to prepare from an Illegal State");
        } catch (IOException e) {
            Logger.log(e);
            throw new PlaybackException("IO problem");
        }
        return track;
    }

    private String updateUrlSession(String url)
    {
        if (mLocker == null)
            return url;
        //String sid = mLocker.getCurrentSession().getSessionId();
        Session session = LockerContext.instance().getSession();
        if (session != null) {
            String sid = session.getSessionId();
            url = url.replaceFirst("sid=(.*?)&", "sid=" + sid + "&");
            Logger.log(this, "updateUrlSession", "fixed url: " + url);
        }
        return url;
    }

    private int chooseBitrate()
    {
        try {
        int bitrate = Integer.valueOf(PreferenceManager
                .getDefaultSharedPreferences(mService).getString("bitrate", "-1"));

        if (bitrate == -1) {
            // Resources resources = getResources();
            // int[] vals = resources.getIntArray(R.array.rate_values);
            // TODO Figure out why the above code does not work
            int[] vals = new int[] { -1, 24000, 56000, 96000, 128000, 192000 };

            ContextWrapper      cw = new ContextWrapper(mContext);
            ConnectivityManager cm = (ConnectivityManager)cw.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo network = cm.getActiveNetworkInfo();
            int type = network.getType();
            if (type == ConnectivityManager.TYPE_WIFI) {
                bitrate = vals[4]; // 5 = 128000
            } else if (type == ConnectivityManager.TYPE_MOBILE) {
                TelephonyManager tm = (TelephonyManager) cw.getSystemService(Context.TELEPHONY_SERVICE);

                int nType = tm.getNetworkType();
                switch (nType) {
                    case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                        return vals[2]; // 1 = 24000
                    case TelephonyManager.NETWORK_TYPE_GPRS:
                        return vals[2]; // 1 = 24000
                    case TelephonyManager.NETWORK_TYPE_EDGE:
                        return vals[2]; // 1 = 24000
                    case TelephonyManager.NETWORK_TYPE_UMTS:
                        return vals[2]; // 2 = 56000
                    case MyTelephonyManager.NETWORK_TYPE_1xRTT:
                        return vals[2]; // 1 = 24000
                    case MyTelephonyManager.NETWORK_TYPE_CDMA:
                        return vals[2]; // 1 = 24000
                    case MyTelephonyManager.NETWORK_TYPE_EVDO_0:
                        return vals[2]; // 1 = 24000
                    case MyTelephonyManager.NETWORK_TYPE_EVDO_A:
                        return vals[2]; // 2 = 56000
                    case MyTelephonyManager.NETWORK_TYPE_HSDPA:
                        return vals[3]; // 3 = 96000
                    case MyTelephonyManager.NETWORK_TYPE_HSPA:
                        return vals[2]; // 2 = 56000
                    case MyTelephonyManager.NETWORK_TYPE_HSUPA:
                        return vals[3]; // 3 = 96000
                    default:
                        Logger.log(this, "chooseBitrate", "Network Type: " + Integer.toString(nType));
                        bitrate = 0;
                }
            }
        }
        return bitrate;
        } catch (Exception e) {
            return 0;
        }
    }

    synchronized public Track prevTrack() throws PlaybackException, ChangeTrackException
    {
        if (mServiceState == STATE.SKIPPING)
            return null;
        
        int   pos = -1;
        Track t   = null;
        try {
            mServiceState = STATE.SKIPPING;

            // Check again, if size still == 0 then the play list is empty.
            if (mCp.getQueueSize() > 0) {
                pos = mCurrentPosition - 1;
                if (pos < 1) pos = 1;
                t = mCp.getTrackQueue(pos);
                if (tryPrefetcherLast()) return mCurrentTrack;
                prepareTrack(t, pos, mMp);
                return t;
            } else {
                throw new ChangeTrackException("No previous track");
            }
        } finally {
            Logger.logTrack(this, "prevTrack", pos, t);
        }
    }

    synchronized public Track nextTrack() throws PlaybackException, ChangeTrackException
    {
        if (mServiceState == STATE.SKIPPING)
            return null;        
        
        int   pos = -1;
        Track t   = null;
        try {
            mServiceState = STATE.SKIPPING;
        
            // Check again, if size still == 0 then the play list is empty.
            if (mCp.getQueueSize() > mCurrentPosition) {
                pos = mCurrentPosition + 1;
                t = mCp.getTrackQueue(pos);
                
                if (tryPrefetcher()) return mCurrentTrack;
                prepareTrack(t, pos, mMp);
                return t;
            } else {
                // play list finished
                throw new ChangeTrackException("No next track");
            }
        } finally {
            Logger.logTrack(this, "nextTrack", pos, t);
        }
    }

    synchronized public void systemPause()
    {
        pause(STATE.SYSTEMPAUSE);
    }
    
    synchronized public Track pause()
    {
        return pause(STATE.PAUSED);
    }
    
    synchronized public Track pause(int state)
    {
        if (mServiceState == STATE.STOPPED ||
                mServiceState == STATE.PAUSED && state == STATE.SYSTEMPAUSE)
            return null;
        
        if (mServiceState == STATE.PAUSED || mServiceState == STATE.SYSTEMPAUSE) {
            mMp.start();
            mServiceState = STATE.PLAYING;
        } else {
            if (mActive == false) {
                Logger.log("paused");
                mMp.pause();
            }
                mServiceState = state;
        }
        return mCurrentTrack;
    }

    synchronized public Track stop()
    {
        if (mServiceState == STATE.PLAYING) {
            mMp.stop();
        }
        mServiceState = STATE.STOPPED;
        return mCurrentTrack;
    }

    synchronized public boolean setCurrentPosition(int msec)
    {
        if (mServiceState != STATE.PLAYING && mServiceState != STATE.PAUSED)
            return false;

        mMp.seekTo(msec);
        // because when we move to an other pos the music starts
        if (mServiceState == STATE.PAUSED) {
            Logger.log("pausing");
            mMp.pause();
        }
        return true;

    }
    
    synchronized private void handleDeadServerError()
    {
        mMp = new MediaPlayer();
    }
    
    synchronized private boolean handleMediaError(int extra)
    {
        if (extra == -26 || extra == -1) {
            if (tryRetry()) return true;
        } else if (extra == -11) {
            return false;
        }
        return false;
    }
    
    synchronized public int handleError(MediaPlayer mp, int what, int extra)
    {
        String error = "unknown";
        String state = "unknown";

        mLastTrackSuccessful   = false;
        mLastTrackServiceState = mServiceState;
        
        try {
            state = STATE.toString(mServiceState);
            //Handle the Errors we know how to deal with
            if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN) {
                if (handleMediaError(extra)) return ERROR_ACTION.RETRYING;
            } else if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                handleDeadServerError();
                return trySkip();
            }

            mServiceState = STATE.STOPPED;
            return trySkip();
        } finally {
            //Make sure that we log what happened
            if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN)
                error = PlaybackErrorCodes.getError(extra);
            if (error == null) 
                error = "Error Code: " + Integer.toString(extra);
            Logger.log(this, "handleError", "MediaPlayer returned error: " + error + " while in state: " + state);
        }
    }
 
    synchronized private String parseFromInt(int num)
    {
        String val;
        try {
            val = Integer.toString(num);
        } catch (Exception e) {
            val = UNKNOWN;
        }
        return val;
    }
    
    synchronized public String[] getMetadata()
    {
        String[] data;
        if (mCurrentTrack == null)
            data = new String[] { UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN,
                    UNKNOWN, UNKNOWN };
        else {
            String id         = parseFromInt(mCurrentTrack.getId());
            String artistId   = parseFromInt(mCurrentTrack.getArtistId());
            String albumId    = parseFromInt(mCurrentTrack.getAlbumId());
            String title      = mCurrentTrack.getTitle();
            String artistName = mCurrentTrack.getArtistName();
            String albumTitle = mCurrentTrack.getAlbumTitle();
         
            data = new String[] { 
                    title,
                    id,
                    artistName,
                    artistId,
                    albumTitle,
                    albumId
            };
        }
        return data;
    }
    
    synchronized public void setVolume(float left, float right)
    {
        mMp.setVolume(left, right);
    }
    
    synchronized public long getDuration() 
    {
        if (mServiceState != STATE.PLAYING && mServiceState != STATE.PAUSED)
            return 0;
        else
            return mMp.getDuration();
    }

    synchronized public long getPosition() 
    {
        if (mServiceState != STATE.PLAYING && mServiceState != STATE.PAUSED)
            return 0;
        else
            return mMp.getCurrentPosition();
    }
    
    synchronized public Track getCurrentTrack()
    {
        return mCurrentTrack;
    }

    synchronized public boolean isPaused()
    {
        return mServiceState == STATE.PAUSED;
    }
    
    synchronized public boolean isSystemPaused()
    {
        return mServiceState == STATE.SYSTEMPAUSE;
    }

    synchronized public int getQueuePosition()
    {
        return mCurrentPosition;
    }
    
    synchronized public void updateBuffering(MediaPlayer mp, int percent) {
        if (mp == mMp)
            mBufferPercent = percent;
        if (percent == 100 && mPrefetcher.empty()) {
            prefetchNextTrack(mCurrentPosition + 1);
            Logger.log(this, "updateBuffering", "Trying to prefetch: " + Integer.toString(mCurrentPosition + 1));
        }
    }
     
    public class PlaybackException extends Exception {
        /**
         * 
         */
        private static final long serialVersionUID = 2434639988504705066L;

        public PlaybackException(String message) {
            super(message);
        }
    }
    
    public class ChangeTrackException extends Exception {
        /**
         * 
         */
        private static final long serialVersionUID = -3881847611458506497L;

        public ChangeTrackException(String message) {
            super(message);
        }
    }

    synchronized public boolean isPlaying()
    {
        return mServiceState != STATE.STOPPED;
    }

    synchronized public int getBufferPercent()
    {
        return mBufferPercent;
    }

    synchronized private int trySkip() {
        if (mSkipper == null) {
            mSkipper = new TrackSkipper();
        } else {
            if (!mSkipper.canSkip()) {
                mSkipper = null;
                return ERROR_ACTION.STOPPING;
            }
        }
        mSkipper.skip();
        return ERROR_ACTION.SKIPPING;
    }
    
    private class TrackSkipper
    {   
        int mNumberSkipped = 0;
        
        boolean canSkip()
        {
            return mNumberSkipped < 4;
        }
        
        public void skip()
        {
                mNumberSkipped++;
        }
        
    }
    
    synchronized private boolean tryRetry()
    {
        if (mRetrier == null) {
            mRetrier = new TrackRetrier();
            new RefreshSessionTask(mContext, mLocker, mRetrier.getMethod("retry"), mRetrier.getMethod("retryFailed"), mRetrier).execute((Void) null);
            return true;
        } else {
            if (mRetrier.canRetry()) {
                new RefreshSessionTask(mContext, mLocker, mRetrier.getMethod("retry"), mRetrier.getMethod("retryFailed"), mRetrier).execute((Void) null);
                return true;
            } else {
            mRetrier = null;
            }
        }
        return false;
    }
    
    public class TrackRetrier
    {   
        int mNumberRetried = 0;
        
        boolean canRetry()
        {
            return mNumberRetried < 3;
        }
        
        public boolean retry()
        {
            try {
                mServiceState = STATE.STOPPED;
                mMp.reset();
                prepareTrack(mCurrentTrack, mCurrentPosition, mMp);
                mNumberRetried++;
                return true;
            } catch (PlaybackException e) {
                Logger.log(e);
            }
            
            return false;
        }
        
        public boolean retryFailed()
        {
            trySkip();
            return false;
        }
        
        @SuppressWarnings("unchecked")
        public Method getMethod(String method)
        {
            try {
                Class cls = this.getClass();
                Method m = cls.getMethod(method, (Class[])null);
                return m;
            } catch (SecurityException e) {
                Logger.log(e);
            } catch (NoSuchMethodException e) {
                Logger.log(e);
            }
            return null;
            
        }   
    }
    
    
    
    synchronized public void playbackSucceeded()
    {
        if (mSkipper != null) mSkipper = null;
        if (mRetrier != null) mRetrier = null;
        mPrefetcher.setPrevious(mMp, mCurrentPosition, mCurrentTrack);
    }
 
    
    synchronized private void prefetchNextTrack(int pos)
    {
        Logger.log(this, "prefetchNextTrack", "prefetching next track");

        // Check again, if size still == 0 then the playlist is empty.
        if (mCp.getQueueSize() >= pos) {;
            Track track = mCp.getTrackQueue(pos);
            mPrefetcher.beginPrefetch(pos, track);
        }
    }
    
    synchronized private boolean tryPrefetcherLast() throws PlaybackException
    {
        Prefetcher.PrefetchInfo p = mPrefetcher.getPrevious();
        if(p != null) {
            Logger.log(this, "tryPrefetcherLast", "Trying prefetcher has track");
            mPrefetcher.clear();
            mPrefetcher.beginPrefetch(mCurrentPosition, mCurrentTrack);
            mCurrentPosition--;
            mCurrentTrack = mCp.getTrackQueue(mCurrentPosition);
            
            //make sure that the track in the prefetcher is the track we are trying to play
            if (mCurrentPosition != p.mPosition || !mCurrentTrack.sameRemoteFile(p.mTrack)) {
                Logger.log(this, "tryPrefetcherLast", "Next Prefetch track does not match track to play");
                return false;
            }
            mMp = p.mPlayer;
            mServiceState = STATE.PREPARING;
            mMp.setOnCompletionListener(mOnCompletionListener);
            mMp.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
            mMp.setOnPreparedListener(mOnPreparedListener);
            mMp.setOnErrorListener(mOnErrorListener);
            mMp.prepareAsync();
            return true;
        }
        return false;
    }
    
    synchronized private boolean tryPrefetcher() throws PlaybackException
    {
        Logger.log(this, "tryPrefetcher", "Trying prefetch queue");
        //PlaybackException e = new PlaybackException("fake");
        //Logger.log(e, "There should be a stack trace here");
        //See if the prefetcher has the next track already
        Prefetcher.PrefetchInfo p = mPrefetcher.getNext();
        if(p != null) {
            Logger.log(this, "tryPrefetcher", "Trying prefetcher has track");
            mCurrentPosition++;
            mCurrentTrack = mCp.getTrackQueue(mCurrentPosition);
            
            //make sure that the track in the prefetcher is the track we are trying to play
            if (mCurrentPosition != p.mPosition || !mCurrentTrack.sameRemoteFile(p.mTrack)) {
                Logger.log(this, "tryPrefetcher", "Next Prefetch track does not match track to play");
                mPrefetcher.clear();
                return false;
            }
            mMp = p.mPlayer;
            mServiceState = STATE.PREPARING;
            mMp.setOnCompletionListener(mOnCompletionListener);
            mMp.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
            mMp.setOnPreparedListener(mOnPreparedListener);
            mMp.setOnErrorListener(mOnErrorListener);
            start(mMp);
            return true;
        }
        return false;
    }
    
    //TODO: Long term we will want to unify the prefetcher and the playback queue.  But for now we
    //are hacking it together like this until we decide on a long term strategy for the playback queue
    private class Prefetcher
    {
        private LinkedList<PrefetchInfo> mQueue;
        private PrefetchInfo             mLast = null;
        private boolean            mPreparing;
        private int                mMaxQueueSize;
        private int                mCurrentBuffering = 0;
        private int                mTimeout          = 0;
        
        MediaPlayer.OnPreparedListener        mPreparedListener        = new OnPreparedListener();
        MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener = new OnBufferingUpdateListener();
        MediaPlayer.OnErrorListener           mErrorListener           = new OnErrorListener();
        
        public class PrefetchInfo
        {
            PrefetchInfo(MediaPlayer p, Track t, int pos) {
                mPlayer   = p;
                mTrack    = t;
                mPosition = pos;
            }
            public MediaPlayer mPlayer;
            public Track       mTrack;
            public int         mPosition;
        }
        
        public Prefetcher()
        {
            
            mQueue        = new LinkedList<PrefetchInfo>();
            mPreparing    = false;
            mMaxQueueSize = 1;
        }
        
        synchronized public void setPrevious(MediaPlayer mp, int pos, Track t)
        {
            mLast = new PrefetchInfo(mp, t, pos);
        }
        
        synchronized public PrefetchInfo getPrevious()
        {
            return mLast;
        }
        
        synchronized public boolean empty()
        {
            return mQueue.isEmpty();
        }

        synchronized public void beginPrefetch(int pos, Track t)
        {       
            if (mQueue.size() >= mMaxQueueSize) return;
            PrefetchInfo info = new PrefetchInfo(new MediaPlayer(), t, pos);
            mPreparing    = true;
            mQueue.offer(info);
            try {
                int bitrate = chooseBitrate();
                String url = t.getPlayUrl() + "&fileformat=mp3" + "&bitrate=" + bitrate;
                url = updateUrlSession(url);
                info.mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                info.mPlayer.setDataSource(url);
                info.mPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
                info.mPlayer.setOnPreparedListener(mPreparedListener);
                info.mPlayer.setOnErrorListener(mErrorListener);
                info.mPlayer.prepareAsync();
            } catch (IllegalStateException e) {
                Logger.log(e);
            } catch (IOException e) {
                Logger.log(e);
            }
        }
        
        synchronized public PrefetchInfo getNext()
        {
                //If the queue has one member and it is still preparing
                //then empty it an return null so that next track will run
                //through its normal process.  We do this to avoid blocking
                //and the potential problems involved
                if (mQueue.size() == 1 && mPreparing) {
                    mQueue.poll();
                    return null;
                }
            
                PrefetchInfo info = mQueue.poll();
                if (info == null) return null;
            
                //Here we get the position of the next track
                int nextTrackIndex;
                if (mQueue.size() > 0)
                    nextTrackIndex = mQueue.getLast().mPosition + 1;
                else
                    nextTrackIndex = info.mPosition + 1;
                prefetchNextTrack(nextTrackIndex);
                
                return info;
        }

        synchronized public void clear()
        {
            mQueue.clear();
        }
        
        private boolean timedOut(int percent)
        {
            if (mCurrentBuffering == percent) {
                mTimeout++;
                if (mTimeout >= 20) {
                    mTimeout = 0;
                    removeLast();
                    return true;
                }
            } else {
                mTimeout = 0;
            }
            return false;
        }
        
        private void removeLast()
        {
            PrefetchInfo info = mQueue.removeLast();
            mCp.removeQueueItem(info.mPosition, info.mPosition);
        }
           
        private class OnPreparedListener implements MediaPlayer.OnPreparedListener
        {
            synchronized public void onPrepared(MediaPlayer mp)
            {
                Logger.log("Prefetcher prepared track");
                mPreparing = false;
            }  
        }
        
        private class OnBufferingUpdateListener implements MediaPlayer.OnBufferingUpdateListener
        {
            public void onBufferingUpdate(MediaPlayer mp, int percent)
            {
                
                if (timedOut(percent)) {
                    Logger.log(this, "onBufferingUpdate", "Prefetch timed out");
                    prefetchNextTrack(mQueue.getLast().mPosition + 1);
                }
                if (percent % 10 == 0) Logger.log("Prefetch buffering: " + Integer.toString(percent));
                if (percent == 100) {
                    Logger.log(this, "onBufferingUpdate", "PreFetch Done");
                    prefetchNextTrack(mQueue.getLast().mPosition + 1);
                }
            }   
        }
        
        private class OnErrorListener implements MediaPlayer.OnErrorListener
        {
            synchronized public boolean onError(MediaPlayer mp, int what, int extra)
            {
                Logger.log("Prefetcher error");
                removeLast();
                
                return false;
            }
        }
        
    }

    synchronized public void emptyPrefetcher()
    {
        mPrefetcher.clear();
    }

    public void setLastTrackSuccessful()
    {
        this.mLastTrackSuccessful = true;
    }
}
