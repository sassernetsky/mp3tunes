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
import com.mp3tunes.android.player.content.LockerDb.IdPolicyException;

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

    Queries(LockerDb db)
    {
        mDb = db;
    }
    
    public boolean artistExists(int id)
    {
        return runExistsQuery(mDb.mDb, mArtistExists, DbTables.ARTIST, Integer.toString(id));
    }
    
    public boolean albumExists(int id)
    {
        return runExistsQuery(mDb.mDb, mAlbumExists, DbTables.ALBUM, Integer.toString(id));
    }
    
    public boolean trackExists(int id)
    {
        return runExistsQuery(mDb.mDb, mTrackExists, DbTables.TRACK, Integer.toString(id));
    }
    
    public boolean playlistExists(String id)
    {
        return runExistsQuery(mDb.mDb, mPlaylistExists, DbTables.PLAYLIST, id);
    }
    
    public boolean cacheExists(int id)
    {
        return runExistsQuery(mDb.mDb, mCacheExists, DbTables.CACHE, Integer.toString(id));
    }
    
    public boolean updateArtist(Artist a)
    {
        if (mUpdateArtist == null)
            mUpdateArtist = makeUpdateArtistStatement(mDb.mDb);
        
        assertLockerId(a.getId());
        
        mUpdateArtist.bindString(1, a.getName());
        mUpdateArtist.bindLong(  2, a.getAlbumCount());
        mUpdateArtist.bindLong(  3, a.getTrackCount());
        mUpdateArtist.bindLong(  4, a.getId().asInt());
        mUpdateArtist.execute();
        return false;
    }
    
    public boolean updateAlbum(Album a)
    {
        if (mUpdateAlbum == null) 
            mUpdateAlbum = makeUpdateAlbumStatement(mDb.mDb);
        
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
    
    public boolean updatePlaylist(Playlist p, int index)
    {
        if (mUpdatePlaylist == null)
            mUpdatePlaylist = makeUpdatePlaylistStatement(mDb.mDb);
       
        mUpdatePlaylist.bindString(1, p.getName());
        mUpdatePlaylist.bindLong(  2, p.getCount());
        mUpdatePlaylist.bindString(3, p.getFileName());
        mUpdatePlaylist.bindLong(  4, index);
        mUpdatePlaylist.bindString(5, p.getId().asString());
        mUpdatePlaylist.execute();
        return false;
    }
    
    
    public boolean insertArtist(Track t)
    {
        if (mInsertArtistFromTrack == null)
            mInsertArtistFromTrack = makeInsertArtistFromTrackStatement(mDb.mDb);
        
        mInsertArtistFromTrack.bindLong(  1, t.getArtistId());
        mInsertArtistFromTrack.bindString(2, t.getArtistName());
        mInsertArtistFromTrack.execute();
        return false;
    }
    
    public boolean insertAlbum(Track t)
    {
        if (mInsertAlbumFromTrack == null)
            mInsertAlbumFromTrack = makeInsertAlbumFromTrackStatement(mDb.mDb);
        
        mInsertAlbumFromTrack.bindLong(  1, t.getAlbumId());
        mInsertAlbumFromTrack.bindString(2, t.getAlbumTitle());
        mInsertAlbumFromTrack.bindLong(  3, t.getArtistId());
        mInsertAlbumFromTrack.execute();
        return false;
    }
    
    public boolean insertTrack(Track t)
    {
        if (mInsertTrack == null)
            mInsertTrack = makeInsertTrackStatement(mDb.mDb);
        
        assertLockerId(t.getId());
        
        mInsertTrack.bindLong(  1,  t.getId().asInt());
        mInsertTrack.bindString(2,  t.getPlayUrl(0));
        mInsertTrack.bindString(4,  t.getTitle());
        mInsertTrack.bindString(6,  t.getArtistName());
        mInsertTrack.bindString(7,  t.getAlbumTitle());
        mInsertTrack.bindLong(  8,  t.getArtistId());
        mInsertTrack.bindLong(  9,  t.getAlbumId());
        mInsertTrack.execute();
        return false;
    }
    
    public boolean insertAlbum(Album a)
    {
        if (mInsertAlbum == null)
            mInsertAlbum = makeInsertAlbumStatement(mDb.mDb);
        
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
    
    public boolean insertArtist(Artist a)
    {
        if (mInsertArtist == null)
            mInsertArtist = makeInsertArtistStatement(mDb.mDb);
        
        assertLockerId(a.getId());
        
        mInsertArtist.bindLong(  1, a.getId().asInt());
        mInsertArtist.bindString(2, a.getName());
        mInsertArtist.bindLong(  3, a.getAlbumCount());
        mInsertArtist.bindLong(  4, a.getTrackCount());
        mInsertArtist.execute();
        return false;
    }
    
    public boolean insertPlaylist(Playlist p, int index)
    {
        if (mInsertPlaylist == null)
            mInsertPlaylist = makeInsertPlaylistStatement(mDb.mDb);
        
        mInsertPlaylist.bindString(1, p.getId().asString());
        mInsertPlaylist.bindString(2, p.getName());
        mInsertPlaylist.bindLong(  3, p.getCount());
        mInsertPlaylist.bindString(4, p.getFileName());
        mInsertPlaylist.bindLong(  5, index);
        mInsertPlaylist.execute();
        return false;
    }
    
    public void updateCache(int id, long time, int state, Progress progress)
    {
        if (mUpdateCache == null)
            mUpdateCache = makeUpdateCacheStatement(mDb.mDb);
        
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
        mUpdateCache.bindLong(5, id);
        mUpdateCache.execute();
    }

    public void insertCache(int id, long time, int state, Progress progress)
    {
        if (mInsertCache == null)
            mInsertCache = makeInsertCacheStatement(mDb.mDb);
        
        int set   = 0;
        int count = 0;
        if (progress != null) {
            set   = progress.mCurrentSet;
            count = progress.mCount;
        }
        
        mInsertCache.bindLong(1, id);
        mInsertCache.bindLong(2, time);
        mInsertCache.bindLong(3, set);
        mInsertCache.bindLong(4, count);
        mInsertCache.bindLong(5, state);
        mInsertCache.execute();
    }

    static private boolean runExistsQuery(SQLiteDatabase db, SQLiteStatement stmt, String table, String id)
    {
        if (stmt == null)
            stmt = db.compileStatement("SELECT " + DbKeys.ID + 
                                       " FROM "  + table +
                                       " WHERE " + DbKeys.ID + "=?");
        stmt.bindString(1, id);
        try {
            stmt.simpleQueryForString();
            return true;
        } catch (SQLiteDoneException e) {
            return false;
        }
    }
    
    static private SQLiteStatement makeUpdateArtistStatement(SQLiteDatabase db)
    {
        String query = 
            "UPDATE " + DbTables.ARTIST  + " "    +
            "SET "    + DbKeys.ARTIST_NAME + "=?, " +
                        DbKeys.ALBUM_COUNT + "=?, " +
                        DbKeys.TRACK_COUNT + "=? "  +
            "WHERE "  + DbKeys.ID          + "=?";
        return db.compileStatement(query);
    }
    
    static private SQLiteStatement makeUpdateAlbumStatement(SQLiteDatabase db)
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
        return db.compileStatement(query);
    }
    
    static private SQLiteStatement makeUpdatePlaylistStatement(SQLiteDatabase db)
    {
        String query = 
            "UPDATE " + DbTables.PLAYLIST   + " "    +
            "SET "    + DbKeys.FILE_COUNT     + "=?, " +
                        DbKeys.FILE_COUNT     + "=?, " +
                        DbKeys.FILE_NAME      + "=?, " +
                        DbKeys.PLAYLIST_ORDER + "=? "  +
            "WHERE " +  DbKeys.ID             + "=?";
        return db.compileStatement(query);
    }
    
    static private SQLiteStatement makeInsertArtistFromTrackStatement(SQLiteDatabase db)
    {
        String query = "INSERT INTO " + DbTables.ARTIST + " (" +
                            DbKeys.ID          + ", " + 
                            DbKeys.ARTIST_NAME +
        		       ") VALUES (?, ?)";
        return db.compileStatement(query);
    }
    static private SQLiteStatement makeInsertAlbumFromTrackStatement(SQLiteDatabase db)
    {
        String query = "INSERT INTO " + DbTables.ALBUM + " (" +
                            DbKeys.ID + ", " +
                            DbKeys.ALBUM_NAME + ", " +
                            DbKeys.ARTIST_ID +
                       ")  VALUES (?, ?, ?)";
        return db.compileStatement(query);
    }
    static private SQLiteStatement makeInsertTrackStatement(SQLiteDatabase db)
    {
        String query = "INSERT INTO " + DbTables.TRACK + " (" +
                            DbKeys.ID           + ", " +
                            DbKeys.PLAY_URL     + ", " +
                            DbKeys.DOWNLOAD_URL + ", " +
                            DbKeys.TITLE        + ", " +
                            DbKeys.TRACK        + ", " +
                            DbKeys.ARTIST_NAME  + ", " +
                            DbKeys.ALBUM_NAME   + ", " +
                            DbKeys.ARTIST_ID    + ", " +
                            DbKeys.ALBUM_ID     + ", " +
                            DbKeys.TRACK_LENGTH + ", " +
                            DbKeys.COVER_URL    +
                       ")  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return db.compileStatement(query);
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
        return db.compileStatement(query);
    }
    static private SQLiteStatement makeInsertArtistStatement(SQLiteDatabase db)
    {
        String query = "INSERT INTO " + DbTables.ARTIST + " (" +
                            DbKeys.ID          + ", " +
                            DbKeys.ARTIST_NAME + ", " +
                            DbKeys.ALBUM_COUNT + ", " +
                            DbKeys.TRACK_COUNT +
        		       ") VALUES (?, ?, ?, ?)";
        return db.compileStatement(query);
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
        return db.compileStatement(query);
    }
    
    private SQLiteStatement makeInsertCacheStatement(SQLiteDatabase db)
    {
        String query = "INSERT INTO " + DbTables.CACHE + " (" +
                            DbKeys.ID          + ", " +
                            DbKeys.LAST_UPDATE + ", " +
                            DbKeys.SET         + ", " +
                            DbKeys.COUNT       + ", " +
                            DbKeys.STATE       + "" +
                       ") VALUES (?, ?, ?, ?, ?)";
        return db.compileStatement(query);
    }
    
    private SQLiteStatement makeUpdateCacheStatement(SQLiteDatabase db)
    {
        String query = 
            "UPDATE " + DbTables.CACHE     + " "    +
            "SET "    + DbKeys.LAST_UPDATE + "=?, " +
                        DbKeys.SET         + "=?, " +
                        DbKeys.COUNT       + "=?, " +
                        DbKeys.STATE       + "=? "  +
            "WHERE " +  DbKeys.ID          + "=?";
        return db.compileStatement(query);
    }
   
    static private void assertLockerId(Id id)
    {
        if (!LockerId.class.isInstance(id)) {
            throw new IdPolicyException();
        }
    }
}
