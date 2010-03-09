package com.mp3tunes.android.player.content;


import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;


class LockerDbHelper extends SQLiteOpenHelper
{

    private static final String DB_NAME = "locker.dat";
    private static final int DB_VERSION = 9;
    
    private static final String CREATE_TRACK = "CREATE TABLE " + DbTables.TRACK + "(" +
                                                    DbKeys.ID           + " INTEGER PRIMARY KEY," +
                                                    DbKeys.PLAY_URL     + " VARCHAR," +
                                                    DbKeys.DOWNLOAD_URL + " VARCHAR," +
                                                    DbKeys.TITLE        + " VARCHAR," + 
                                                    DbKeys.TRACK        + " NUMBER(2) DEFAULT 0," +
                                                    DbKeys.ARTIST_ID    + " INTEGER," + 
                                                    DbKeys.ARTIST_NAME  + " VARCHAR," + 
                                                    DbKeys.ALBUM_ID     + " INTEGER," +
                                                    DbKeys.ALBUM_NAME   + " VARCHAR," + 
                                                    DbKeys.TRACK_LENGTH + "," + 
                                                    DbKeys.COVER_URL    + " VARCHAR DEFAULT NULL" + 
                                                ")";
    private static final String CREATE_ARTIST = "CREATE TABLE " + DbTables.ARTIST + "(" +
    	                                            DbKeys.ID          + " INTEGER PRIMARY KEY," +
    	                                            DbKeys.ARTIST_NAME + " VARCHAR," + 
    	                                            DbKeys.ALBUM_COUNT + " INTEGER," + 
    	                                            DbKeys.TRACK_COUNT + " INTEGER" +
                                                ")";
    private static final String CREATE_ALBUM = "CREATE TABLE " + DbTables.ALBUM + "(" +
    	                                            DbKeys.ID          + " INTEGER PRIMARY KEY," + 
    	                                            DbKeys.ALBUM_NAME  + " VARCHAR, " +
    	                                            DbKeys.ARTIST_ID   + " INTEGER, " + 
    	                                            DbKeys.ARTIST_NAME + " VARCHAR, " + 
    	                                            DbKeys.TRACK_COUNT + " INTEGER, " +
    	                                            DbKeys.YEAR        + " INTEGER, " + 
    	                                            DbKeys.COVER_URL   + " VARCHAR DEFAULT NULL" + 
    	                                        ")";
    private static final String CREATE_PLAYLIST = "CREATE TABLE " + DbTables.PLAYLIST + "(" + 
                                                       DbKeys.ID             + " VARCHAR PRIMARY KEY," + 
                                                       DbKeys.PLAYLIST_NAME  + " VARCHAR, " + 
                                                       DbKeys.FILE_COUNT     + " INTEGER," + 
                                                       DbKeys.FILE_NAME      + " VARCHAR," + 
                                                       DbKeys.PLAYLIST_ORDER + " INTEGER" +
                                                   ")";
    private static final String CREATE_PLAYLIST_TRACKS  = "CREATE TABLE " + DbTables.PLAYLIST_TRACKS + "(" + 
                                                               DbKeys.PLAYLIST_ID    + " VARCHAR," + 
                                                               DbKeys.TRACK_ID       + " INTEGER," + 
                                                               DbKeys.PLAYLIST_INDEX + " INTEGER" + 
                                                           ")";

    private static final String CREATE_TOKEN = "CREATE TABLE " + DbTables.TOKEN + "(" + 
                                                    DbKeys.TYPE  + " VARCHAR," + 
                                                    DbKeys.TOKEN + " VARCHAR," + 
                                                    DbKeys.COUNT + " INTEGER" + 
                                               ")";
//    private static final String CREATE_CURRENT_PLAYLIST = "CREATE TABLE " + DbTables.CURRENT_PLAYLIST + "(" + 
//                                                               DbKeys.POS      + " INTEGER PRIMARY KEY," +
//                                                               DbKeys.TRACK_ID + " INTEGER" + 
//                                                           ")";
//     
    private static final String CREATE_CACHE = "CREATE TABLE " + DbTables.CACHE + "(" +
                                                    DbKeys.ID          + " VARCHAR PRIMARY KEY," + 
                                                    DbKeys.LAST_UPDATE + " INTEGER,"             +
                                                    DbKeys.SET         + " INTEGER,"             +
                                                    DbKeys.COUNT       + " INTEGER,"             +
                                                    DbKeys.STATE       + " INTEGER"              +
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
        db.execSQL(CREATE_CACHE);
        //db.execSQL(CREATE_CURRENT_PLAYLIST);

    }

    @Override
    public void onUpgrade( SQLiteDatabase db, int oldV, int newV )
    {
        //db.execSQL(DELETE + DbTables.CURRENT_PLAYLIST);
        db.execSQL(DELETE + DbTables.ALBUM);
        db.execSQL(DELETE + DbTables.ARTIST);
        db.execSQL(DELETE + DbTables.TRACK);
        db.execSQL(DELETE + DbTables.PLAYLIST);
        db.execSQL(DELETE + DbTables.PLAYLIST_TRACKS);
        db.execSQL(DELETE + DbTables.TOKEN);
        db.execSQL(DELETE + DbTables.CACHE);
        onCreate( db );
    }

    public SQLiteDatabase getWritableDatabase()
    {
        return super.getWritableDatabase();

    }

}
