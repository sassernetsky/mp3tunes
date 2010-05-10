package com.mp3tunes.android.player.activity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ExpandableListActivity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.SimpleCursorTreeAdapter;
import android.widget.TextView;

import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.mp3tunes.android.player.IdParcel;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.content.DbKeys;
import com.mp3tunes.android.player.content.LockerDb;
import com.mp3tunes.android.player.util.AlphabeticalTheRemovedIndexer;
import com.mp3tunes.android.player.util.ReindexingCursorWrapper;

public class QueryBrowser2 extends ExpandableListActivity 
{
    private final static int PROGRESS = 7;
    private final static int NO_RESULTS = 8;
    
    ArtistAlbumListAdapter mAdapter;
    Cursor mParentCursor;
    Cursor mArtistsCursor;
    Cursor mTracksCursor;
    SearchTask   mSearchTask;
    String       mFilterString;
    
    private AlertDialog mProgDialog;
    private AlertDialog mNoResults;
    
    private static final int ARTIST = 1;
    private static final int TRACK  = 2;
    
    static String[] mGroupFrom = new String[] {"_id", "type", "count"};
    static String[] mChildFrom = new String[] {"_id", "name1", "name2", "type"}; 
    static int[]    mGroupTo   = new int[] {0, R.id.line1, R.id.line2};    
    static int[]    mChildTo   = new int[] {0, R.id.line1, R.id.line2, R.id.icon};
    
    @Override
    public void onCreate(Bundle in)
    {
        super.onCreate(in);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        Music.bindToService(this);
        Music.ensureSession(this);
        
        if (in == null) {
            Intent intent = getIntent();
            
            if (intent.getAction().equals(Intent.ACTION_SEARCH)) {
               System.out.println("ACTION = SEARCH!");
            }
            mFilterString = intent.getStringExtra(SearchManager.QUERY);
        }
        
        AlertDialog.Builder builder;
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.progress_dialog,
                                       (ViewGroup) findViewById(R.id.layout_root));

        TextView text = (TextView) layout.findViewById(R.id.progress_text);
        text.setText(R.string.loading_search);

        builder = new AlertDialog.Builder(this);
        builder.setView(layout);
        mProgDialog = builder.create();
        
        AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
        builder1.setMessage("No Results");
        mNoResults = builder1.create();
        
        mParentCursor  = new MatrixCursor(mGroupFrom);
        //createParentRow(0, getBaseContext().getString(R.string.artists), mParentCursor, 0);
        //createParentRow(1, getBaseContext().getString(R.string.tracks),  mParentCursor, 0);
        
        mArtistsCursor = new MatrixCursor(mChildFrom);
        mTracksCursor  = new MatrixCursor(mChildFrom);
        mAdapter = new ArtistAlbumListAdapter();
        this.setListAdapter(mAdapter);
        
        mSearchTask = new SearchTask(mFilterString);
        mSearchTask.execute();
    }
    
    @Override
    public void onStop() 
    {
        killTasks();
        super.onStop();
    }
    
    @Override
    public void onDestroy() {
        killTasks();
        Music.unbindFromService(this);
        Music.unconnectFromDb( this );
        super.onDestroy();
    }
    @Override
    protected Dialog onCreateDialog( int id )
    {
        switch ( id )
        {
        case PROGRESS:
            return mProgDialog;
        case NO_RESULTS:
            return mNoResults;
        default:
            return null;
        }
    }
    
    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id)
    {
        if (mParentCursor.moveToPosition(groupPosition)) {
            String type = mParentCursor.getString(1);
            if (type.equals("Artists")) {
                if (mArtistsCursor.moveToPosition(childPosition)) {
                    int    artist = mArtistsCursor.getInt(0);
                    String name   = mArtistsCursor.getString(1);
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    intent.setDataAndType(Uri.EMPTY, "vnd.mp3tunes.android.dir/track");
                    intent.putExtra("artist", new IdParcel(new LockerId(artist)));
                    intent.putExtra("name", name);
                    startActivity(intent);
                }
            } else if (type.equals("Tracks")) {
                if (mTracksCursor.moveToPosition(childPosition)) {
                    int track = mTracksCursor.getInt(0);
                    Id [] list = new Id[] {new LockerId(track)};
                    Music.playAll(this, list, 0);
                }
            }
        }
        
        return false;
    }
    
    class ArtistAlbumListAdapter extends SimpleCursorTreeAdapter
    {
        private final BitmapDrawable mArtistIcon;
        private final BitmapDrawable mTrackIcon;
        
        ArtistAlbumListAdapter() 
        {
            super(getBaseContext(), 
                  mParentCursor, 
                  R.layout.search_parent, 
                  mGroupFrom, 
                  mGroupTo, 
                  R.layout.track_list_item, 
                  mChildFrom, 
                  mChildTo);
            
            Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.song_icon);
            mTrackIcon = new BitmapDrawable(b);
            mTrackIcon.setFilterBitmap(false);
            mTrackIcon.setDither(false);
            
            b = BitmapFactory.decodeResource(getResources(), R.drawable.artist_icon);
            mArtistIcon = new BitmapDrawable(b);
            mArtistIcon.setFilterBitmap(false);
            mArtistIcon.setDither(false);
        }

        @Override
        public void bindGroupView(View v, Context context, Cursor cursor, boolean isexpanded) 
        {
            Log.w("Mp3Tunes", "bindGroupView");
            String text = cursor.getString(1);
            
            TextView first  = (TextView)v.findViewById(R.id.line1);
            first.setText(text);
            
            TextView second = (TextView)v.findViewById(R.id.line2);
            second.setText(cursor.getString(2));
        }
        @Override
        public void bindChildView(View v, Context context, Cursor cursor, boolean islast) 
        {
            String text = cursor.getString(1);
            
            TextView first  = (TextView)v.findViewById(R.id.line1);
            first.setText(text);
            
            TextView second = (TextView)v.findViewById(R.id.line2);
            second.setText(cursor.getString(2));
            
            ImageView view  = (ImageView)v.findViewById(R.id.icon);
            int type = cursor.getInt(3);
            if (type == ARTIST) {
                view.setImageDrawable(mArtistIcon);
                view.setPadding(0, 0, 1, 0);
                second.setText("");
            } else if (type == TRACK) {
                view.setImageDrawable(mTrackIcon);
                view.setPadding(0, 0, 1, 0);
            }
        }
        
        @Override
        protected Cursor getChildrenCursor(Cursor groupCursor) 
        {
            String type = groupCursor.getString(1);
            if (type.equals("Artists")) {
                return mArtistsCursor;
            } else if (type.equals("Tracks")) {
                return mTracksCursor;
            }
            return null;
        }
    }

    private void createParentRow(int id, String title, MatrixCursor c, int num)
    {
        MatrixCursor.RowBuilder builder = c.newRow();
        builder.add(id);
        builder.add(title);
        builder.add(format(num, title));
    }
    
    private String format(int num, String plural)
    {
        if (num == 1) 
            plural = plural.substring(0, plural.length() - 1) ;
        plural = Integer.toString(num) + " " + plural;
        return plural;
    }
    
    private void killTasks()
    {
        if( mSearchTask != null && mSearchTask.getStatus() == AsyncTask.Status.RUNNING) {
            mSearchTask.cancelSafe();
        }
    }
    
    private class SearchTask extends LockerDb.RefreshSearchTask
    {
        SearchTask(String query)
        {
            super (Music.getDb(getBaseContext()), 
                   new DbSearchQuery(query, true, false, true), 
                   new String[] {DbKeys.ID, DbKeys.TITLE, DbKeys.ARTIST_NAME},
                   new String[] {DbKeys.ID, DbKeys.ARTIST_NAME, DbKeys.ALBUM_COUNT});
        }
        
        @Override
        public void onPreExecute()
        {
            showDialog( PROGRESS );
        }

//        @Override
//        public Boolean doInBackground( Void... params )
//        {
//            System.out.println("Searching for " + mQuery);
//            String[] artist = new String[] {
//                    DbKeys.ID, DbKeys.ARTIST_NAME, DbKeys.ALBUM_COUNT
//            };
//            String[] track = new String[] {
//                    DbKeys.ID, DbKeys.TITLE, DbKeys.ARTIST_NAME
//            };
//            try {
//                
//                LockerDb db = Music.getDb(getBaseContext());
//                res = db.search(db.new DbSearchQuery(mQuery, true, false, true), track, artist);
//            } catch ( Exception e ) {
//                e.printStackTrace();
//                return false;
//            }
//            return true;
//        }

        @Override
        public void onPostExecute( Boolean result )
        {
            dismissDialog( PROGRESS );
            if (!result || mResult == null || (mResult.mArtists == null && mResult.mTracks == null)) {
                Log.w("Mp3Tunes", "Search Failed");
                return;
            }
            
            MatrixCursor parentCursor  = new MatrixCursor(mGroupFrom);
            createParentRow(0, getBaseContext().getString(R.string.artists), parentCursor, mResult.mArtists.getCount());
            createParentRow(1, getBaseContext().getString(R.string.tracks),  parentCursor, mResult.mTracks.getCount());
            mParentCursor =  parentCursor;
            
            mArtistsCursor = new ReindexingCursorWrapper(addColumnToCursor(mResult.mArtists, ARTIST), new AlphabeticalTheRemovedIndexer(), 1);
            mTracksCursor  = new ReindexingCursorWrapper(addColumnToCursor(mResult.mTracks,  TRACK),  new AlphabeticalTheRemovedIndexer(), 1);
            mAdapter.changeCursor(mParentCursor);
            
            if (mResult.mArtists != null)
                mResult.mArtists.close();
            if (mResult.mTracks != null)
                mResult.mTracks.close();
        }
    }
    
    private MatrixCursor addColumnToCursor(Cursor c, int val)
    {
        MatrixCursor output = new MatrixCursor(mChildFrom);
        
        if (c.moveToFirst()) {
            do {
                MatrixCursor.RowBuilder builder = output.newRow();
                for (int i = 0; i < mChildFrom.length - 1; i++) {
                    builder.add(c.getString(i));
                }
                builder.add(val);
            } while (c.moveToNext());
        }
        return output;
    }
}
