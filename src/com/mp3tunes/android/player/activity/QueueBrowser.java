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

import com.mp3tunes.android.player.LockerDb;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.MusicAlphabetIndexer;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.service.GuiNotifier;
import com.mp3tunes.android.player.util.BaseMp3TunesListActivity;

public class QueueBrowser extends BaseMp3TunesListActivity implements
        View.OnCreateContextMenuListener, Music.Defs, ServiceConnection
{
    private boolean mEditMode = false;
    private boolean mAdapterSent = false;
    private ListView mTrackList;
    private Cursor mTrackCursor;
    private TrackListAdapter mAdapter;
    private String mAlbumId;
    private String mArtistId;
    private String mPlaylist;
    private String mPlaylistName;
    private String mGenre;
    private boolean mPlayNow;
    private long mSelectedId;
    //private AsyncTask<Void, Void, Boolean> mTrackTask;

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
        if (icicle != null) {
            mSelectedId = icicle.getLong("selectedtrack");
            mAlbumId = icicle.getString("album");
            mArtistId = icicle.getString("artist");
            mPlaylist = icicle.getString("playlist");
            mPlaylistName = icicle.getString("playlist_name");
            mGenre = icicle.getString("genre");
            mEditMode = icicle.getBoolean("editmode", false);
        } else {
            mAlbumId = getIntent().getStringExtra("album");
            // If we have an album, show everything on the album, not just stuff
            // by a particular artist.
            Intent intent = getIntent();
            mArtistId     = intent.getStringExtra("artist");
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

        mAdapter = (TrackListAdapter) getLastNonConfigurationInstance();

        if (mAdapter != null) {
            mAdapter.setActivity(this);
            setListAdapter(mAdapter);
        }
        Music.bindToService(this, this);
    }

    public void onServiceConnected(ComponentName name, IBinder service)
    {

        if (mAdapter == null) {
            // need to use application context to avoid leaks
            mAdapter = new TrackListAdapter(getApplication(), this,
                    R.layout.track_list_item, null, // cursor
                    new String[] {}, new int[] {}, "nowplaying"
                            .equals(mPlaylist), mPlaylist != null
                            && !(mPlaylist.equals("podcasts") || mPlaylist
                                    .equals("recentlyadded")), mPlayNow);
            mPlayNow = false;
            setListAdapter(mAdapter);
            setTitle(R.string.title_working_songs);
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
        TrackListAdapter a = mAdapter;
        mAdapterSent = true;
        return a;
    }

    @Override
    public void onDestroy()
    {
        //if (mTrackTask != null
        //        && mTrackTask.getStatus() == AsyncTask.Status.RUNNING)
        //    mTrackTask.cancel(true);
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
        outcicle.putLong("selectedtrack", mSelectedId);
        outcicle.putString("artist", mArtistId);
        outcicle.putString("album", mAlbumId);
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

        if (mTrackCursor == null) {
            // Music.displayDatabaseError(this);
            closeContextMenu();
            return;
        }

        // Music.hideDatabaseError(this);
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

        CharSequence fancyName = null;
        if (mAlbumId != null) {
            int numresults = mTrackCursor != null ? mTrackCursor.getCount() : 0;
            if (numresults > 0) {
                mTrackCursor.moveToFirst();
                int album = Music.TRACK_MAPPING.ALBUM_NAME;
                int artist = Music.TRACK_MAPPING.ALBUM_NAME;
                fancyName = mTrackCursor.getString(artist) + " - "
                        + mTrackCursor.getString(album);
                if (fancyName == null
                        || fancyName.equals(LockerDb.UNKNOWN_STRING)) {
                    fancyName = getString(R.string.unknown_album_name);
                }
            }
        } else if (mPlaylist != null) {
            if (mPlaylist.equals("nowplaying")) {
                fancyName = getText(R.string.title_nowplaying);
            } else {
                fancyName = mPlaylistName;
            }
        }
        if (fancyName != null)
            setTitle(fancyName);
        else
            setTitle(R.string.title_tracks);

    }

    public void moveQueueItem(int from, int to)
    {
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
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        return super.onContextItemSelected(item);
    }

    // In order to use alt-up/down as a shortcut for moving the selected item
    // in the list, we need to override dispatchKeyEvent, not onKeyDown.
    // (onKeyDown never sees these events, since they are handled by the list)
    @Override
    public boolean dispatchKeyEvent(KeyEvent event)
    {
        if (mPlaylist != null && event.getMetaState() != 0
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    //moveItem(true);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    //moveItem(false);
                    return true;
                case KeyEvent.KEYCODE_DEL:
                    //removeItem();
                    return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }





    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        if (mTrackCursor.getCount() == 0)
            return;

        Music.playAll(this, mTrackCursor, position);
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
                Music.playAll(this, mTrackCursor, 0);
                return true;

            case R.id.menu_opt_shuffleall:
                if (mTrackCursor.getCount() == 0)
                    break;
                Music.shuffleAll(this, mTrackCursor);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private Cursor getTrackCursor(String filter)
    {
        Cursor ret = null;
        fetch(new FetchTracksTask());
        return ret;
    }

    static class TrackListAdapter extends SimpleCursorAdapter implements
            SectionIndexer
    {

        boolean mIsNowPlaying;
        boolean mDisableNowPlayingIndicator;

        int mTitleIdx;
        int mArtistIdx;
        int mAlbumIdx;
        int mDurationIdx;
        int mAudioIdIdx;

        private final StringBuilder mBuilder = new StringBuilder();
        private final String mUnknownArtist;
        // private final String mUnknownAlbum;

        private AlphabetIndexer mIndexer;

        private QueueBrowser mActivity = null;
        private String mConstraint = null;
        private boolean mConstraintIsValid = false;

        class ViewHolder
        {
            ImageView icon;
            TextView line1;
            TextView line2;
            TextView duration;
            ImageView play_indicator;
            CharArrayBuffer buffer1;
            char[] buffer2;
        }

        TrackListAdapter(Context context, QueueBrowser currentactivity,
                int layout, Cursor cursor, String[] from, int[] to,
                boolean isnowplaying, boolean disablenowplayingindicator, boolean playnow)
        {
            super(context, layout, cursor, from, to);
            mActivity = currentactivity;
            getColumnIndices(cursor);
            mIsNowPlaying = isnowplaying;
            mDisableNowPlayingIndicator = disablenowplayingindicator;
            mUnknownArtist = context.getString(R.string.unknown_artist_name);
            // mUnknownAlbum =
            context.getString(R.string.unknown_album_name);
        }

        public void setActivity(QueueBrowser newactivity)
        {
            mActivity = newactivity;
        }

        private void getColumnIndices(Cursor cursor)
        {
            if (cursor != null) {
                mTitleIdx = Music.TRACK_FOR_BROWSER_MAPPING.TITLE;
                mArtistIdx = Music.TRACK_FOR_BROWSER_MAPPING.ARTIST_NAME;
                mAlbumIdx = Music.TRACK_FOR_BROWSER_MAPPING.ALBUM_NAME;
                mDurationIdx = Music.TRACK_FOR_BROWSER_MAPPING.TRACK_LENGTH;
                mAudioIdIdx = Music.TRACK_FOR_BROWSER_MAPPING.ID;

                if (mIndexer != null) {
                    mIndexer.setCursor(cursor);
                } else if (!mActivity.mEditMode) {
                    String alpha = mActivity.getString(R.string.alphabet);

                    mIndexer = new MusicAlphabetIndexer(cursor, mTitleIdx,
                            alpha);
                }

            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent)
        {
            View v = super.newView(context, cursor, parent);

            ViewHolder vh = new ViewHolder();
            vh.icon = (ImageView) v.findViewById(R.id.icon);
            vh.line1 = (TextView) v.findViewById(R.id.line1);
            vh.line2 = (TextView) v.findViewById(R.id.line2);
            vh.duration = (TextView) v.findViewById(R.id.duration);
            vh.play_indicator = (ImageView) v.findViewById(R.id.play_indicator);
            vh.buffer1 = new CharArrayBuffer(100);
            vh.buffer2 = new char[200];

            //if (mActivity.mEditMode) {
            //    vh.icon.setVisibility(View.VISIBLE);
            //    vh.icon.setImageResource(R.drawable.ic_mp_move);
            //} else {
                vh.icon.setImageResource(R.drawable.song_icon);
                vh.icon.setVisibility(View.VISIBLE);
            //}

            v.setTag(vh);
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor)
        {
            if (cursor == null)
                return;
            ViewHolder vh = (ViewHolder) view.getTag();

            cursor.copyStringToBuffer(mTitleIdx, vh.buffer1);
            vh.line1.setText(vh.buffer1.data, 0, vh.buffer1.sizeCopied);
            long secs = 0; // cursor.getLong( mDurationIdx ) / 1000;
            if (secs == 0) {
                vh.duration.setText("");
            } else {
                vh.duration.setText(Music.makeTimeString(context, secs));
            }

            final StringBuilder builder = mBuilder;
            builder.delete(0, builder.length());

            String name = cursor.getString(mArtistIdx);
            if (name == null) {
                builder.append(mUnknownArtist);
            } else {
                builder.append(name);
            }
            int len = builder.length();
            if (vh.buffer2.length < len) {
                vh.buffer2 = new char[len];
            }
            builder.getChars(0, len, vh.buffer2, 0);
            vh.line2.setText(vh.buffer2, 0, len);

            ImageView iv = vh.play_indicator;
            int id = -1;
            if (mIsNowPlaying)
                // we -1 form the current position because the queue is
                // 1 based in the sql table
                id = Music.getCurrentQueuePosition() - 1;
            else
                id = Music.getCurrentTrackId();

            // Determining whether and where to show the "now playing indicator
            // is tricky, because we don't actually keep track of where the
            // songs
            // in the current playlist came from after they've started playing.
            //
            // If the "current playlists" is shown, then we can simply match by
            // position,
            // otherwise, we need to match by id. Match-by-id gets a little
            // weird if
            // a song appears in a playlist more than once, and you're in
            // edit-playlist
            // mode. In that case, both items will have the "now playing"
            // indicator.
            // For this reason, we don't show the play indicator at all when in
            // edit
            // playlist mode (except when you're viewing the "current playlist",
            // which is not really a playlist)
            if ((mIsNowPlaying && cursor.getPosition() == id)
                    || (!mIsNowPlaying && !mDisableNowPlayingIndicator && cursor
                            .getInt(mAudioIdIdx) == id)) {
                iv.setImageResource(R.drawable.indicator_ic_mp_playing_list);
                iv.setVisibility(View.VISIBLE);
            } else {
                iv.setVisibility(View.GONE);
            }
        }

        @Override
        public void changeCursor(Cursor cursor)
        {
            try {
                if (cursor != mActivity.mTrackCursor) {
                    mActivity.mTrackCursor = cursor;

                    super.changeCursor(cursor);
                    getColumnIndices(cursor);
                }
            } catch (Exception e) {
                Log.e("Mp3Tunes", Log.getStackTraceString(e));
            }
        }

        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint)
        {
            String s = constraint.toString();
            if (mConstraintIsValid
                    && ((s == null && mConstraint == null) || (s != null && s
                            .equals(mConstraint)))) {
                return getCursor();
            }
            Cursor c = mActivity.getTrackCursor(s);
            mConstraint = s;
            mConstraintIsValid = true;
            return c;
        }

        // SectionIndexer methods

        public Object[] getSections()
        {
            if (mIndexer != null) {
                return mIndexer.getSections();
            } else {
                return null;
            }
        }

        public int getPositionForSection(int section)
        {
            int pos = mIndexer.getPositionForSection(section);
            return pos;
        }

        public int getSectionForPosition(int position)
        {
            return 0;
        }
    }

    private class FetchTracksTask extends FetchBrowserCursor
    {
        @Override
        public Boolean doInBackground(Void... params)
        {
            try {
                LockerDb db = Music.getDb(getBaseContext());
                if (mAlbumId != null)
                    mCursor = db.getTracksForAlbum(Integer.valueOf(mAlbumId));
                else if (mPlaylist != null)
                    mCursor = db.getTracksForPlaylist(mPlaylist);
                else
                    mCursor = db.getTableList(Music.Meta.TRACK);
            } catch (Exception e) {
                Log.w("Mp3Tunes", Log.getStackTraceString(e));
                return false;
            }
            return true;
        }
    }
}
