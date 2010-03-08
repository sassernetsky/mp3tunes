package com.mp3tunes.android.player.content;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

public class Content extends ContentProvider
{
    public static final Uri CONTENT_URI = Uri.parse("content://com.mp3tunes.android.player.provider");
    public static final Uri TRACK       = Uri.parse("content://com.mp3tunes.android.player.provider/track");
    public static final Uri ALBUM       = Uri.parse("content://com.mp3tunes.android.player.provider/album");
    public static final Uri ARTIST      = Uri.parse("content://com.mp3tunes.android.player.provider/artist");
    public static final Uri PLAYLIST    = Uri.parse("content://com.mp3tunes.android.player.provider/playlist");
    
    @Override
    public int delete(Uri arg0, String arg1, String[] arg2)
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getType(Uri uri)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean onCreate()
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs)
    {
        // TODO Auto-generated method stub
        return 0;
    }

}
