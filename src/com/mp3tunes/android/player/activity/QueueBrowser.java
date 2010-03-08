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
import com.binaryelysium.mp3tunes.api.LockerId;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.IdParcel;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.MusicAlphabetIndexer;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.content.DbKeys;
import com.mp3tunes.android.player.content.LockerDb;
import com.mp3tunes.android.player.content.MediaStore;
import com.mp3tunes.android.player.service.GuiNotifier;
import com.mp3tunes.android.player.util.AddTrackToMediaStore;
import com.mp3tunes.android.player.util.BaseMp3TunesListActivity;
import com.mp3tunes.android.player.util.Timer;

public class QueueBrowser extends BaseMp3TunesListActivity implements
        View.OnCreateContextMenuListener, Music.Defs, ServiceConnection
{
    private boolean mEditMode = false;
    private boolean mAdapterSent = false;
    private ListView mTrackList;
    private Cursor mTrackCursor;
    //private TrackListAdapter mAdapter;
    private SimpleCursorAdapter mAdapter;
    private Id mAlbumId;
    private Id mArtistId;
    private String mPlaylist;
    private String mPlaylistName;
    private String mGenre;
    private boolean mPlayNow;
    private long mSelectedId;
    private String mTrackName;
    private FetchTracksTask mTrackTask;
    private Id    mTrackId;
    
    private TrackAdder mTrackAdder;
    
    private static final int GET_TRACK = 0;

    String[] mFrom = new String[] {
            DbKeys.ID,
            DbKeys.TITLE,
            DbKeys.ARTIST_NAME,
            MediaStore.KEY_LOCAL
      };
    
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
            mSelectedId = icicle.getLong("selectedtrack");
            mAlbumId = IdParcel.idParcelToId(icicle.getParcelable("album"));
            mArtistId = IdParcel.idParcelToId(icicle.getParcelable("artist"));
            mPlaylist = icicle.getString("playlist");
            mPlaylistName = icicle.getString("playlist_name");
            mGenre = icicle.getString("genre");
            mEditMode = icicle.getBoolean("editmode", false);
        } else {
            mAlbumId = IdParcel.idParcelToId(getIntent().getParcelableExtra(("album")));
            // If we have an album, show everything on the album, not just stuff
            // by a particular artist.
            Intent intent = getIntent();
            mArtistId     = IdParcel.idParcelToId(intent.getParcelableExtra("artist"));
            mPlaylist     = intent.getStringExtra("playlist");
            mPlaylistName = intent.getStringExtra("playlist_name");
            mGenre        = intent.getStringExtra("genre");
            mPlayNow      = intent.getBooleanExtra("playnow", false);
            mEditMode     = intent.getAction().equals(Intent.ACTION_EDIT);
        }

        setContentView(R.layout.media_picker_activity);
        mTrackList = getListView();
        mTrackList.setOnCreateContextMenuListener(this);
        if (mEditMode) {
            mTrackList.setCacheColorHint(0);
        } else {
            mTrackList.setTextFilterEnabled(true);
        }

        buildErrorDialog(R.string.track_browser_error);
        buildProgressDialog(R.string.loading_tracks);

        mAdapter = (SimpleCursorAdapter) getLastNonConfigurationInstance();

        //if (mAdapter != null) {
        //    mAdapter.setActivity(this);
            setListAdapter(mAdapter);
        //}
        Music.bindToService(this, this);
    }

    public void onServiceConnected(ComponentName name, IBinder service)
    {

        if (mAdapter == null) {
            // need to use application context to avoid leaks
            mAdapter = new SimpleCursorAdapter(this, R.layout.track_list_item, null, mFrom, mTo);
            mPlayNow = false;
            setListAdapter(mAdapter);
            setTitle(R.string.title_working_songs);
            mAdapter.setViewBinder(new Binder());
            getTrackCursor(null);
        } else {
            mTrackCursor = mAdapter.getCursor();
            // If mTrackCursor is null, this can be because it doesn't have
            // a cursor yet (because the initial query that sets its cursor
            // is still in progress), or because the query failed.
            // In order to not flash the error dialog at the user for the
            // first case, simply retry the query when the cursor is null.
            // Worst case, we end up doing the same query twice.
            if (mTrackCursor != null) {
                init(mTrackCursor);
            } else {
                setTitle(R.string.title_working_songs);
                getTrackCursor(null);
            }
        }
    }

    public void onServiceDisconnected(ComponentName name)
    {
        // we can't really function without the service, so don't
        finish();
    }

    @Override
    public Object onRetainNonConfigurationInstance()
    {
        SimpleCursorAdapter a = mAdapter;
        mAdapterSent = true;
        return a;
    }

    @Override
    public void onStop()
    {
        if (mTrackTask != null
                && mTrackTask.getStatus() == AsyncTask.Status.RUNNING)
            mTrackTask.cancel(true);
        super.onStop();
    }
    
    @Override
    public void onDestroy()
    {
        if (mTrackTask != null
                && mTrackTask.getStatus() == AsyncTask.Status.RUNNING)
            mTrackTask.cancel(true);
        Music.unbindFromService(this);
        try {
            if ("nowplaying".equals(mPlaylist)) {
                unregisterReceiverSafe(mNowPlayingListener);
            } else {
                unregisterReceiverSafe(mTrackListListener);
            }
        } catch (IllegalArgumentException ex) {
            // we end up here in case we never registered the listeners
        }

        // if we didn't send the adapter off to another activity, we should
        // close the cursor
        if (!mAdapterSent) {
            Cursor c = mAdapter.getCursor();
            if (c != null) {
                c.close();
            }
        }
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
        if (mArtistId != null)
            outcicle.putParcelable("artist", new IdParcel(mArtistId));
        if (mAlbumId != null)
            outcicle.putParcelable("album", new IdParcel(mAlbumId));
            
        
        outcicle.putLong("selectedtrack", mSelectedId);
        outcicle.putString("playlist", mPlaylist);
        outcicle.putString("playlist_name", mPlaylistName);
        outcicle.putString("genre", mGenre);
        outcicle.putBoolean("editmode", mEditMode);
        super.onSaveInstanceState(outcicle);
    }

    public void init(Cursor newCursor)
    {
        if (newCursor != null) {
            Log.w("Mp3Tunes", "cursor count: "
                    + Integer.toString(newCursor.getCount()));
        }
        mAdapter.changeCursor(newCursor); // also sets mTrackCursor

        mTrackCursor = newCursor;
        setTitle();

        // When showing the queue, position the selection on the currently
        // playing track
        // Otherwise, position the selection on the first matching artist, if
        // any
        IntentFilter f = new IntentFilter();

        if ("nowplaying".equals(mPlaylist)) {
            try {
                int cur = Music.sService.getQueuePosition();
                setSelection(cur);
                registerReceiver(mNowPlayingListener, new IntentFilter(f));
                mNowPlayingListener.onReceive(this, new Intent(
                        GuiNotifier.META_CHANGED));
            } catch (RemoteException ex) {
            }
        }
    }

    private void setTitle()
    {
        setTitle(R.string.title_tracks);
    }

    private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent)
        {
            getListView().invalidateViews();
        }
    };

    private BroadcastReceiver mNowPlayingListener = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (intent.getAction().equals(GuiNotifier.META_CHANGED)) {
                getListView().invalidateViews();
            } else if (intent.getAction().equals(GuiNotifier.QUEUE_CHANGED)) {

            }
        }
    };

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
        Cursor ret = null;
        mTrackTask = new FetchTracksTask();
        fetch(mTrackTask);
        return ret;
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
    
    private class FetchTracksTask extends FetchBrowserCursor
    {
        @Override
        public Boolean doInBackground(Void... params)
        {
            Timer timer = new Timer("Fetching tracks");
            try {
                LockerDb db = Music.getDb(getBaseContext());
                MediaStore store = new MediaStore(db, getContentResolver());
                if (mAlbumId != null)
                    mCursor = store.getTrackDataByAlbum(mFrom, mAlbumId);
                else if (mPlaylist != null)
                    mCursor = store.getTrackDataByPlaylist(mFrom, new LockerId(mPlaylist));
                else if (mArtistId != null)
                    mCursor = store.getTrackDataByArtist(mFrom, mArtistId);
            } catch (Exception e) {
                Log.w("Mp3Tunes", Log.getStackTraceString(e));
                return false;
            } finally {
                timer.push();
            }
            return true;
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
}
