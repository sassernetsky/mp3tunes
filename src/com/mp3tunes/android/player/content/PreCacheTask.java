package com.mp3tunes.android.player.content;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import com.binaryelysium.mp3tunes.api.Album;
import com.binaryelysium.mp3tunes.api.Artist;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.Playlist;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.content.LockerCache.Progress;
import com.mp3tunes.android.player.content.Queries.MakeQueryException;

public class PreCacheTask extends RefreshTask
{
    public PreCacheTask(LockerDb db)
    {
        super(db);
    }
    
    private void cacheData(String cache) throws SQLiteException, IOException, LockerException, MakeQueryException
    {
        if (mDb.mCache.getCacheState(cache) == LockerCache.CacheState.UNCACHED) {
            mDb.mCache.beginCaching(cache, System.currentTimeMillis());
            LockerCache.Progress p = mDb.mCache.getProgress(cache);
            if (refreshDispatcher(cache, p))
                p.mCurrentSet++;
        }
    }
    
    @Override
    protected Boolean doInBackground(Void... params)
    {
        Log.w("Mp3Tunes", "Starting PreCache");
        try {
            cacheData(LockerCache.CACHES.ARTIST);
            cacheData(LockerCache.CACHES.ALBUM);
            cacheData(LockerCache.CACHES.PLAYLIST);
            Log.w("Mp3Tunes", "PreCache done");
            return true;
        } catch (SQLiteException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (LockerException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.w("Mp3Tunes", "PreCache Failed");
        return false;
    }

    @Override
    protected boolean dispatch(String cacheId, Progress p, String id)
            throws SQLiteException, IOException, LockerException,
            MakeQueryException
    {
        return false;
    }
    
  private boolean refreshDispatcher(String cacheId, LockerCache.Progress p) throws SQLiteException, IOException, LockerException, MakeQueryException
  {
      if (cacheId.equals(LockerCache.CACHES.ARTIST)) {
              List<Artist> artists = mDb.mLocker.getArtists(p.mCount, p.mCurrentSet);
              lock();
              try {return mDb.multiInsert(artists, p);} finally{unlock();}
      } else if (cacheId.equals(LockerCache.CACHES.ALBUM)) {
              List<Album> albums = mDb.mLocker.getAlbums(p.mCount, p.mCurrentSet);
              lock();
              try {return mDb.multiInsert(albums, p);} finally{unlock();}
      } else if (cacheId.equals(LockerCache.CACHES.TRACK)) {
              List<Track> tracks = mDb.mLocker.getTracks(p.mCount, p.mCurrentSet);
              lock();
              try {return mDb.multiInsert(tracks, p);} finally{unlock();}
      } else if (cacheId.equals(LockerCache.CACHES.PLAYLIST)) {
              List<Playlist> playlists = mDb.mLocker.getPlaylists(p.mCount, p.mCurrentSet);
              lock();
              try {return mDb.multiInsert(playlists, p);} finally{unlock();}
      } 
      return false;
  }
  
  @Override
  protected  void onPostExecute(Boolean result)
  {
      if (result) {
          cleanUp();
      }
  }
  
}

