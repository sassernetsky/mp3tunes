package com.mp3tunes.android.player.content;

import java.io.IOException;
import android.content.ContentResolver;
import android.database.Cursor;
import android.database.CursorJoiner;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.LocalId;
import com.mp3tunes.android.player.content.Queries.MakeQueryException;
import com.mp3tunes.android.player.util.Timer;

public class MediaStore
{
    private LockerDb        mLockerDb;
    private ContentResolver mCr;
    
    public MediaStore(LockerDb db, ContentResolver cr)
    {
        mLockerDb = db;
        mCr       = cr;
    }
    
    public static final Uri sArtistsUri = android.provider.MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
    public static final Uri sAlbumsUri  = android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
    public static final Uri sTracksUri   = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    
    public static final String KEY_LOCAL = "local";
    
    public static final class STORAGE {
        public static final int LOCKER = 0;
        public static final int LOCAL  = 1;
        public static final int BOTH   = 2;
    }
    
    public Cursor getArtistData(String[] columns)throws IOException, LockerException
    {
        String[] joinBy =  new String[] {DbKeys.ARTIST_NAME};

        String[] cols = rmLocalCol(columns);
        String[] projection = lockerDbToMediaStoreColumns(cols);
        String where = android.provider.MediaStore.Audio.Media.ARTIST + "!=?";
        String[] whereArgs = new String[] {"<unknown>"};

        Cursor locker = mLockerDb.getArtistData(cols, null);
        Cursor store = mCr.query(sArtistsUri, projection, where, whereArgs, 
                              lockerDbToMediaStoreKey(DbKeys.ARTIST_NAME));
        return merge(locker, store, columns, joinBy);
        //return getData(columns, joinBy, sArtistsUri, DbKeys.ARTIST_NAME, new GetArtists());
    }
    
    public Cursor getAlbumData(String[] columns)throws IOException, LockerException
    {
        Timer timer = new Timer("Get Album Data");
        try {
        String[] joinBy = new String[] {DbKeys.ALBUM_NAME};
        return getData(columns, joinBy, sAlbumsUri, DbKeys.ALBUM_NAME,  new GetAlbums());
        } finally {
            timer.push();
        }
    }
    
    public Cursor getAlbumDataByArtist(String[] columns, Id id) throws IOException, LockerException
    {
        String[] joinBy = new String[] {DbKeys.ALBUM_NAME};
        return getDataById(columns, joinBy, sAlbumsUri, DbKeys.ALBUM_NAME, id, 
                           android.provider.MediaStore.Audio.Media.ARTIST, new ArtistGetter(mLockerDb, mCr),
                            new GetAlbumsByArtist());
    }

    public Cursor getTrackDataByAlbum(String[] columns, Id id) throws SQLiteException, IOException, LockerException
    {
        String[] joinBy = new String[] {DbKeys.TITLE};
        return getDataById(columns, joinBy, sTracksUri, DbKeys.TITLE, id, 
                           android.provider.MediaStore.Audio.Media.ALBUM, new AlbumGetter(mLockerDb, mCr),
                            new GetTracksByAlbum());
    }

    public Cursor getTrackDataByArtist(String[] columns, Id id) throws IOException, LockerException
    {
        String[] joinBy = new String[] {DbKeys.TITLE};
        return getDataById(columns, joinBy, sTracksUri, DbKeys.TITLE, id, 
                android.provider.MediaStore.Audio.Media.ARTIST, new ArtistGetter(mLockerDb, mCr),
                 new GetTracksByArtist());
    }
    
    public Cursor getTrackDataByPlaylist(String[] columns, Id id) throws SQLiteException, IOException, LockerException
    {
        String[] cols = rmLocalCol(columns);
        if (LockerId.class.isInstance(id)) {
        //String[] joinBy = new String[] {DbKeys.TITLE};
        //String[] projection = lockerDbToMediaStoreColumns(cols);
        
        //no matter what columns the caller requests we need the Artist, Album, and Title to be returned
        //so we make sure they are in the projection here
        //cols = ensureColInColumns(cols, DbKeys.TITLE);
        //cols = ensureColInColumns(cols, DbKeys.ALBUM_NAME); 
        //cols = ensureColInColumns(cols, DbKeys.ARTIST_NAME);
        
            Cursor locker = mLockerDb.getTrackDataByPlaylist(cols, (LockerId)id);
            MatrixCursor output = new MatrixCursor(columns);
            int len = columns.length - 1;
            if (locker.moveToFirst()) {
                do {
                    MatrixCursor.RowBuilder builder = output.newRow();
                    buildRow(builder, locker, len, 0);
                } while (locker.moveToNext());
            }
            locker.close();
            return output;
        
        } else {
            //At this point we only have one local playlist which is the list of all local tracks
            return null;
        }
    }
    
    public Cursor getLocalTracksForPlaylist(String[] columns)
    {
        String[] cols       = lockerDbToMediaStoreColumns(rmLocalCol(columns));
        String[] projection = new String[cols.length + 1];
        System.arraycopy(cols, 0, projection, 0, cols.length);
        projection[projection.length - 1] = android.provider.MediaStore.MediaColumns.DATE_ADDED;
        String where = android.provider.MediaStore.Audio.AudioColumns.IS_MUSIC + "!=0 AND " + 
                       android.provider.MediaStore.Audio.Media.ARTIST + "!=\"<unknown>\" AND " +
                       android.provider.MediaStore.Audio.Media.ARTIST + "!=\"Ringtone\"";
        
        Cursor c = mCr.query(sTracksUri, projection, where, null, android.provider.MediaStore.MediaColumns.DATE_ADDED);
        
        MatrixCursor output = new MatrixCursor(columns);
        int len = cols.length;
        if (c.moveToLast()) {
            do {
                MatrixCursor.RowBuilder builder = output.newRow();
                for (int i = 0; i < len; i++) {
                    builder.add(c.getString(i));
                }
                builder.add(1);
            } while (c.moveToPrevious());
        }
        return output;
    }
    
    public Track getTrack(Id id)
    {
        try {
            return (Track)(new TrackGetter(mLockerDb, mCr).get(id));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (LockerException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    private String[] lockerDbToMediaStoreColumns(String[] from)
    {
        String[] output = new String[from.length];
        for (int i = 0; i < from.length; i++){
            output[i] = lockerDbToMediaStoreKey(from[i]);
        }
        return output;
    }
    
    private String lockerDbToMediaStoreKey(String from)
    {
        if (from.equals(DbKeys.ARTIST_NAME))
            return android.provider.MediaStore.Audio.Media.ARTIST;
        else if (from.equals(DbKeys.ALBUM_COUNT))
            return android.provider.MediaStore.Audio.ArtistColumns.NUMBER_OF_ALBUMS;
        else if (from.equals(DbKeys.ID))
            return android.provider.BaseColumns._ID;
        else if (from.equals(DbKeys.ALBUM_NAME))
            return android.provider.MediaStore.Audio.Media.ALBUM;
        return from;
    }
    
    private Cursor merge(Cursor locker, Cursor store, String[] columns, String[] joinOn)
    {
        Timer timer = new Timer("Merge took");
        try { 
        MatrixCursor output;
        if (locker == null) {
            output = addColumnToCursor(store, columns, 1);
        } else if (store  == null) {
            output = addColumnToCursor(locker, columns, 0);
        } else {
            output = new MatrixCursor(columns);
            CursorJoiner joiner = new CursorJoiner(locker, joinOn, store, lockerDbToMediaStoreColumns(joinOn));
            
            int len = columns.length - 1;
            for (CursorJoiner.Result joinerResult : joiner) {
                MatrixCursor.RowBuilder builder = output.newRow();
                switch (joinerResult) {
                    case LEFT:
                        //Log.w("Mp3Tunes", "Locker: \"" + locker.getString(1) + "\"");
                        buildRow(builder, locker, len, STORAGE.LOCKER);
                        break;
                    case RIGHT:
                        //Log.w("Mp3Tunes", "store: \"" + store.getString(1) + "\"");
                        buildRow(builder, store, len, STORAGE.LOCAL);
                        break;
                    case BOTH: 
                        //Log.w("Mp3Tunes", "Both: \"" + store.getString(1) + "\"");
                        buildRow(builder, store, len, STORAGE.BOTH);
                        break;
                }
            }
            locker.close();
            store.close();
        }
        return output;
        } finally {
            timer.push();
        }
    }
    
    private String[] rmLocalCol(String[] array)
    {
        if (array[array.length - 1].equals(KEY_LOCAL)) {
            String[] a = new String[array.length - 1];
            System.arraycopy(array, 0, a, 0, array.length - 1);
            return a;
        }
        return array;
    }
    
//    private String[] ensureColInColumns(String[] array, String key) 
//    {
//        String[] sorted = new String[array.length];
//        System.arraycopy(array, 0, sorted, 0, array.length);
//        Arrays.sort(sorted);
//        if (Arrays.binarySearch(sorted, key) < 0) {
//            String[] old = array;
//            array = new String[old.length + 1];
//            System.arraycopy(old, 0, array, 0, old.length);
//            array[array.length - 1] = key;
//        }
//        return array;
//    }
    
    private MatrixCursor addColumnToCursor(Cursor c, String[] columns, int local)
    {
        MatrixCursor output = new MatrixCursor(columns);
        if (c == null) return output;
        int len = columns.length - 1;
        if (c.moveToFirst()) {
            do {
                MatrixCursor.RowBuilder builder = output.newRow();
                buildRow(builder, c, len, local);
            } while (c.moveToNext());
        }
        c.close();
        return output;
    }
    
    private void buildRow(MatrixCursor.RowBuilder builder, Cursor c, int len, int local)
    {
        for (int i = 0; i < len; i++) {
            builder.add(c.getString(i));
        }
        builder.add(local);
    }

    private Cursor getData(String[] columns, String[] joinBy, Uri storeUri, String order, LockerDataCall call) throws SQLiteException, IOException, LockerException
    {
        String[] cols = rmLocalCol(columns);
        String[] projection = lockerDbToMediaStoreColumns(cols);

        Cursor locker = call.get(cols);
        Cursor store = mCr.query(storeUri, projection, null, null, 
                              lockerDbToMediaStoreKey(order));
        return merge(locker, store, columns, joinBy);
    }
    
    private Cursor getDataById(String[] columns, String[] joinBy, Uri storeUri, String order, Id id, String nameKey, MergeHelper helper, LockerDataByCall call) throws IOException, LockerException
    {
        String[] cols = rmLocalCol(columns);
        String[] projection = lockerDbToMediaStoreColumns(cols);
        
        LocalId  localId  = helper.getLocalId(id);
        LockerId lockerId = helper.getLockerId(id);
        String   name     = helper.getName(id);
        
        
        Cursor locker = null;
        Cursor store  = null;
        if (localId != null) {
            String[] args = new String[] {name};
            store = mCr.query(storeUri, projection, nameKey + "=?", args, 
                              lockerDbToMediaStoreKey(order));
        }
        if (lockerId != null) {
            locker = call.get(cols, lockerId);
        }
        return merge(locker, store, columns, joinBy);
    }

    
    
    
    abstract public class LockerDataCall {
        public abstract Cursor get(String[] columns) throws SQLiteException, IOException, LockerException;
    };
    
    public class GetAlbums extends LockerDataCall
    {
        public Cursor get(String[] columns) throws SQLiteException, IOException, LockerException
        {
            return mLockerDb.getAlbumData(columns, null);//
        }
    };
    
    public class GetArtists extends LockerDataCall
    {
        public Cursor get(String[] columns) throws IOException, LockerException
        {
            return mLockerDb.getArtistData(columns, null);
        }
    };
    
    abstract public class LockerDataByCall {
        public abstract Cursor get(String[] columns, LockerId id) throws SQLiteException, IOException, LockerException;
    };
    
    public class GetAlbumsByArtist extends LockerDataByCall
    {
        public Cursor get(String[] columns, LockerId id) throws IOException, LockerException, SQLiteException
        {
            try {
                return mLockerDb.getAlbumDataByArtist(columns, id);
            } catch (MakeQueryException e) {
                e.printStackTrace();
            }
            return null;
        }
    };
    
    public class GetTracksByAlbum extends LockerDataByCall
    {
        public Cursor get(String[] columns, LockerId id) throws IOException, LockerException
        {
            return mLockerDb.getTrackDataByAlbum(columns, id);
        }
    };
    
    public class GetTracksByArtist extends LockerDataByCall
    {
        public Cursor get(String[] columns, LockerId id) throws IOException, LockerException
        {
            return mLockerDb.getTrackDataByArtist(columns, id);
        }
    };
}
