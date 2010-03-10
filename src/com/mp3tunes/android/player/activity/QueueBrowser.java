/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mp3tunes.android.player.activity;

import java.io.IOException;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager; //import android.media.MediaFile;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AlphabetIndexer;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.IdParcel;
import com.mp3tunes.android.player.LocalId;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.MusicAlphabetIndexer;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.content.AlbumGetter;
import com.mp3tunes.android.player.content.ArtistGetter;
import com.mp3tunes.android.player.content.DbKeys;
import com.mp3tunes.android.player.content.LockerDb;
import com.mp3tunes.android.player.content.MediaStore;
import com.mp3tunes.android.player.content.MergeHelper;
import com.mp3tunes.android.player.content.LockerDb.RefreshAlbumTracksTask;
import com.mp3tunes.android.player.content.LockerDb.RefreshArtistTracksTask;
import com.mp3tunes.android.player.content.LockerDb.RefreshPlaylistTracksTask;
import com.mp3tunes.android.player.content.LockerDb.RefreshTask;
import com.mp3tunes.android.player.service.GuiNotifier;
import com.mp3tunes.android.player.util.AddTrackToMediaStore;
import com.mp3tunes.android.player.util.BaseMp3TunesListActivity;
import com.mp3tunes.android.player.util.Timer;

public class QueueBrowser extends BaseMp3TunesListActivity implements
        View.OnCreateContextMenuListener, Music.Defs, ServiceConnection
{
    //private boolean mEditMode = false;
    //private boolean mAdapterSent = false;
    private ListView mTrackList;
    private Cursor mTrackCursor;
    private SimpleCursorAdapter mAdapter;
    private TrackList mList;
    private long mSelectedId;
    private String mTrackName;
    private Id    mTrackId;
    private boolean mShowingDialog;
    
    
    private TrackAdder mTrackAdder;
    
    private static final int GET_TRACK = 0;
    
    int[] mTo = new int[] {
            R.id.icon,
            R.id.line1,
            R.id.line2,
            0
    };
    
    static class FROM_MAPPING {
        static final int ID          = 0;
        static final int NAME        = 1;
        static final int ARTIST_NAME = 2;
        static final int LOCAL       = 3;
    };
    
    public QueueBrowser()
    {
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        Music.ensureSession(this);
        
        if (icicle != null) {
            mList = getTrackList(icicle);
            mSelectedId = icicle.getLong("selectedtrack");
        } else {
            mList = getTrackList(getIntent());
        }

        setContentView(R.layout.media_picker_activity);
        mTrackList = getListView();
        mTrackList.setOnCreateContextMenuListener(this);
        mTrackList.setTextFilterEnabled(true);

        buildErrorDialog(R.string.track_browser_error);
        buildProgressDialog(R.string.loading_tracks);

        mAdapter = (SimpleCursorAdapter) getLastNonConfigurationInstance();
        setListAdapter(mAdapter);
        
        Music.bindToService(this, this);
    }
    
    public void onServiceConnected(ComponentName name, IBinder service)
    {

        if (mAdapter == null) {
            // need to use application context to avoid leaks
            mAdapter = new SimpleCursorAdapter(this, R.layout.track_list_item, null, mList.getFrom(), mTo);
            setListAdapter(mAdapter);
            setTitle(R.string.title_working_songs);
            mAdapter.setViewBinder(new Binder());
            mLoadingCursor = true;
            getTrackCursor(null);
            showDialog(PROGRESS_DIALOG);
            mShowingDialog = true;
        } else {
            mTrackCursor = mAdapter.getCursor();
            // If mTrackCursor is null, this can be because it doesn't have
            // a cursor yet (because the initial query that sets its cursor
            // is still in progress), or because the query failed.
            // In order to not flash the error dialog at the user for the
            // first case, simply retry the query when the cursor is null.
            // Worst case, we end up doing the same query twice.
            if (mTrackCursor == null) {
                setTitle(R.string.title_working_songs);
                mLoadingCursor = true;
                getTrackCursor(null);
                showDialog(PROGRESS_DIALOG);
                mShowingDialog = true;
            } else {
                mShowingDialog = false;
            }
        }
        init(mTrackCursor, 100);
    }

    public void onServiceDisconnected(ComponentName name)
    {
        // we can't really function without the service, so don't
        finish();
    }

//    @Override
//    public Object onRetainNonConfigurationInstance()
//    {
//        SimpleCursorAdapter a = mAdapter;
//        mAdapterSent = true;
//        return a;
//    }

    @Override
    public void onStop()
    {
        killTasks();
        super.onStop();
    }
    
    @Override
    public void onDestroy()
    {
        killTasks();
        Music.unbindFromService(this);
//        try {
//            if ("nowplaying".equals(mPlaylist)) {
//                unregisterReceiverSafe(mNowPlayingListener);
//            } else {
//                unregisterReceiverSafe(mTrackListListener);
//            }
//        } catch (IllegalArgumentException ex) {
//            // we end up here in case we never registered the listeners
//        }

        // if we didn't send the adapter off to another activity, we should
        // close the cursor
//        if (!mAdapterSent) {
            Cursor c = mAdapter.getCursor();
            if (c != null) {
                c.close();
            }
//        }
        Music.unconnectFromDb(this);
        super.onDestroy();
    }

    /**
     * Unregister a receiver, but eat the exception that is thrown if the
     * receiver was never registered to begin with. This is a little easier than
     * keeping track of whether the receivers have actually been registered by
     * the time onDestroy() is called.
     */
    private void unregisterReceiverSafe(BroadcastReceiver receiver)
    {
        try {
            unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            // ignore
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (mTrackCursor != null) {
            getListView().invalidateViews();
        }
        // Music.setSpinnerState(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
    }

    public void onSaveInstanceState(Bundle outcicle)
    {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        IdParcel parcel = mList.getParcelableOutput();
        if (parcel != null) 
            outcicle.putParcelable(mList.key(), parcel);
        else {
            outcicle.putString(mList.key(), mList.mId.asString());
            outcicle.putString("playlist_name", mList.getName());
        }
        outcicle.putLong("selectedtrack", mSelectedId);
        super.onSaveInstanceState(outcicle);
    }

    public void init(Cursor newCursor, int nextRefresh)
    {
        Log.w("Mp3Tunes", "init called");
        tryDismissProgress(mShowingDialog, newCursor);
        if (newCursor != null) {
            Log.w("Mp3Tunes", "cursor count: "
                    + Integer.toString(newCursor.getCount()));
        }
        mAdapter.changeCursor(newCursor); // also sets mTrackCursor

        mTrackCursor = newCursor;
        setTitle();
        super.init(newCursor, nextRefresh);
    }

    private void setTitle()
    {
        setTitle(mList.getName());
    }

//    private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
//
//        @Override
//        public void onReceive(Context context, Intent intent)
//        {
//            getListView().invalidateViews();
//        }
//    };
//
//    private BroadcastReceiver mNowPlayingListener = new BroadcastReceiver() {
//
//        @Override
//        public void onReceive(Context context, Intent intent)
//        {
//            if (intent.getAction().equals(GuiNotifier.META_CHANGED)) {
//                getListView().invalidateViews();
//            } else if (intent.getAction().equals(GuiNotifier.QUEUE_CHANGED)) {
//
//            }
//        }
//    };

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view,
            ContextMenuInfo menuInfoIn)
    {
        AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfoIn;

        menu.add(0, GET_TRACK, 0, R.string.menu_add_track_locally);

        mTrackCursor.moveToPosition(mi.position);
        mTrackName = mTrackCursor.getString(FROM_MAPPING.NAME);
        mTrackId   = cursorToId(mTrackCursor);
        
        menu.setHeaderTitle(mTrackName);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case GET_TRACK: {
                // add track to local storage
                if (LockerId.class.isInstance(mTrackId)) {
                    Track t = Music.getDb(getBaseContext()).getTrack((LockerId)mTrackId);
                    mTrackAdder = new TrackAdder(t);
                    mTrackAdder.execute();
                } else {
                    Log.w("Mp3Tunes", "Track already in store");
                }
                return true;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        if (mTrackCursor.getCount() == 0)
            return;

        Music.playAll(this, cursorToIdArray(mTrackCursor), position);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.artists, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        menu.findItem(R.id.menu_opt_player).setVisible(Music.isMusicPlaying());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.menu_opt_home:
                intent = new Intent();
                intent.setClass(this, LockerList.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;

            case R.id.menu_opt_player:
                intent = new Intent("com.mp3tunes.android.player.PLAYER");
                startActivity(intent);
                return true;
            case R.id.menu_opt_playall:
                if (mTrackCursor.getCount() == 0)
                    break;
                Music.playAll(this, cursorToIdArray(mTrackCursor), 0);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private Cursor getTrackCursor(String filter)
    {
        Log.w("Mp3Tunes", "Trying to get tracks cursor");
        mCursorTask = mList.getTask();
        mCursorTask.execute((Void[])null);
        return null;
    }

    class Binder implements SimpleCursorAdapter.ViewBinder
    {
        private final BitmapDrawable mDefaultIcon;
        private static final int  mUnknown = R.string.unknown_playlist_name;
        
        public Binder ()
        {
            Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.song_icon);

            mDefaultIcon = new BitmapDrawable(b);
             //no filter or dither, it's a lot faster and we can't tell the difference
            mDefaultIcon.setFilterBitmap(false);
            mDefaultIcon.setDither(false);
        }
        public boolean setViewValue(View v, Cursor cursor, int columnIndex)
        {
            if (columnIndex == 1) {
                TextView view = (TextView)v.findViewById(R.id.line1);
                String val = cursor.getString(columnIndex);
                if (val == null) view.setText(mUnknown);
                view.setText(val);
            } else if (columnIndex == 2) {
                String text = cursor.getString(columnIndex);
                TextView view = ((TextView)v.findViewById(R.id.line2));
                view.setText(text);
            } else if (columnIndex == 0) {
                ImageView view = (ImageView)v.findViewById(R.id.icon);
                view.setImageDrawable(mDefaultIcon);
                view.setPadding(0, 0, 1, 0);
            }
            
            return true;
        }
    }
    
    private void killTasks()
    {
        if( mCursorTask != null && mCursorTask.getStatus() == AsyncTask.Status.RUNNING) {
            mCursorTask.cancel(true);
            mLoadingCursor = false;
        }
        if( mTracksTask != null && mTracksTask.getStatus() == AsyncTask.Status.RUNNING)
            mTracksTask.cancel(true);
    }
    
    private class FetchPlaylistTracksTask extends RefreshPlaylistTracksTask
    {

        public FetchPlaylistTracksTask(LockerDb db, Id id)
        {
            super(db, id);
        }
        
        @Override
        protected  void onPostExecute(Boolean result)
        {
            mLoadingCursor = false;
            if (!result) {
                    Log.w("Mp3Tunes", "Got Error Fetching Playlist Tracks");
            } else {
                mTracksTask = new LockerDb.RefreshTracksTask(Music.getDb(getBaseContext()));
                mTracksTask.execute((Void[])null);
            }
        }
    };
    
    private class FetchArtistTracksTask extends RefreshArtistTracksTask
    {

        public FetchArtistTracksTask(LockerDb db, Id id)
        {
            super(db, id);
        }
        
        @Override
        protected  void onPostExecute(Boolean result)
        {
            mLoadingCursor = false;
            if (!result) {
                    Log.w("Mp3Tunes", "Got Error Fetching Artist Tracks");
            } else {
                mTracksTask = new LockerDb.RefreshTracksTask(Music.getDb(getBaseContext()));
                mTracksTask.execute((Void[])null);
            }
        }
    };
    
    private class FetchAlbumTracksTask extends RefreshAlbumTracksTask
    {

        public FetchAlbumTracksTask(LockerDb db, Id id)
        {
            super(db, id);
        }
        
        @Override
        protected  void onPostExecute(Boolean result)
        {
            mLoadingCursor = false;
            if (!result) {
                    Log.w("Mp3Tunes", "Got Error Fetching Album Tracks");
            } else {
                mTracksTask = new LockerDb.RefreshTracksTask(Music.getDb(getBaseContext()));
                mTracksTask.execute((Void[])null);
            }
        }
    };
    
    @Override
    protected void updateCursor()
    {
        try {
            Log.w("Mp3Tunes", "Updating cursor");
            mCursor = mList.getCursor();
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
    
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
    
    private TrackList getTrackList(Bundle b)
    {
        Id id = IdParcel.idParcelToId(b.getParcelable("album"));
        if (id != null) return new AlbumList(id);
            
        id = IdParcel.idParcelToId(b.getParcelable("artist"));
        if (id != null) return new ArtistList(id);
        
        String playlistId   = b.getString("playlist");
        String playlistName = b.getString("playlist_name");
        return new PlaylistList(new LockerId(playlistId), playlistName);
    }
    
    private TrackList getTrackList(Intent i)
    {
        Id id = IdParcel.idParcelToId(i.getParcelableExtra("album"));
        if (id != null) return new AlbumList(id);
        
        id = IdParcel.idParcelToId(i.getParcelableExtra("artist"));
        if (id != null) return new ArtistList(id);
        
        String playlistId   = i.getStringExtra("playlist");
        String playlistName = i.getStringExtra("playlist_name");
        return new PlaylistList(new LockerId(playlistId), playlistName);
    }
    
    
    //This is a really bad name.  Basically our TrackBrowser shows lists of tracks based on some filter.
    //The current filters are artist, album, and playlist.  These classes allow us to treat polymophically
    abstract class TrackList 
    {
        protected Id       mId;
        protected LockerId mLockerId;
        protected LocalId  mLocalId;
        protected String   mName;
        protected String[] mFrom;
        
        public String[] getFrom()
        {
            return mFrom;
        }
        
        public String getName()
        {
            if (mName == null)
                makeName();
            return mName;
        }
        public LockerId getLockerId()
        {
            if (mLockerId == null)
                makeLockerId();
            return mLockerId;
        }
        public LocalId  getLocalId()
        {
            if (mLocalId == null)
                makeLocalId();
            return mLocalId; 
        }   
        public IdParcel getParcelableOutput()
        {
            return null;
        }
        public abstract RefreshTask getTask();
        public abstract String key();
        public abstract Cursor getCursor() throws IOException, LockerException;
        
        protected void makeLocalId()
        {
            try {
                mLocalId = getMergeHelper().getLocalId(mId);
            } catch (Exception e) {}
        }

        protected void makeLockerId()
        {
            try {
                mLockerId = getMergeHelper().getLockerId(mId);
            } catch (Exception e) {}
        }

        protected void makeName()
        {
            try {
                mName = getMergeHelper().get(mId).getName();
            } catch (Exception e) {
                mName = "Unknown";
            }
        }
        
        protected LockerDb getDb()
        {
            return Music.getDb(getBaseContext());
        }
        
        protected MediaStore getStore()
        {
            return new MediaStore(getDb(), getContentResolver());
        }
        abstract protected MergeHelper getMergeHelper();
    }
    
    
    
    class PlaylistList extends TrackList
    {
        PlaylistList(LockerId id, String name)
        {
            mLockerId = id;
            mName = name;
            mFrom = new String[] {
                    DbKeys.ID,
                    DbKeys.TITLE,
                    DbKeys.ARTIST_NAME,
                    MediaStore.KEY_LOCAL
              };
        }
        
        @Override protected void makeLocalId() {}
        @Override protected void makeLockerId() {}
        @Override protected void makeName() {}

        @Override
        public String key()
        {
            return "playlist";
        }

        @Override
        public RefreshTask getTask()
        {
            return new FetchPlaylistTracksTask(Music.getDb(getBaseContext()), mLockerId);
        }

        @Override
        public Cursor getCursor() throws SQLiteException, IOException, LockerException
        {
            return getStore().getTrackDataByPlaylist(mFrom, mLockerId);
        }

        @Override
        protected MergeHelper getMergeHelper()
        {
            return null;
        }
    }
    
    class ArtistList extends TrackList
    {
        ArtistList(Id id)
        {
            mId = id;
            mFrom = new String[] {
                    DbKeys.ID,
                    DbKeys.TITLE,
                    DbKeys.ALBUM_NAME,
                    MediaStore.KEY_LOCAL
              };
        }
        
        @Override public String key()
        {
            return "artist";
        }
        
        public IdParcel getParcelableOutput()
        {
            return new IdParcel(mId);
        }

        @Override
        public RefreshTask getTask()
        {
            return new FetchArtistTracksTask(Music.getDb(getBaseContext()), getLockerId());
        }

        @Override
        public Cursor getCursor() throws IOException, LockerException
        {
            return getStore().getTrackDataByArtist(mFrom, mId);
        }

        @Override
        protected MergeHelper getMergeHelper()
        {
            return new ArtistGetter(getDb(), getContentResolver());
        }
    }
    
    class AlbumList extends TrackList
    {
        AlbumList(Id id)
        {
            mId = id;
            mFrom = new String[] {
                    DbKeys.ID,
                    DbKeys.TITLE,
                    DbKeys.ARTIST_NAME,
                    MediaStore.KEY_LOCAL
              };
        }

        @Override public String key()
        {
            return "album";
        }
        
        public IdParcel getParcelableOutput()
        {
            return new IdParcel(mId);
        }

        @Override
        public RefreshTask getTask()
        {
            return new FetchAlbumTracksTask(Music.getDb(getBaseContext()), getLockerId());
        }

        @Override
        public Cursor getCursor() throws SQLiteException, IOException, LockerException
        {
            return getStore().getTrackDataByAlbum(mFrom, mId);
        }

        @Override
        protected MergeHelper getMergeHelper()
        {
            return new AlbumGetter(getDb(), getContentResolver());
        }
    }
    
}
