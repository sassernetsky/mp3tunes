package com.mp3tunes.android.player;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDiskIOException;

import com.binaryelysium.mp3tunes.api.Track;

public class CurrentPlaylist {
	
	private SQLiteDatabase mDb;
	
	public CurrentPlaylist(Context context) {
		mDb = ( new LockerDbHelper(context, null)).getWritableDatabase();
        if ( mDb == null )
        {
            throw new SQLiteDiskIOException( "Error creating database" );
        }
	}
	
	/**
     * return the song at pos in the queue
     * 
     * @param pos NOTE: Positions are 1 indexed i.e., the first song is @ pos = 1
     * @return a complete Track obj of the song
     */
    public Track getTrackQueue( int pos )
    {
        Cursor c = mDb.query( "track, current_playlist", Music.TRACK, "current_playlist.pos=" + pos+" AND track._id=current_playlist.track_id", null, null, null, null );
//        Cursor c = mDb.rawQuery("SELECT play_url FROM song, current_playlist WHERE pos="+pos+" AND song._id=current_playlist.id", null);
        if ( !c.moveToFirst() )
        {
            c.close();
            return null;
        }
        
        Track t = new Track(
                c.getInt( Music.TRACK_MAPPING.ID ),
                c.getString( Music.TRACK_MAPPING.PLAY_URL ),
                c.getString( Music.TRACK_MAPPING.DOWNLOAD_URL ),
                c.getString( Music.TRACK_MAPPING.TITLE ),
                c.getInt( Music.TRACK_MAPPING.TRACKNUM ),
                c.getInt( Music.TRACK_MAPPING.ARTIST_ID ),
                c.getString( Music.TRACK_MAPPING.ARTIST_NAME ),
                c.getInt( Music.TRACK_MAPPING.ALBUM_ID ),
                c.getString( Music.TRACK_MAPPING.ALBUM_NAME ),
                c.getString( Music.TRACK_MAPPING.COVER_URL ) );
        c.close();
        return t;
    }
    
    /**
     * insert several tracks into the playlist queue
     * Note: the song ids are not verified!
     * @param ids the songs ids
     */
    public void insertQueueItems( int[] ids )
    {
        for( int id : ids )
        {
            appendQueueItem( id );
        }
    }
    
    /**
     * appends one track into the queue
     * Note: the song ids are not verified!
     * @param ids the song id
     */
    public void appendQueueItem( int id )
    {
        mDb.execSQL("INSERT INTO current_playlist(track_id) VALUES("+id+")");
    }
    
    /**
     * Insert an entire artist into the playlist.
     * @param id the artist id
     */
    public void insertArtistQueue( int id )
    {
        mDb.execSQL("INSERT INTO current_playlist(track_id) " +
                "SELECT track._id FROM track " +
                "WHERE track.artist_id = " + id);
    }
    
    /**
     * Insert an entire album into the playlist.
     * @param id album id
     */
    public void insertAlbumQueue( int id )
    {
        mDb.execSQL("INSERT INTO current_playlist(track_id) " +
                "SELECT track._id FROM track " +
                "WHERE track.album_id = " + id);
    }
    
    public Cursor getQueueCursor()
    {
        return mDb.query( "track, current_playlist", Music.TRACK, "track._id=current_playlist.track_id", null, null, null, "pos" );   
    }
    
    public int[] getQueue()
    {
        Cursor c = mDb.rawQuery( "select * from current_playlist ORDER BY 'pos'", null );
        
        int size = c.getCount();
        int[] queue = new int[size];

        c.moveToFirst();
        for ( int i = 0; i < size; i++ )
        {
            queue[i] = c.getInt( 1 );
            c.moveToNext();
        }
        c.close();
        return queue;
    }
    
    /**
     * Returns the size of the current playlist
     *
     * @return  size of the playlist or -1 if an error occurs
     */
    public int getQueueSize()
    {
        int size = -1;
        Cursor c = mDb.rawQuery("SELECT COUNT(track_id) FROM current_playlist" ,null);
        if(c.moveToFirst())
        {
            size = c.getInt(0);
        }
        c.close();
        return size;
    }
    
    /**
    * Removes the range of tracks specified from the queue
    * @param first The first track to be removed
    * @param last The last track to be removed
    * @return the number of tracks deleted
    */

    public int removeQueueItem(int first, int last)
    {
        if( last < first )
            return 0;
        int num_deleted = 0;
        while( first < last )
        {
            int res = mDb.delete( "current_playlist", "pos="+first++, null);
            if(res != 0)
                num_deleted++;
        }
        return num_deleted;   
    }
    
    /**
     * Moves the item at index1 to index2.
     * @param index1
     * @param index2
     */
    public void moveQueueItem( int index1, int index2 )
    {
        System.out.println("Move queue item " + index1 + " to " + index2);
        int queue_len = getQueueSize();
        
        //TODO this is incredibly inefficient, ideally this should be replaced
        // with a couple nifty sql queries.
//        Cursor c = mDb.query( "current_playlist", new String[] {"pos", "track_id"}, null, null, null, null, "pos" );
        Cursor c = mDb.rawQuery( "select * from current_playlist ORDER BY 'pos'", null );
        int[] queue = new int[queue_len];
        
        while(c.moveToNext())
        {
            int ididx = c.getColumnIndex( "track_id" );
            int posidx = c.getColumnIndex( "pos" );
            int pos = c.getInt( posidx ) - 1; // sqlite is 1 indexed
            int id =  c.getInt( ididx );
            queue[pos] = id;
        }
        
        if (index1 >= queue_len) {
            index1 = queue_len - 1;
        }
        if (index2 >= queue_len) {
            index2 = queue_len - 1;
        }
        if (index1 < index2) {
            int tmp = queue[index1];
            for (int i = index1; i < index2; i++) {
                queue[i] = queue[i+1];
            }
            queue[index2] = tmp;
        } else if (index2 < index1) {
            int tmp = queue[index1];
            for (int i = index1; i > index2; i--) {
                queue[i] = queue[i-1];
            }
            queue[index2] = tmp;
        }
        c.close();
        clearQueue();
        insertQueueItems( queue );
    }
    
    /**
     * Clear the current playlist
     */
    public void clearQueue()
    {
        mDb.execSQL("DELETE FROM current_playlist");
    }
}
