package com.mp3tunes.android.player;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;


class LockerDbHelper extends SQLiteOpenHelper
{

    private static final String DB_NAME = "locker.dat";
    private static final int DB_VERSION = 4;
    
    private static final String CREATE_TRACK = "CREATE TABLE " + LockerDb.TABLE_TRACK + "(" +
                                                    LockerDb.KEY_ID           + " INTEGER PRIMARY LockerDb.KEY," +
                                                    LockerDb.KEY_PLAY_URL     + " VARCHAR," +
                                                    LockerDb.KEY_DOWNLOAD_URL + " VARCHAR," +
                                                    LockerDb.KEY_TITLE        + " VARCHAR," + 
                                                    LockerDb.KEY_TRACK        + " NUMBER(2) DEFAULT 0," +
                                                    LockerDb.KEY_ARTIST_ID    + " INTEGER," + 
                                                    LockerDb.KEY_ARTIST_NAME  + " VARCHAR," + 
                                                    LockerDb.KEY_ALBUM_ID     + " INTEGER," +
                                                    LockerDb.KEY_ALBUM_NAME   + " VARCHAR," + 
                                                    LockerDb.KEY_TRACK_LENGTH + "," + 
                                                    LockerDb.KEY_COVER_URL    + " VARCHAR DEFAULT NULL" + 
                                                ")";
    private static final String CREATE_ARTIST = "CREATE TABLE " + LockerDb.TABLE_ARTIST + "(" +
    	                                            LockerDb.KEY_ID          + " INTEGER PRIMARY LockerDb.KEY," +
    	                                            LockerDb.KEY_ARTIST_NAME + " VARCHAR," + 
    	                                            LockerDb.KEY_ALBUM_COUNT + " INTEGER," + 
    	                                            LockerDb.KEY_TRACK_COUNT + " INTEGER" +
                                                ")";
    private static final String CREATE_ALBUM = "CREATE LockerDb.TABLE " + LockerDb.TABLE_ALBUM + "(" +
    	                                            LockerDb.KEY_ID          + " INTEGER PRIMARY LockerDb.KEY," + 
    	                                            LockerDb.KEY_ALBUM_NAME  + " VARCHAR, " +
    	                                            LockerDb.KEY_ARTIST_ID   + " INTEGER, " + 
    	                                            LockerDb.KEY_ARTIST_NAME + " VARCHAR, " + 
    	                                            LockerDb.KEY_TRACK_COUNT + " INTEGER, " +
    	                                            LockerDb.KEY_YEAR        + " INTEGER, " + 
    	                                            LockerDb.KEY_COVER_URL   + " VARCHAR DEFAULT NULL" + 
    	                                        ")";
    private static final String CREATE_PLAYLIST = "CREATE TABLE " + LockerDb.TABLE_PLAYLIST + "(" + 
                                                       LockerDb.KEY_ID             + " VARCHAR PRIMARY LockerDb.KEY," + 
                                                       LockerDb.KEY_PLAYLIST_NAME  + " VARCHAR, " + 
                                                       LockerDb.KEY_FILE_COUNT     + " INTEGER," + 
                                                       LockerDb.KEY_FILE_NAME      + " VARCHAR," + 
                                                       LockerDb.KEY_PLAYLIST_ORDER + " INTEGER" +
                                                   ")";
    private static final String CREATE_PLAYLIST_TRACKS  = "CREATE LockerDb.TABLE " + LockerDb.TABLE_PLAYLIST_TRACKS + "(" + 
                                                               LockerDb.KEY_PLAYLIST_ID    + " VARCHAR," + 
                                                               LockerDb.KEY_TRACK_ID       + " INTEGER," + 
                                                               LockerDb.KEY_PLAYLIST_INDEX + " INTEGER" + 
                                                           ")";
    private static final String CREATE_TOKEN = "CREATE TABLE " + LockerDb.TABLE_TOKEN + "(" + 
                                                    LockerDb.KEY_TYPE  + " VARCHAR," + 
                                                    LockerDb.KEY_TOKEN + " VARCHAR," + 
                                                    LockerDb.KEY_COUNT + " INTEGER" + 
                                               ")";
    private static final String CREATE_CURRENT_PLAYLIST = "CREATE TABLE " + LockerDb.TABLE_CURRENT_PLAYLIST + "(" + 
                                                               LockerDb.KEY_POS      + " INTEGER PRIMARY LockerDb.KEY," +
                                                               LockerDb.KEY_TRACK_ID + " INTEGER" + 
                                                           ")";
    
    private static final String DELETE = "DROP TABLE IF EXISTS ";
    
    public LockerDbHelper(Context context, CursorFactory factory)
    {
        super(context, DB_NAME, factory, DB_VERSION);
    }

    @Override
    public void onCreate( SQLiteDatabase db )
    {
        db.execSQL(CREATE_TRACK);
        db.execSQL(CREATE_ARTIST);
        db.execSQL(CREATE_ALBUM);
        db.execSQL(CREATE_PLAYLIST);
        db.execSQL(CREATE_PLAYLIST_TRACKS);
        db.execSQL(CREATE_TOKEN);
        db.execSQL(CREATE_CURRENT_PLAYLIST);

    }

    @Override
    public void onUpgrade( SQLiteDatabase db, int oldV, int newV )
    {
        db.execSQL(DELETE + LockerDb.TABLE_CURRENT_PLAYLIST);
        db.execSQL(DELETE + LockerDb.TABLE_ALBUM);
        db.execSQL(DELETE + LockerDb.TABLE_ARTIST);
        db.execSQL(DELETE + LockerDb.TABLE_TRACK);
        db.execSQL(DELETE + LockerDb.TABLE_PLAYLIST);
        db.execSQL(DELETE + LockerDb.TABLE_PLAYLIST_TRACKS);
        db.execSQL(DELETE + LockerDb.TABLE_TOKEN);
        //mCache.clearCache();
        onCreate( db );
    }

    public SQLiteDatabase getWritableDatabase()
    {
        return super.getWritableDatabase();

    }

}
