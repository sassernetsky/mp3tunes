package com.mp3tunes.android.player.serviceold;

import java.util.Vector;

import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.IdParcel;
import com.mp3tunes.android.player.ParcelableTrack;

import com.mp3tunes.android.player.serviceold.ITunesService;
import com.mp3tunes.android.player.util.LazyTrack;
import com.mp3tunes.android.player.util.Timer;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;

public class Mp3tunesService extends Service
{

    @Override
    public IBinder onBind(Intent intent)
    {
        // TODO Auto-generated method stub
        return null;
    }
//    private PlayerHandler       mPlayerHandler;
//    private MusicPlayStateLocker mPlayStateLocker;
//    
//    @Override
//    public void onCreate()
//    {
//        super.onCreate();
//
//        //we don't want the service to be killed while playing
//        //later we need to determine a persistence strategy so that
//        //can only set us in the foreground when we are actually playing
//        //music
//        setForeground(true);
//        
//        mPlayStateLocker = new MusicPlayStateLocker(getBaseContext());
//        mPlayerHandler   = new PlayerHandler(this, getBaseContext());
//        mPlayStateLocker.lock();
//    }
//
//    @Override
//    public void onDestroy()
//    {
//        Logger.log("destroying music service");
//        mPlayerHandler.destroy();
//        mPlayerHandler = null;
//        mPlayStateLocker.release();
//        mPlayStateLocker = null;
//    }
//
//    @Override
//    public IBinder onBind(Intent arg0)
//    {
//        return mBinder;
//    }
//
//    @Override
//    public void onRebind(Intent intent)
//    {
//        mDeferredStopHandler.cancelStopSelf();
//        super.onRebind(intent);
//    }
//
//    @Override
//    public boolean onUnbind(Intent intent)
//    {
//        //if (mPlayerHandler.getTrack().isPlaying())
//        //    return true;
//
//        Logger.log("Unbinding music service");
//        mDeferredStopHandler.deferredStopSelf();
//        return true;
//    }
//    
//    /**
//     * Deferred stop implementation from the five music player for android:
//     * http://code.google.com/p/five/ (C) 2008 jasta00
//     */
//    private final DeferredStopHandler mDeferredStopHandler = new DeferredStopHandler();
//
//    private class DeferredStopHandler extends Handler
//    {
//
//        /* Wait 1 minute before vanishing. */
//        public static final long DEFERRAL_DELAY = 1 * (60 * 1000);
//
//        private static final int DEFERRED_STOP = 0;
//
//        public void handleMessage(Message msg)
//        {
//
//            switch (msg.what) {
//                case DEFERRED_STOP:
//                    stopSelf();
//                    break;
//                default:
//                    super.handleMessage(msg);
//            }
//        }
//
//        public void deferredStopSelf()
//        {
//            Logger.log(this, "deferredStopSelf", "Service stop scheduled "
//                    + (DEFERRAL_DELAY / 1000 / 60) + " minutes from now.");
//            sendMessageDelayed(obtainMessage(DEFERRED_STOP), DEFERRAL_DELAY);
//        }
//
//        public void cancelStopSelf()
//        {
//
//            if (hasMessages(DEFERRED_STOP) == true) {
//                Logger.log(this, "cancelStopSelf", "Service stop cancelled.");
//                removeMessages(DEFERRED_STOP);
//            }
//        }
//    };
//
//    private final ITunesService.Stub mBinder = new ITunesService.Stub() {
//
//        public int getBufferPercent() throws RemoteException
//        {
//            try {
//                return mPlayerHandler.getMediaPlayerTrack().getBufferPercent();
//            } catch (Exception e) {
//                return 0;
//            } 
//        }
//
//        public long getDuration() throws RemoteException
//        {
//            try {
//                return mPlayerHandler.getMediaPlayerTrack().getDuration();
//            } catch (Exception e) {
//                return 0;
//            }  
//        }
//
//        public long getPosition() throws RemoteException
//        {
//            try {
//                return mPlayerHandler.getMediaPlayerTrack().getPosition();
//            } catch (Exception e) {
//                return 0;
//            } 
//        }
//
//        public boolean isPlaying() throws RemoteException
//        {
//            try {
//                return mPlayerHandler.getMediaPlayerTrack().isPlaying();
//            } catch (Exception e) {
//                return false;
//            }
//        }
//
//        public void next() throws RemoteException
//        {
//            Timer timings = new Timer("Mp3TunesServices.next");
//            try {
//            if (!mPlayerHandler.playNext()) throw new RemoteException();
//            } finally {
//                timings.push();
//            }
//        }
//
//        public void pause() throws RemoteException
//        {
//            if (!mPlayerHandler.pause()) throw new RemoteException();
//        }
//
//        public void prev() throws RemoteException
//        {
//            if (!mPlayerHandler.playPrevious()) throw new RemoteException();
//        }
//        
//        public void start() throws RemoteException
//        {
//            if (!mPlayerHandler.playAt(0)) throw new RemoteException();
//        }
//
//        public void startAt(int pos) throws RemoteException
//        {
//            if (!mPlayerHandler.playAt(pos)) throw new RemoteException();
//        }
//
//        public void stop() throws RemoteException
//        {
//            if (!mPlayerHandler.stop()) throw new RemoteException();
//        }
//
//        public ParcelableTrack getTrack() throws RemoteException
//        {
//            try {
//                return new ParcelableTrack(mPlayerHandler.getTrack());
//            } catch (Exception e) {
//                throw new RemoteException();
//            }
//        }
//
//        public boolean isPaused() throws RemoteException
//        {
//            try {
//                return mPlayerHandler.getMediaPlayerTrack().isPaused();
//            } catch (Exception e) {
//                throw new RemoteException();
//            }     
//        }
//
//        public int getQueuePosition() throws RemoteException
//        {
//            try {
//                return mPlayerHandler.getQueuePosition();
//            } catch (Exception e) {
//                return -1;
//            }   
//        }
//
//        public void createPlaybackList(IdParcel[] trackIds) throws RemoteException
//        {
//            
//            PlaybackList list = new PlaybackList(getTracksForList(trackIds));
//            mPlayerHandler.setPlaybackList(list);
//        }
//
//        public void togglePlayback() throws RemoteException
//        {
//            mPlayerHandler.tooglePlayback();
//        }
//
//        public IdParcel[] getTrackIds() throws RemoteException
//        {
//            return getParcelsForTracks(mPlayerHandler.getTracks());
//        }
//
//        public ParcelableTrack nextTrack() throws RemoteException
//        {
//            try {
//                return new ParcelableTrack(mPlayerHandler.getNextTrack());
//            } catch (Exception e) {
//                throw new RemoteException();
//            }
//        }
//
//        public void addToPlaybackList(IdParcel[] trackIds)
//                throws RemoteException
//        {
//            mPlayerHandler.addToPlaybackList(getTracksForList(trackIds));
//        }
//
//        public boolean setPosition(int msec) throws RemoteException
//        {
//            return mPlayerHandler.setPosition(msec);
//        }
//        
//    };
//    
//    private Vector<MediaPlayerTrack> getTracksForList(IdParcel[] trackIds)
//    {
//        Vector<MediaPlayerTrack> tracks = new Vector<MediaPlayerTrack>();
//
//        for (IdParcel id : trackIds) {
//            MediaPlayerTrack track = new MediaPlayerTrack(new LazyTrack(id.getId(), getBaseContext()), this, getBaseContext());
//            tracks.add(track);
//        }
//        return tracks;
//    }
//    
//    private IdParcel[] getParcelsForTracks(Vector<Track> tracks)
//    {
//        IdParcel[] parcels = new IdParcel[tracks.size()];
//        for (int i = 0; i < tracks.size(); i++) {
//            parcels[i] = new IdParcel(tracks.elementAt(i).getId());
//        }
//        return parcels;
//    }
    
}
