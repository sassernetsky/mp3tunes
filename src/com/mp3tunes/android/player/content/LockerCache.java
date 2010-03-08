/***************************************************************************
*   Copyright (C) 2009  Casey Link <unnamedrambler@gmail.com>             *
*                                                                         *
*   This program is free software; you can redistribute it and/or modify  *
*   it under the terms of the GNU General Public License as published by  *
*   the Free Software Foundation; either version 3 of the License, or     *
*   (at your option) any later version.                                   *
*                                                                         *
*   This program is distributed in the hope that it will be useful,       *
*   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*   GNU General Public License for more details.                          *
*                                                                         *
*   You should have received a copy of the GNU General Public License     *
*   along with this program; if not, write to the                         *
*   Free Software Foundation, Inc.,                                       *
*   51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.         *
***************************************************************************/
package com.mp3tunes.android.player.content;

import com.mp3tunes.android.player.MP3tunesApplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

public class LockerCache
{
    public class CacheState {
        public static final int UNCACHED = 0;
        public static final int CACHED   = 1;
        public static final int CACHING  = 2;
    }
    
    public class CACHES {
        public static final int ARTIST   = 0;
        public static final int ALBUM    = 1;
        public static final int TRACK    = 2;
        public static final int PLAYLIST = 3;
    }
    
    private int mArtistCacheState;
    private int mAlbumCacheState;
    private int mTrackCacheState;
    private int mPlaylistCacheState;
    
    private long mArtistCacheLastUpdate;
    private long mAlbumCacheLastUpdate;
    private long mTrackCacheLastUpdate;
    private long mPlaylistCacheLastUpdate;
    
    private Progress mArtistProgress;
    private Progress mAlbumProgress;
    private Progress mTrackProgress;
    private Progress mPlaylistProgress;

    //TODO: remove the ugly siwtches
    
    public int getCacheState(int cacheId)
    {
        switch (cacheId) {
            case CACHES.ARTIST:
                return mArtistCacheState;
            case CACHES.ALBUM:
                return mAlbumCacheState;
            case CACHES.TRACK:
                return mTrackCacheState;
            case CACHES.PLAYLIST:
                return mPlaylistCacheState;
        }
        return CacheState.UNCACHED;
    }
    
    public void beginCaching(int cacheId, long time)
    {
        Progress progress = new Progress(20);
        switch (cacheId) {
            case CACHES.ARTIST:
                mArtistCacheState      = CacheState.CACHING;
                mArtistCacheLastUpdate = time;
                mArtistProgress        = progress;
                break;
            case CACHES.ALBUM:
                mAlbumCacheState      = CacheState.CACHING;
                mAlbumCacheLastUpdate = time;
                mAlbumProgress        = progress;
                break;
            case CACHES.TRACK:
                mTrackCacheState      = CacheState.CACHING;
                mTrackCacheLastUpdate = time;
                mTrackProgress        = progress;
                break;
            case CACHES.PLAYLIST:
                mPlaylistCacheState      = CacheState.CACHING;
                mPlaylistCacheLastUpdate = time;
                mPlaylistProgress        = progress;
                break;
        }
    }
    
    public void finishCaching(int cacheId)
    {
        switch (cacheId) {
            case CACHES.ARTIST:
                mArtistCacheState = CacheState.CACHED;
                break;
            case CACHES.ALBUM:
                mAlbumCacheState = CacheState.CACHED;
                break;
            case CACHES.TRACK:
                mTrackCacheState = CacheState.CACHED;
                break;
            case CACHES.PLAYLIST:
                mPlaylistCacheState = CacheState.CACHED;
                break;
        }
    }
    
    public Progress getProgress(int cacheId)
    {
        switch (cacheId) {
            case CACHES.ARTIST:
                return mArtistProgress;
            case CACHES.ALBUM:
                return mAlbumProgress;
            case CACHES.TRACK:
                return mTrackProgress;
            case CACHES.PLAYLIST:
                return mPlaylistProgress;
        }
        return null;
    }
    
    public void clearCache()
    {
        makeMemoryCacheClean();
    }
    
    public void saveCache(LockerDb db)
    {
        db.updateCache(CACHES.ARTIST, mArtistCacheLastUpdate, mArtistCacheState, mArtistProgress);
        db.updateCache(CACHES.ALBUM, mAlbumCacheLastUpdate, mAlbumCacheState, mAlbumProgress);
        db.updateCache(CACHES.TRACK, mTrackCacheLastUpdate, mTrackCacheState, mTrackProgress);
        db.updateCache(CACHES.PLAYLIST, mPlaylistCacheLastUpdate, mPlaylistCacheState, mPlaylistProgress);
    }
  
    public static LockerCache loadCache(LockerDb db)
    {
      LockerCache cache = new LockerCache();
      String[] projection = new String[] {
              DbKeys.ID,
              DbKeys.LAST_UPDATE,
              DbKeys.SET,
              DbKeys.COUNT,
              DbKeys.STATE
      };
      
      Cursor c = db.getCache(projection);
      
      if (c.moveToFirst()) {
          do {
              int  id     = c.getInt(0);
              long update = c.getLong(1);
              Progress progress = cache.new Progress(c.getInt(3));
              progress.mCurrentSet = c.getInt(2);
              int  state  = c.getInt(4);
              switch (id) {
                  case CACHES.ARTIST:
                      cache.mArtistCacheLastUpdate = update;
                      cache.mArtistProgress        = progress;
                      cache.mArtistCacheState      = state;
                      break;
                  case CACHES.ALBUM:
                      cache.mAlbumCacheLastUpdate = update;
                      cache.mAlbumProgress        = progress;
                      cache.mAlbumCacheState      = state;
                      break;
                  case CACHES.TRACK:
                      cache.mTrackCacheLastUpdate = update;
                      cache.mTrackProgress        = progress;
                      cache.mTrackCacheState      = state;
                      break;
                  case CACHES.PLAYLIST:
                      cache.mPlaylistCacheLastUpdate = update;
                      cache.mPlaylistProgress        = progress;
                      cache.mPlaylistCacheState      = state;
                      break;
              }
          } while (c.moveToNext());
      }
      c.close();

      return cache;
    }
    
    private LockerCache()
    {
        makeMemoryCacheClean();
    }
    
    private void makeMemoryCacheClean()
    {
        mArtistCacheState   = CacheState.UNCACHED;
        mAlbumCacheState    = CacheState.UNCACHED;
        mTrackCacheState    = CacheState.UNCACHED;
        mPlaylistCacheState = CacheState.UNCACHED;
        
        mArtistCacheLastUpdate   = -1;
        mAlbumCacheLastUpdate    = -1;
        mTrackCacheLastUpdate    = -1;
        mPlaylistCacheLastUpdate = -1;
        
        mArtistProgress   = null;
        mAlbumProgress    = null;
        mTrackProgress    = null;
        mPlaylistProgress = null;
    }
    
    class Progress
    {
        int mCurrentSet;
        int mCount;
        
        Progress( int count)
        {
            mCurrentSet = 0;
            mCount      = count;
        }
    }
}
