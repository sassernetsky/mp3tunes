package com.mp3tunes.android.player.activity;

import java.io.IOException;
import java.util.ArrayList;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.InvalidSessionException;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.binaryelysium.mp3tunes.api.RemoteMethod;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.IdParcel;
import com.mp3tunes.android.player.LocalId;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.ParcelableTrack;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.RemoteAlbumArtHandler;
import com.mp3tunes.android.player.RemoteImageView;
import com.mp3tunes.android.player.content.DbKeys;
import com.mp3tunes.android.player.content.LockerDb;
import com.mp3tunes.android.player.content.MediaStore;
import com.mp3tunes.android.player.content.TrackGetter;
import com.mp3tunes.android.player.content.LockerCache.RefreshPlaylistTracksTask;
import com.mp3tunes.android.player.service.GuiNotifier;
import com.mp3tunes.android.player.service.Logger;
import com.mp3tunes.android.player.service.PlaybackState;
import com.mp3tunes.android.player.service.PlaybackService.NoCurrentTrackException;
import com.mp3tunes.android.player.util.AddTrackToLocker;
import com.mp3tunes.android.player.util.AddTrackToMediaStore;
import com.mp3tunes.android.player.util.DialogHandler;
import com.mp3tunes.android.player.util.LifetimeLoggingActivity;
import com.mp3tunes.android.player.util.Worker;


public class Player extends LifetimeLoggingActivity
{
    private static final int REFRESH = 0;
    private DialogHandler mDialogHandler;

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
    private TextView mNextTrackData;
    private ProgressBar mProgress;
    private ProgressDialog mStatusDialog;
    
    private boolean paused;
    private boolean mShowingOptions;
    
    private Worker mAlbumArtWorker;
    private RemoteAlbumArtHandler mAlbumArtHandler;
    private IntentFilter mIntentFilter;
    private AsyncTask<Void, Void, Boolean> mArtTask;
    
    private FetchPlaylistTracksTask mPlaylistTask;
    
    private TrackAdder  mTrackAdder;
    private TrackPutter mTrackPutter;
    
    private Track       mTrack;
    
    private static final class Dialogs {
        public static final int BUFFERING_DIALOG = 0;  
        public static final int CHANGING_DIALOG  = 1;
        public static final int STARTING_DIALOG  = 2;
        
        public static final String[] sDialogs = new String[] {
            "Buffering",
            "Changing track",
            "Starting track"
        };
    }
    
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.audio_player);
        Music.ensureSession(this);
        Music.bindToService(this);
        mShowingOptions = false;
        
        mCurrentTime   = (TextView)findViewById(R.id.currenttime);
        mTotalTime     = (TextView)findViewById(R.id.totaltime);
        mAlbum         = (RemoteImageView)findViewById(R.id.album);
        mArtistName    = (TextView)findViewById(R.id.track_artist);
        mTrackName     = (TextView)findViewById(R.id.track_title);
        mNextTrackData = (TextView)findViewById(R.id.next_track);
        mProgress      = (ProgressBar)findViewById(android.R.id.progress);
        mProgress.setMax(1000);
        //mProgress.setOnSeekBarChangeListener(mSeekBarListener);
        
        mPrevButton = ( ImageButton ) findViewById( R.id.rew );
        mPlayButton = ( ImageButton ) findViewById( R.id.play );
        mNextButton = ( ImageButton ) findViewById( R.id.fwd );
        mStopButton = ( ImageButton ) findViewById( R.id.stop );
        
        mPrevButton.setOnClickListener(mPrevListener);
        mPlayButton.setOnClickListener(mPlayListener);
        mNextButton.setOnClickListener(mNextListener);
        mStopButton.setOnClickListener(mStopListener);
        
        mPlayButton.requestFocus();
        
        mDialogHandler = new DialogHandler(this, Dialogs.sDialogs);
        
        mAlbumArtWorker  = new Worker("album art worker");
        if (icicle != null) {
            try {
                mAlbumArtHandler = new RemoteAlbumArtHandler(mAlbumArtWorker.getLooper(), mHandler, getBaseContext(), (Track)icicle.getParcelable("track"));
                mImage = (Bitmap)icicle.getParcelable("artwork");
                if (mImage != null) {
                    mAlbum.setArtwork(mImage);
                    mAlbum.invalidate();
                }
            } catch (Exception e) {
                mAlbumArtHandler = new RemoteAlbumArtHandler(mAlbumArtWorker.getLooper(), mHandler, getBaseContext(), null);
            }
        } else {
            Intent intent = getIntent();
            IdParcel idParcel = intent.getParcelableExtra("playlist");
            if (idParcel != null) {
                tryToStopPlayingTrack();
                //intent.removeExtra("playlist");
                Id id = idParcel.getId();
                Log.w("Mp3Tunes", "playing playlist: " + id.asString());
                //If all the tracks we are asking for are local then we do not need to run the refresh task
                mPlaylistTask = new FetchPlaylistTracksTask(Music.getDb(getBaseContext()), id);
                mPlaylistTask.execute((Void[])null);                    
            }
            mAlbumArtHandler = new RemoteAlbumArtHandler(mAlbumArtWorker.getLooper(), mHandler, getBaseContext(), null);
        }
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(GuiNotifier.META_CHANGED);
        mIntentFilter.addAction(GuiNotifier.PLAYBACK_FINISHED);
        mIntentFilter.addAction(GuiNotifier.PLAYBACK_STATE_CHANGED);
        mIntentFilter.addAction(GuiNotifier.PLAYBACK_ERROR);
        mIntentFilter.addAction(GuiNotifier.DATABASE_ERROR);

    }
    
    @Override
    public void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        Log.w("Mp3Tunes", "onNewIntent");
        IdParcel idParcel = intent.getParcelableExtra("playlist");
        if (idParcel != null) {
            tryToStopPlayingTrack();
            Id id = idParcel.getId();
            Log.w("Mp3Tunes", "playing playlist: " + id.asString());
            //If all the tracks we are asking for are local then we do not need to run the refresh task
            mPlaylistTask = new FetchPlaylistTracksTask(Music.getDb(getBaseContext()), id);
            mPlaylistTask.execute((Void[])null);                    
        }
    }
    
    @Override
    public void onRestart()
    {
        super.onRestart();
    }
    
    @Override
    public void onStart() {
        super.onStart();
        
        paused = false;
        long next = refreshNow();
        queueNextRefresh(next);
    }
    
    @Override
    public void onStop() 
    {
        paused = true;
        mHandler.removeMessages(REFRESH);
        mDialogHandler.dismissAll();
        super.onStop();
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("configchange", getChangingConfigurations() != 0);
        if (mImage != null)
            outState.putParcelable("artwork", mImage);
        if (mTrack != null)
            outState.putParcelable("track", new ParcelableTrack(mTrack));
        mDialogHandler.dismissAll();
        
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        unregisterReceiver(mStatusListener);
        killTasks();
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
        killTasks();
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
        mDialogHandler.dismissAll();
        try {
            mTrack = Music.sService.getTrack();
            TrackGetter getter = new TrackGetter(Music.getDb(this), getContentResolver());
            menu.findItem(R.id.menu_opt_load_track).setVisible(getter.getLocalId(mTrack.getId()) == null);
            menu.findItem(R.id.menu_opt_put_track).setVisible(getter.getLockerId(mTrack.getId()) == null);
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
                mTrack = Music.sService.getTrack();
                if (AddTrackToMediaStore.isInStore(mTrack, this)) {
                    Log.w("Mp3Tunes", "Track already in store");
                    return true;
                }
                    
                mTrackAdder = new TrackAdder(mTrack);
                mTrackAdder.execute();
                return true;
            }
            case R.id.menu_opt_put_track: {
                mTrack = Music.sService.getTrack();
                mTrackPutter = new TrackPutter(mTrack);
                mTrackPutter.execute();
                return true;
            }
            case R.id.menu_current_playlist: {
                intent = new Intent(Intent.ACTION_PICK);
                intent.setDataAndType(Uri.EMPTY, "vnd.mp3tunes.android.dir/track");
                intent.putExtra("playlist_name", QueueBrowser.NOW_PLAYING);
                intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP );
                startActivity(intent);
                return true;
            }   
        }
        
        } catch (RemoteException e) {
            e.printStackTrace();
            handleRemoteException();
        } finally {
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onOptionsMenuClosed(Menu menu)
    {
        mShowingOptions = false;
    }
    
    private void tryToStopPlayingTrack()
    {
        try {
            Log.w("Mp3Tunes", "Stopping old playback");
            Music.sService.stop();
        } catch (Exception e) {
            Log.w("Mp3Tunes", "Unable to stop old playback this may not be an error");
        }
    }
    
    private View.OnClickListener mPrevListener = new View.OnClickListener() {
        public void onClick(View v) 
        {
            if (Music.sService == null) return;
            try {
                mDialogHandler.show(Dialogs.CHANGING_DIALOG);
                Music.sService.prev();
            } catch (RemoteException e) {
                e.printStackTrace();
                handleRemoteException();
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
                handleRemoteException();
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
        } catch ( RemoteException ex ) {
            handleRemoteException();
        }
    }
    
    private View.OnClickListener mNextListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (Music.sService== null)
                return;
            try {
                Log.w("Mp3Tunes", "changing track true");
                mDialogHandler.show(Dialogs.CHANGING_DIALOG);
                Music.sService.next();
            } catch ( RemoteException e ) {
                e.printStackTrace();
                handleRemoteException();
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
                handleRemoteException();
            }
        }
    };
    
//    private void tryDismiss(int dialog)
//    {
//        if (mStatusDialog != null) dialog = mRealDialog;
//        try {dismissDialog(dialog);} catch (Exception e2) {}
//    }
    
    //All of the wierdness in these functions is to work around incompatible behavior in
    //Android 1.5.  There are much better ways to deal with this, but the hope is that
    //we will not have to support Android 1.5 for that long.
//    private void tryShow(int dialog)
//    {
//        Logger.log("Dialog: " + dialog + " state: " + mStatusDialog);
//        if (mStatusDialog != null) {
//            onPrepareDialog(dialog, mStatusDialog);
//            showDialog(mRealDialog);
//        } else {
//            mRealDialog = dialog;
//            showDialog(dialog);
//        }
//        Logger.log("Dialog done");
//    }
    
    private void handleRemoteException()
    {
        try {
            mDialogHandler.dismissAll();
        } finally {
            finish();
        }
    }

    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) 
        {
            String action = intent.getAction();
            if (action.equals(GuiNotifier.META_CHANGED)) {
                mImage = null;
                updateTrackInfo();
            } else if (action.equals(GuiNotifier.PLAYBACK_FINISHED)) {
                handleRemoteException();
            } else if (action.equals(GuiNotifier.PLAYBACK_ERROR)) {
                handleRemoteException();
            } else if(action.equals( GuiNotifier.PLAYBACK_STATE_CHANGED )) {
                setPauseButtonImage();
            }
        }
    };
    
    private void updateTrackInfo() 
    {
        try {
            if (Music.sService== null)
                return;
            mTrack = Music.sService.getTrack();
            mArtistName.setText(mTrack.getArtistName());
            mTrackName.setText(mTrack.getTitle());

            try {
                Track next = Music.sService.nextTrack();
                mNextTrackData.setText("Next: \"" + next.getTitle() + "\" by " + next.getArtistName());
            } catch (RemoteException e) {
                mNextTrackData.setText("");
            }
                
            mArtTask = new LoadAlbumArtTask();
            mArtTask.execute((Void) null);
                
            setPauseButtonImage();
        } catch (java.util.concurrent.RejectedExecutionException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (Exception e) {
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
    protected Dialog onCreateDialog(int id) 
    {
        return mDialogHandler.createDialog(id);
    }
    
    @Override
    protected void onPrepareDialog(int id, Dialog dialog)
    {
        mDialogHandler.prepareDialog(id);
    }

    private long refreshNow() {
        if (Music.sService != null) {
            try {
                PlaybackState state = Music.sService.getPlaybackState();
                if (!mShowingOptions) {
                    switch (state.getState()) {
                        case PlaybackState.State.PLAYING:
                            mDialogHandler.dismiss(Dialogs.BUFFERING_DIALOG);
                            mDialogHandler.dismiss(Dialogs.CHANGING_DIALOG);
                            break;
                        case PlaybackState.State.CHANGING_TRACK:
                            mDialogHandler.show(Dialogs.CHANGING_DIALOG);
                            break;
                        case PlaybackState.State.BUFFERING:
                            mDialogHandler.show(Dialogs.BUFFERING_DIALOG);
                            break;
                        case PlaybackState.State.STARTING:
                            mDialogHandler.dismiss(Dialogs.CHANGING_DIALOG);
                            break;
                        default:
                            assert(false);
                    }
                } else {
                    mDialogHandler.dismiss(Dialogs.BUFFERING_DIALOG);
                }
                
                if (state.isPaused()) {
                    mPlayButton.setImageResource(R.drawable.play_button);
                } else {
                    mPlayButton.setImageResource(R.drawable.pause_button);
                }
                mCurrentTime.setText(Music.makeTimeString(this, state.getCurrentTime()));
                mTotalTime.setText(Music.makeTimeString(this, state.getTotalTime()));    
                mProgress.setProgress(state.getCurrentProgress());
                mProgress.setSecondaryProgress(state.getBufferProgress());
                return state.getRemainingTime();
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
                case RemoteAlbumArtHandler.REMOTE_IMAGE_DECODED:
                    Log.w("Mp3Tunes", "Got decoded artwork");
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
                    mTrack = Music.sService.getTrack();
                    setArtUrl(mTrack);
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
                    mAlbumArtHandler.removeMessages(RemoteAlbumArtHandler.GET_REMOTE_IMAGE);
                    mAlbumArtHandler.obtainMessage(RemoteAlbumArtHandler.GET_REMOTE_IMAGE, mTrack).sendToTarget();
                }
            } else {
                System.out.println("Art url: unknown"); 
            }
        }
    }
    
    SeekBar.OnSeekBarChangeListener mSeekBarListener = new SeekBar.OnSeekBarChangeListener ()
    {
        int mProgress;
        
        public void onProgressChanged(SeekBar seekBar, int progress,
                boolean fromUser)
        {
            if (fromUser) {
                mProgress = progress;
            }
        }

        public void onStartTrackingTouch(SeekBar seekBar)
        {
        }

        public void onStopTrackingTouch(SeekBar seekBar)
        {
            if (Music.sService != null) {
                try {
                    Log.w("Mp3Tunes", "Moving playhead to: " + Integer.toString(mProgress));
                    Music.sService.setPosition(mProgress);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        
    };

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
                mDialogHandler.dismissAll();
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
    
    void killTasks()
    {
        if( mPlaylistTask != null && mPlaylistTask.getStatus() == AsyncTask.Status.RUNNING) {
            mPlaylistTask.cancel(true);
        }
    }
    
    private class FetchPlaylistTracksTask extends RefreshPlaylistTracksTask
    {
        Cursor   mCursor;
        int      mTracks;
        Boolean  mRefreshing;
        Boolean  mRefreshedSome;
        String[] mData = new String[] {DbKeys.ID, MediaStore.KEY_LOCAL};
        
        private void queueNextRefresh(long delay) 
        {
            synchronized (mRefreshing) {
                if (mRefreshing) {
                    Message msg = mHandler.obtainMessage(REFRESH);
                    mHandler.removeMessages(REFRESH);
                    mHandler.sendMessageDelayed(msg, delay);
                }
            }
        }

        private final Handler mHandler = new Handler() 
        {
            public void handleMessage(Message msg) 
            {
                if (msg.what == REFRESH) {
                    Thread t = new Thread() 
                    {
                        public void run() 
                        {
                            updateCursor();
                        }
                    };
                    t.start();
                }
            }
        };
        
        private IdParcel createIdParcel(int id, Id playlist)
        {
            if (LockerId.class.isInstance(mId))
                return new IdParcel(new LockerId(id));
            return new IdParcel(new LocalId(id));
        }
        
        
        private void setCursor() throws SQLiteException, IOException, LockerException
        {
            if (LockerId.class.isInstance(mId))
                mCursor = new MediaStore(mDb, getBaseContext().getContentResolver()).getTrackDataByPlaylist(mData, mId);
            else
                mCursor = new MediaStore(mDb, getBaseContext().getContentResolver()).getLocalTracksForPlaylist(mData);
        }
        
        
        private IdParcel[]  createIdParcels()
        {
            ArrayList<IdParcel> ids = new ArrayList<IdParcel>(); 
            if (mCursor.moveToPosition(mTracks)) {
                do {
                    int id = mCursor.getInt(0);
                    ids.add(createIdParcel(id, mId));
                } while (mCursor.moveToNext());
            }
            return ids.toArray(new IdParcel[1]);
        }
        
        private void createNewPlaybackList(IdParcel[] array) throws RemoteException
        {
            if (array != null && array.length > 0) {
                Music.sService.createPlaybackList(array);
                Music.sService.start();
            } else {
                queueNextRefresh(100);
            }
        }
        
        private void addToCurrentPlaybackList(IdParcel[] array) throws RemoteException
        {
            if (array != null && array.length > 0) {
                Music.sService.addToPlaybackList(array);
            } else {
                queueNextRefresh(100);
            }
        }
        
        void updateCursor()
        {
            try {
                //It is possible that the playback service has not been started yet 
                if (Music.sService == null) {
                    queueNextRefresh(100);
                    return;
                }
                
                //If we get here before we have any data then we do not want to wait a full hal
                synchronized (mRefreshing) {
                    if (!mRefreshedSome) {
                        queueNextRefresh(100);
                        return;
                    }
                }
                
                synchronized (mCursor) {
                    setCursor();
                    
                    //Make sure that we have new tracks to add
                    Log.w("Mp3Tunes", "FetchPlaylistTracksTask cursor: " + mCursor.getCount() + " mTracks: " + mTracks);
                    if (mCursor.getCount() > mTracks) {
                        IdParcel[] array = createIdParcels();
                        
                        //check to see if we need to create a new playback list this time
                        if (mTracks == 0) {
                            createNewPlaybackList(array);
                        } else {
                            addToCurrentPlaybackList(array);
                        }
                        mTracks = mCursor.getCount();
                    } else {
                        return;
                    }
                }
                queueNextRefresh(500);
            } catch (SQLiteException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (LockerException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        public FetchPlaylistTracksTask(LockerDb db, Id id)
        {
            super(db, id);
            mCursor        = new MatrixCursor(mData);
            mTracks        = 0;
            mRefreshing    = true;
            mRefreshedSome = false;
        }
        
        @Override
        protected  void onPreExecute()
        {
            queueNextRefresh(100);
        }
        
        @Override 
        protected void onProgressUpdate(Void... voids)
        {
            synchronized (mRefreshing) {
                mRefreshedSome = true;
            }
        }
        
        @Override
        protected  void onPostExecute(Boolean result)
        {
            synchronized (mRefreshing) {
                mRefreshing    = false;
                mRefreshedSome = true;
            }
            if (!result) {
                    Log.w("Mp3Tunes", "Got Error Fetching Playlist Tracks");
            }
        }
    };
}
