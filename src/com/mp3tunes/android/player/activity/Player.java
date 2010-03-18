package com.mp3tunes.android.player.activity;

import java.io.IOException;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.util.TimingLogger;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.binaryelysium.mp3tunes.api.InvalidSessionException;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.binaryelysium.mp3tunes.api.RemoteMethod;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.LocalId;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.RemoteImageHandler;
import com.mp3tunes.android.player.RemoteImageView;
import com.mp3tunes.android.player.content.TrackGetter;
import com.mp3tunes.android.player.service.GuiNotifier;
import com.mp3tunes.android.player.util.AddTrackToLocker;
import com.mp3tunes.android.player.util.AddTrackToMediaStore;
import com.mp3tunes.android.player.util.Worker;


public class Player extends Activity
{
    private static final int REFRESH = 0;
    private static final int BUFFERING_DIALOG = 0;  

    private ImageButton mPrevButton;
    private ImageButton mPlayButton;
    private ImageButton mNextButton;
    private ImageButton mStopButton;
    private RemoteImageView mAlbum;
    private Bitmap   mImage;
    private TextView mCurrentTime;
    private TextView mTotalTime;
    private TextView mArtistName;
    private TextView mTrackName;
    private ProgressBar mProgress;
    private ProgressDialog mBufferingDialog;
    
    private long mDuration;
    private boolean paused;
    private boolean mShowingOptions;
    
    private Worker mAlbumArtWorker;
    private RemoteImageHandler mAlbumArtHandler;
    private IntentFilter mIntentFilter;
    private AsyncTask<Void, Void, Boolean> mArtTask;
    
    private TrackAdder  mTrackAdder;
    private TrackPutter mTrackPutter;
    
    @Override
    public void onCreate( Bundle icicle )
    {
        super.onCreate(icicle);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.audio_player);
        Music.ensureSession(this);
        
        mShowingOptions = false;
        
        mCurrentTime = (TextView)findViewById(R.id.currenttime);
        mTotalTime   = (TextView)findViewById(R.id.totaltime);
        mAlbum       = (RemoteImageView)findViewById(R.id.album);
        mArtistName  = (TextView)findViewById(R.id.track_artist);
        mTrackName   = (TextView)findViewById(R.id.track_title);
        mProgress    = (ProgressBar)findViewById(android.R.id.progress);
        mProgress.setMax(1000);
        
        mPrevButton = ( ImageButton ) findViewById( R.id.rew );
        mPlayButton = ( ImageButton ) findViewById( R.id.play );
        mNextButton = ( ImageButton ) findViewById( R.id.fwd );
        mStopButton = ( ImageButton ) findViewById( R.id.stop );
        
        mPrevButton.setOnClickListener(mPrevListener);
        mPlayButton.setOnClickListener(mPlayListener);
        mNextButton.setOnClickListener(mNextListener);
        mStopButton.setOnClickListener(mStopListener);
        
        mPlayButton.requestFocus();
        
        mAlbumArtWorker  = new Worker("album art worker");
        mAlbumArtHandler = new RemoteImageHandler(mAlbumArtWorker.getLooper(), mHandler);
        if (icicle != null) {
            mImage = (Bitmap)icicle.getParcelable("artwork");
            if (mImage != null) {
                mAlbum.setArtwork(mImage);
                mAlbum.invalidate();
            }
        }
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(GuiNotifier.META_CHANGED);
        mIntentFilter.addAction(GuiNotifier.PLAYBACK_FINISHED);
        mIntentFilter.addAction(GuiNotifier.PLAYBACK_STATE_CHANGED);
        mIntentFilter.addAction(GuiNotifier.PLAYBACK_ERROR);
        mIntentFilter.addAction(GuiNotifier.DATABASE_ERROR);
        
        Music.bindToService(this);
    }
    
    @Override
    public void onStart() {
        super.onStart();
        paused = false;
        long next = refreshNow();
        queueNextRefresh(next);
    }
    
    @Override
    public void onStop() {
        paused = true;
        mHandler.removeMessages(REFRESH);

        super.onStop();
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("configchange", getChangingConfigurations() != 0);
        if (mImage != null)
            outState.putParcelable("artwork", mImage);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        unregisterReceiver(mStatusListener);
        super.onPause();
    }
    
    @Override
    public void onResume() {
        registerReceiver(mStatusListener, mIntentFilter);
        updateTrackInfo();
        setPauseButtonImage();

        super.onResume();
    }

    @Override
    public void onDestroy() {
        Music.unbindFromService( this );
        mAlbumArtWorker.quit();
        super.onDestroy();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.player, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mShowingOptions = true;
        try {
            dismissDialog(BUFFERING_DIALOG);
        } catch (IllegalArgumentException e) {}
        try {
            Track t = Music.sService.getTrack();
            TrackGetter getter = new TrackGetter(Music.getDb(this), getContentResolver());
            menu.findItem(R.id.menu_opt_load_track).setVisible(getter.getLocalId(t.getId()) == null);
            menu.findItem(R.id.menu_opt_put_track).setVisible(getter.getLockerId(t.getId()) == null);
        } catch (Exception e) {
            menu.findItem(R.id.menu_opt_load_track).setVisible(false);
            menu.findItem(R.id.menu_opt_put_track).setVisible(false);
        }
        
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        try {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.menu_opt_main:
                intent = new Intent();
                intent.setClass( this, LockerList.class );
                intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP );
                startActivity(intent);
                return true;
            case R.id.menu_opt_stop:
                if (Music.sService == null) return false;
                try {
                    Music.sService.stop();
                } catch (RemoteException e) {
                    e.printStackTrace();
                    return false;
                }
                return true;
            case R.id.menu_opt_load_track: {
                // add track to local storage
                Track t = Music.sService.getTrack();
                if (AddTrackToMediaStore.isInStore(t, this)) {
                    Log.w("Mp3Tunes", "Track already in store");
                    return true;
                }
                    
                mTrackAdder = new TrackAdder(t);
                mTrackAdder.execute();
                return true;
            }
            case R.id.menu_opt_put_track: {
                Track t = Music.sService.getTrack();
                mTrackPutter = new TrackPutter(t);
                mTrackPutter.execute();
                return true;
            }
                
        }
        } catch (RemoteException e) {
            e.printStackTrace();
        } finally {
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onOptionsMenuClosed(Menu menu)
    {
        mShowingOptions = false;
    }
    
    private View.OnClickListener mPrevListener = new View.OnClickListener() {
        public void onClick(View v) 
        {
            if (Music.sService == null) return;
            try {
                Music.sService.prev();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };
    
    private View.OnClickListener mPlayListener = new View.OnClickListener() {
        public void onClick(View v) 
        {
            if (Music.sService== null) return;
            try {
                Music.sService.togglePlayback();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };
    
    private void setPauseButtonImage()
    {
         try {
            if (Music.sService != null && Music.sService.isPaused()) {
                mPlayButton.setImageResource( R.drawable.play_button );
            } else {
                mPlayButton.setImageResource( R.drawable.pause_button );
            }
        } catch ( RemoteException ex ) {}
    }
    
    private View.OnClickListener mNextListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (Music.sService== null)
                return;
            try {
                Music.sService.next();
            } catch ( RemoteException e ) {
                e.printStackTrace();
            }
        }
    };
    
    private View.OnClickListener mStopListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (Music.sService== null)
                return;
            try {
                Music.sService.stop();
            } catch ( RemoteException e ) {
                e.printStackTrace();
            }
        }
    };

    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) 
        {
            try {
                String action = intent.getAction();
                if (action.equals(GuiNotifier.META_CHANGED)) {
                    mImage = null;
                    updateTrackInfo();
                } else if (action.equals(GuiNotifier.PLAYBACK_FINISHED)) {
                    dismissDialog(BUFFERING_DIALOG);
                    finish();
                } else if (action.equals(GuiNotifier.PLAYBACK_ERROR)) {
                    dismissDialog(BUFFERING_DIALOG);
                    finish();
                } else if(action.equals( GuiNotifier.PLAYBACK_STATE_CHANGED )) {
                    setPauseButtonImage();
                }
            } catch (IllegalArgumentException e) {
                Log.e("Mp3Tunes", Log.getStackTraceString(e));
            }
        }
    };
    
    private void updateTrackInfo() 
    {
        try {
            if (Music.sService== null)
                return;
            Track track = Music.sService.getTrack();
            mArtistName.setText(track.getArtistName());
            mTrackName.setText(track.getTitle());

            if (mImage == null)
            { 
                mArtTask = new LoadAlbumArtTask();
                mArtTask.execute((Void) null);
            }
            setPauseButtonImage();
        } catch (java.util.concurrent.RejectedExecutionException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        } finally  {
        }
    }
    
    private void queueNextRefresh(long delay) {
        if (!paused) {
            Message msg = mHandler.obtainMessage(REFRESH);
            mHandler.removeMessages(REFRESH);
            mHandler.sendMessageDelayed(msg, delay);
        }
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog d = null;
        if(id == BUFFERING_DIALOG){
            createBufferingDialog();
            d = mBufferingDialog;
        }
        return d;
    }
    
    private void createBufferingDialog()
    {
        mBufferingDialog = new ProgressDialog(this);
        mBufferingDialog.setTitle("");
        mBufferingDialog.setMessage("Buffering");
        mBufferingDialog.setIndeterminate(true);
        mBufferingDialog.setCancelable(true);
    }

    private long refreshNow() {
        if (Music.sService != null) {
            try {
                mDuration = Music.sService.getDuration();
                long pos  = Music.sService.getPosition();
                int buffpercent = Music.sService.getBufferPercent();
                long remaining = 1000 - (pos % 1000);
                
                if (pos > 0 && mDuration > 0 && pos <= mDuration) {
                    mCurrentTime.setText(Music.makeTimeString(this, pos / 1000));
                    mTotalTime.setText(Music.makeTimeString(this, mDuration / 1000));    
                    mProgress.setProgress((int)(1000 * pos / mDuration));
                    mProgress.setSecondaryProgress(buffpercent * 10);
                    dismissDialog(BUFFERING_DIALOG);
                } else {
                    mCurrentTime.setText("--:--");
                    mTotalTime.setText("--:--");
                    mProgress.setProgress(0);
                    if(pos < 1 && !mShowingOptions) showDialog(BUFFERING_DIALOG);
                    else dismissDialog(BUFFERING_DIALOG);
                }
                // return the number of milliseconds until the next full second, so
                // the counter can be updated at just the right time
                return remaining;
            } catch (IllegalArgumentException e) {
                //Log.e("Mp3Tunes", Log.getStackTraceString(e));
            } catch (RemoteException e) {
                Log.e("Mp3Tunes", Log.getStackTraceString(e));
            }
        }
        return 500;
    }

    private final Handler mHandler = new Handler() {

        public void handleMessage(Message msg) {
            switch (msg.what) 
            {
                case REFRESH:
                    long next = refreshNow();
                    queueNextRefresh(next);
                    break;
                case RemoteImageHandler.REMOTE_IMAGE_DECODED:
                        mImage = (Bitmap) msg.obj;
                        mAlbum.setArtwork(mImage);
                        mAlbum.invalidate();

                    break;
    
                default:
                    break;
            }
        }
    };
    
    private class LoadAlbumArtTask extends AsyncTask<Void, Void, Boolean> 
    {
        String artUrl;

        private void setArtUrl(Track t) throws InvalidSessionException {
            RemoteMethod method 
            = new RemoteMethod.Builder(RemoteMethod.METHODS.ALBUM_ART_GET)
                    .addFileKey(t.getFileKey())
                    .create();
            artUrl = method.getCall();
        }
        
        @Override
        public void onPreExecute() {
        }

        @Override
        public Boolean doInBackground(Void... params) 
        {
            try {
                if (Music.sService!= null) {
                    setArtUrl(Music.sService.getTrack());
                    return true;
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
        

        @Override
        public void onPostExecute(Boolean result) {
            if (result) {
                System.out.println("Art url: " + artUrl);
                if (artUrl != GuiNotifier.UNKNOWN) {
                    mAlbumArtHandler.removeMessages(RemoteImageHandler.GET_REMOTE_IMAGE);
                    mAlbumArtHandler.obtainMessage(RemoteImageHandler.GET_REMOTE_IMAGE, artUrl).sendToTarget();
                }
            } else {
                System.out.println("Art url: unknown"); 
            }
        }
    }

    class ProgressDialog extends android.app.ProgressDialog
    {

        public ProgressDialog(Context context)
        {
            super(context);
        }
        
        public ProgressDialog(Context context, int theme)
        {
            super(context, theme);
        }
        
        @Override
        public boolean onKeyUp(int code, KeyEvent event)
        {
            Log.w("Mp3Tunes", "Got KeyUp: " + Integer.toString(code));
            if (code == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
                Log.w("Mp3Tunes", "Opening options menu");
                mShowingOptions = true;
                Player.this.dismissDialog(BUFFERING_DIALOG);
                Player.this.openOptionsMenu();
                return true;
            }
            return super.onKeyUp(code, event); 
        }
        
    };
    
    private class TrackAdder extends AddTrackToMediaStore
    {

        public TrackAdder(Track track)
        {
            super(track, getBaseContext());
        }
        
        @Override
        protected void onPostExecute(Boolean result)
        {
            if (!result) {
                Log.w("Mp3Tunes", "Failed to add track");
            }
        }
    }
    
    private class TrackPutter extends AddTrackToLocker
    {

        public TrackPutter(Track track)
        {
            super(track, getBaseContext());
        }
        
        @Override
        protected void onPostExecute(Boolean result)
        {
            if (!result) {
                Log.w("Mp3Tunes", "Failed to add track");
            }
        }
    }
    
}
