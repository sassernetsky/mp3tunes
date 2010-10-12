package com.mp3tunes.android.player.content;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.binaryelysium.mp3tunes.api.Album;
import com.binaryelysium.mp3tunes.api.Artist;
import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.binaryelysium.mp3tunes.api.Playlist;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.content.DbKeys;
import com.mp3tunes.android.player.content.LockerCache.Progress;

public class Queries
{
    private LockerDb mDb;
    
    private SQLiteStatement    mInsertArtistFromTrack;
    private SQLiteStatement    mInsertAlbumFromTrack;
    private SQLiteStatement    mInsertTrack;
    private SQLiteStatement    mInsertArtist;
    private SQLiteStatement    mInsertAlbum;
    private SQLiteStatement    mInsertPlaylist;

    private SQLiteStatement    mUpdateArtist;
    private SQLiteStatement    mUpdateAlbum;
    private SQLiteStatement    mUpdatePlaylist;

    private SQLiteStatement    mArtistExists;
    private SQLiteStatement    mAlbumExists;
    private SQLiteStatement    mTrackExists;
    private SQLiteStatement    mPlaylistExists;
    
    private SQLiteStatement    mCacheExists;
    private SQLiteStatement    mInsertCache;
    private SQLiteStatement    mUpdateCache;
    
    private SQLiteStatement    mTrackInPlaylist;

    Queries(LockerDb db) throws MakeQueryException
    {
        mDb = db;
        mInsertArtistFromTrack = makeInsertArtistFromTrackStatement(mDb.mDb);
        mInsertAlbumFromTrack  = makeInsertAlbumFromTrackStatement(mDb.mDb);
        mInsertTrack           = makeInsertTrackStatement(mDb.mDb);
        mInsertArtist          = makeInsertArtistStatement(mDb.mDb);
        mInsertAlbum           = makeInsertAlbumStatement(mDb.mDb);
        mInsertPlaylist        = makeInsertPlaylistStatement(mDb.mDb);
        mUpdateArtist          = makeUpdateArtistStatement(mDb.mDb);
        mUpdateAlbum           = makeUpdateAlbumStatement(mDb.mDb);
        mUpdatePlaylist        = makeUpdatePlaylistStatement(mDb.mDb);
        mArtistExists          = makeExistsQuery(mDb.mDb, DbTables.ARTIST);
        mAlbumExists           = makeExistsQuery(mDb.mDb, DbTables.ALBUM);
        mTrackExists           = makeExistsQuery(mDb.mDb, DbTables.TRACK);
        mPlaylistExists        = makeExistsQuery(mDb.mDb, DbTables.PLAYLIST);
        mCacheExists           = makeExistsQuery(mDb.mDb, DbTables.CACHE);
        mInsertCache           = makeInsertCacheStatement(mDb.mDb);
        mUpdateCache           = makeUpdateCacheStatement(mDb.mDb);;
        mTrackInPlaylist       = makeTrackInPlaylistQuery(mDb.mDb);
    }

    public boolean artistExists(int id) throws MakeQueryException
    {
        return runExistsQuery(mDb.mDb, mArtistExists, DbTables.ARTIST, Integer.toString(id));
    }
    
    public boolean albumExists(int id) throws MakeQueryException
    {
        return runExistsQuery(mDb.mDb, mAlbumExists, DbTables.ALBUM, Integer.toString(id));
    }
    
    public boolean trackExists(int id) throws MakeQueryException
    {
        return runExistsQuery(mDb.mDb, mTrackExists, DbTables.TRACK, Integer.toString(id));
    }
    
    public boolean playlistExists(String id) throws MakeQueryException
    {
        return runExistsQuery(mDb.mDb, mPlaylistExists, DbTables.PLAYLIST, id);
    }
    
    public boolean cacheExists(String id) throws MakeQueryException
    {
        return runExistsQuery(mDb.mDb, mCacheExists, DbTables.CACHE, id);
    }
    
    public boolean trackInPlaylist(String playlist, int track) throws MakeQueryException
    {
        mTrackInPlaylist.bindString(1, playlist);
        mTrackInPlaylist.bindLong(  2, track);
        try {
            mTrackInPlaylist.simpleQueryForString();
            return true;
        } catch (SQLiteDoneException e) {
            return false;
        }
    }
    
    public boolean updateArtist(Artist a) throws MakeQueryException
    {
        assertLockerId(a.getId());
        
        mUpdateArtist.bindString(1, a.getName());
        mUpdateArtist.bindLong(  2, a.getAlbumCount());
        mUpdateArtist.bindLong(  3, a.getTrackCount());
        mUpdateArtist.bindLong(  4, a.getId().asInt());
        mUpdateArtist.execute();
        return false;
    }
    
    public boolean updateAlbum(Album a) throws MakeQueryException
    {
        assertLockerId(a.getId());
        
        String year;
        if (a.getYear() != null)
            year = a.getYear();
        else
            year = DbKeys.UNKNOWN_STRING;
        mUpdateAlbum.bindString(1, a.getName());
        mUpdateAlbum.bindString(2, a.getArtistName());
        mUpdateAlbum.bindLong(  3, a.getArtistId());
        mUpdateAlbum.bindString(4, year);
        mUpdateAlbum.bindLong(  5, a.getTrackCount());
        mUpdateAlbum.bindString(6, DbKeys.UNKNOWN_STRING);
        mUpdateAlbum.bindLong(  7, a.getId().asInt());
        mUpdateAlbum.execute();
        return false;
    }
    
    public boolean updatePlaylist(Playlist p, int index) throws MakeQueryException
    {
        mUpdatePlaylist.bindString(1, p.getName());
        mUpdatePlaylist.bindLong(  2, p.getCount());
        mUpdatePlaylist.bindString(3, p.getFileName());
        mUpdatePlaylist.bindLong(  4, index);
        mUpdatePlaylist.bindString(5, p.getId().asString());
        mUpdatePlaylist.execute();
        return false;
    }
    
    
    public boolean insertArtist(Track t) throws MakeQueryException
    {
        mInsertArtistFromTrack.bindLong(  1, t.getArtistId());
        mInsertArtistFromTrack.bindString(2, t.getArtistName());
        mInsertArtistFromTrack.execute();
        return false;
    }
    
    public boolean insertAlbum(Track t) throws MakeQueryException
    {
        mInsertAlbumFromTrack.bindLong(  1, t.getAlbumId());
        mInsertAlbumFromTrack.bindString(2, t.getAlbumTitle());
        mInsertAlbumFromTrack.bindLong(  3, t.getArtistId());
        mInsertAlbumFromTrack.execute();
        return false;
    }
    
    public boolean insertTrack(Track t, int trackNumber) throws MakeQueryException
    {
        assertLockerId(t.getId());
        
        mInsertTrack.bindLong(  1,  t.getId().asInt());
        mInsertTrack.bindString(2,  t.getPlayUrl(null, 0));
        mInsertTrack.bindString(4,  "x"  + t.getTitle());
        mInsertTrack.bindLong(  5,  1000 + trackNumber);  // Adding 1000 is cheaper than creating a DecimalFormatter to zero-pad on the left...
        mInsertTrack.bindString(6,  t.getArtistName());
        mInsertTrack.bindString(7,  t.getAlbumTitle());
        mInsertTrack.bindLong(  8,  t.getArtistId());
        mInsertTrack.bindLong(  9,  t.getAlbumId());
        mInsertTrack.execute();
        return false;
    }
    
    public boolean insertAlbum(Album a) throws MakeQueryException
    {
        assertLockerId(a.getId());
        
        String year;
        if (a.getYear() != null)
            year = a.getYear();
        else
            year = DbKeys.UNKNOWN_STRING;
        mInsertAlbum.bindLong(  1, a.getId().asInt());
        mInsertAlbum.bindString(2, a.getName());
        mInsertAlbum.bindString(3, a.getArtistName());
        mInsertAlbum.bindLong(  4, a.getArtistId());
        mInsertAlbum.bindString(5, year);
        mInsertAlbum.bindLong(  6, a.getTrackCount());
        mInsertAlbum.bindString(7, DbKeys.UNKNOWN_STRING);
        mInsertAlbum.execute();
        return false;
    }
    
    public boolean insertArtist(Artist a) throws MakeQueryException
    {
        assertLockerId(a.getId());
        
        mInsertArtist.bindLong(  1, a.getId().asInt());
        mInsertArtist.bindString(2, a.getName());
        mInsertArtist.bindLong(  3, a.getAlbumCount());
        mInsertArtist.bindLong(  4, a.getTrackCount());
        mInsertArtist.execute();
        return false;
    }
    
    public boolean insertPlaylist(Playlist p, int index) throws MakeQueryException
    {
        mInsertPlaylist.bindString(1, p.getId().asString());
        mInsertPlaylist.bindString(2, p.getName());
        mInsertPlaylist.bindLong(  3, p.getCount());
        mInsertPlaylist.bindString(4, p.getFileName());
        mInsertPlaylist.bindLong(  5, index);
        mInsertPlaylist.execute();
        return false;
    }
    
    public void updateCache(String id, long time, int state, Progress progress) throws MakeQueryException
    {
        int set   = 0;
        int count = 0;
        if (progress != null) {
            set   = progress.mCurrentSet;
            count = progress.mCount;
        }
        
        mUpdateCache.bindLong(1, time);
        mUpdateCache.bindLong(2, set);
        mUpdateCache.bindLong(3, count);
        mUpdateCache.bindLong(4, state);
        mUpdateCache.bindString(5, id);
        mUpdateCache.execute();
    }

    public void insertCache(String id, long time, int state, Progress progress) throws MakeQueryException
    {
        int set   = 0;
        int count = 0;
        if (progress != null) {
            set   = progress.mCurrentSet;
            count = progress.mCount;
        }
        
        mInsertCache.bindString(1, id);
        mInsertCache.bindLong(2, time);
        mInsertCache.bindLong(3, set);
        mInsertCache.bindLong(4, count);
        mInsertCache.bindLong(5, state);
        mInsertCache.execute();
    }

    static private boolean runExistsQuery(SQLiteDatabase db, SQLiteStatement stmt, String table, String id) throws MakeQueryException
    {
        stmt.bindString(1, id);
        try {
            stmt.simpleQueryForString();
            return true;
        } catch (SQLiteDoneException e) {
            return false;
        }
    }
    
    static private SQLiteStatement makeTrackInPlaylistQuery(SQLiteDatabase db) throws MakeQueryException
    {
        String query = 
            "SELECT " + DbKeys.PLAYLIST_INDEX + 
            " FROM "  + DbTables.PLAYLIST_TRACKS + 
            " WHERE " + 
                DbKeys.PLAYLIST_ID + "=? AND " + 
                DbKeys.TRACK_ID    + "=?";

        return makeStatement(db, query);
    }
    
    
    static private SQLiteStatement makeExistsQuery(SQLiteDatabase db, String table) throws MakeQueryException
    {
        String query = 
            "SELECT " + DbKeys.ID + 
            " FROM "  + table +
            " WHERE " + DbKeys.ID + "=?";
        return makeStatement(db, query);
    }
    
    
    static private SQLiteStatement makeUpdateArtistStatement(SQLiteDatabase db) throws MakeQueryException
    {
        String query = 
            "UPDATE " + DbTables.ARTIST  + " "    +
            "SET "    + DbKeys.ARTIST_NAME + "=?, " +
                        DbKeys.ALBUM_COUNT + "=?, " +
                        DbKeys.TRACK_COUNT + "=? "  +
            "WHERE "  + DbKeys.ID          + "=?";
        return makeStatement(db, query);
    }
    
    static private SQLiteStatement makeUpdateAlbumStatement(SQLiteDatabase db) throws MakeQueryException
    {
        String query = 
            "UPDATE " + DbTables.ALBUM   + " "    +
            "SET "    + DbKeys.ALBUM_NAME  + "=?, " +
                        DbKeys.ARTIST_NAME + "=?, " +
                        DbKeys.ARTIST_ID   + "=?, " +
                        DbKeys.YEAR        + "=?, " +
                        DbKeys.TRACK_COUNT + "=?, " +
                        DbKeys.COVER_URL   + "=? "  +
            "WHERE "  + DbKeys.ID          + "=?";
        return makeStatement(db, query);
    }
    
    static private SQLiteStatement makeUpdatePlaylistStatement(SQLiteDatabase db) throws MakeQueryException
    {
        String query = 
            "UPDATE " + DbTables.PLAYLIST   + " "    +
            "SET "    + DbKeys.FILE_COUNT     + "=?, " +
                        DbKeys.FILE_COUNT     + "=?, " +
                        DbKeys.FILE_NAME      + "=?, " +
                        DbKeys.PLAYLIST_ORDER + "=? "  +
            "WHERE " +  DbKeys.ID             + "=?";
        return makeStatement(db, query);
    }
    
    static private SQLiteStatement makeInsertArtistFromTrackStatement(SQLiteDatabase db) throws MakeQueryException
    {
        String query = "INSERT INTO " + DbTables.ARTIST + " (" +
                            DbKeys.ID          + ", " + 
                            DbKeys.ARTIST_NAME +
        		       ") VALUES (?, ?)";
        return makeStatement(db, query);
    }
    
    static private SQLiteStatement makeInsertAlbumFromTrackStatement(SQLiteDatabase db) throws MakeQueryException
    {
        String query = "INSERT INTO " + DbTables.ALBUM + " (" +
                            DbKeys.ID + ", " +
                            DbKeys.ALBUM_NAME + ", " +
                            DbKeys.ARTIST_ID +
                       ")  VALUES (?, ?, ?)";
        return makeStatement(db, query);
    }
    
    static private SQLiteStatement makeInsertTrackStatement(SQLiteDatabase db)
    {
        String query = "INSERT INTO " + DbTables.TRACK + " (" +
                            DbKeys.ID           + ", " +
                            DbKeys.PLAY_URL     + ", " +
                            DbKeys.DOWNLOAD_URL + ", " +
                            DbKeys.TITLE        + ", " +
                            DbKeys.ORDINAL      + ", " +
                            DbKeys.ARTIST_NAME  + ", " +
                            DbKeys.ALBUM_NAME   + ", " +
                            DbKeys.ARTIST_ID    + ", " +
                            DbKeys.ALBUM_ID     + ", " +
                            DbKeys.TRACK_LENGTH + ", " +
                            DbKeys.COVER_URL    +
                       ")  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return makeStatement(db, query);
    }
    
    static private SQLiteStatement makeInsertAlbumStatement(SQLiteDatabase db)
    {
        String query = "INSERT INTO " + DbTables.ALBUM + " (" +
                            DbKeys.ID          + ", " +
                            DbKeys.ALBUM_NAME  + ", " +
                            DbKeys.ARTIST_NAME + ", " +
                            DbKeys.ARTIST_ID   + ", " +
                            DbKeys.YEAR        + ", " +
                            DbKeys.TRACK_COUNT + ", " +
                            DbKeys.COVER_URL   +
        		       ")  VALUES (?, ?, ?, ?, ?, ?, ?)";
        return makeStatement(db, query);
    }
    
    static private SQLiteStatement makeInsertArtistStatement(SQLiteDatabase db)
    {
        String query = "INSERT INTO " + DbTables.ARTIST + " (" +
                            DbKeys.ID          + ", " +
                            DbKeys.ARTIST_NAME + ", " +
                            DbKeys.ALBUM_COUNT + ", " +
                            DbKeys.TRACK_COUNT +
        		       ") VALUES (?, ?, ?, ?)";
        return makeStatement(db, query);
    }
    
    static private SQLiteStatement makeInsertPlaylistStatement(SQLiteDatabase db)
    {
        String query = "INSERT INTO " + DbTables.PLAYLIST + " (" +
                            DbKeys.ID             + ", " +
                            DbKeys.PLAYLIST_NAME  + ", " +
                            DbKeys.FILE_COUNT     + ", " +
                            DbKeys.FILE_NAME      + ", " +
                            DbKeys.PLAYLIST_ORDER +
                       ") VALUES (?, ?, ?, ?, ?)";
        return makeStatement(db, query);
    }
    
    static  private SQLiteStatement makeInsertCacheStatement(SQLiteDatabase db)
    {
        String query = "INSERT INTO " + DbTables.CACHE + " (" +
                            DbKeys.ID          + ", " +
                            DbKeys.LAST_UPDATE + ", " +
                            DbKeys.SET         + ", " +
                            DbKeys.COUNT       + ", " +
                            DbKeys.STATE       + "" +
                       ") VALUES (?, ?, ?, ?, ?)";
        return makeStatement(db, query);
    }
    
    static  private SQLiteStatement makeUpdateCacheStatement(SQLiteDatabase db)
    {
        String query = 
            "UPDATE " + DbTables.CACHE     + " "    +
            "SET "    + DbKeys.LAST_UPDATE + "=?, " +
                        DbKeys.SET         + "=?, " +
                        DbKeys.COUNT       + "=?, " +
                        DbKeys.STATE       + "=? "  +
            "WHERE " +  DbKeys.ID          + "=?";
        return makeStatement(db, query);
    }
    
    static private SQLiteStatement makeStatement(SQLiteDatabase db, String query) 
    {
        try {
            return db.compileStatement(query);
        } catch (Exception e) {
            Log.w("MP3tunes", Log.getStackTraceString(e));
            throw new MakeQueryException();
        }
    }
   
    static private void assertLockerId(Id id)
    {
        if (!LockerId.class.isInstance(id)) {
            throw new Id.IdPolicyException();
        }
    }
    
    static public class MakeQueryException extends RuntimeException
    {

        /**
         * 
         */
        private static final long serialVersionUID = 3120482315125882355L;
        
    };
}
