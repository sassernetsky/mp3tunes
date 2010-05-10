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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.binaryelysium.mp3tunes.api.Album;
import com.binaryelysium.mp3tunes.api.Artist;
import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.Playlist;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.content.Queries.MakeQueryException;

import android.database.Cursor;
import android.database.sqlite.SQLiteException;

public class LockerCache
{
    public class CacheState {
        public static final int UNCACHED = 0;
        public static final int CACHED   = 1;
        public static final int CACHING  = 2;
    }
    
    public class CACHES {
        public static final String ARTIST   = "ARTIST_CACHE";
        public static final String ALBUM    = "ALBUM_CACHE";
        public static final String TRACK    = "TRACK_CACHE";
        public static final String PLAYLIST = "PLAYLIST_CACHE";
    }
    
    Map<String, CacheItem> mMap = new HashMap<String, CacheItem>();
    
    static public class CacheItem 
    {
        int      mState;
        long     mUpdate;
        Progress mProgress;
        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((mProgress == null) ? 0 : mProgress.hashCode());
            result = prime * result + mState;
            result = prime * result + (int) (mUpdate ^ (mUpdate >>> 32));
            return result;
        }
        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CacheItem other = (CacheItem) obj;
            if (mProgress == null) {
                if (other.mProgress != null)
                    return false;
            } else if (!mProgress.equals(other.mProgress))
                return false;
            if (mState != other.mState)
                return false;
            if (mUpdate != other.mUpdate)
                return false;
            return true;
        }
        
        
    };
    
    public int getCacheState(String cacheId)
    {
        try {
            return mMap.get(cacheId).mState;
        } catch (Exception e) {
            return CacheState.UNCACHED;
        }
    }
    
    public void beginCaching(String cacheId, long time)
    {
        CacheItem item = mMap.get(cacheId);
        if (item == null) {
            item = new CacheItem();
            mMap.put(cacheId, item);
        }
        item.mProgress = new Progress(20);
        item.mState    = CacheState.CACHING;
        item.mUpdate   = time;
    }
    
    public void finishCaching(String cacheId)
    {
        CacheItem item = mMap.get(cacheId);
        if (Playlist.isDynamicPlaylist(cacheId)) {
            item.mState    = CacheState.UNCACHED;
        } else {
            item.mState    = CacheState.CACHED;
        }
    }
    
    public Progress getProgress(String cacheId)
    {
        CacheItem item = mMap.get(cacheId);
        return item.mProgress;
    }
    
    public void clearCache()
    {
        makeMemoryCacheClean();
    }
    
    public void saveCache(LockerDb db)
    {
        for (Map.Entry<String, CacheItem> entry : mMap.entrySet()) {
            CacheItem item = entry.getValue();
            try {
                db.updateCache(entry.getKey(), item.mUpdate, item.mState, item.mProgress);
            } catch (MakeQueryException e) {
                e.printStackTrace();
            }
        }
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
              String id   = c.getString(0);
              
              CacheItem item = new CacheItem();
              item.mUpdate               = c.getLong(1);
              item.mProgress             = cache.new Progress(c.getInt(3));
              item.mProgress.mCurrentSet = c.getInt(2);
              item.mState                = c.getInt(4);
              
              cache.mMap.put(id, item);
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
        mMap.clear();
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

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + mCount;
            result = prime * result + mCurrentSet;
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Progress other = (Progress) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (mCount != other.mCount)
                return false;
            if (mCurrentSet != other.mCurrentSet)
                return false;
            return true;
        }

        private LockerCache getOuterType()
        {
            return LockerCache.this;
        }
        
    }
    
  
    static public class RefreshArtistsTask extends RefreshTask
    {
        public RefreshArtistsTask(LockerDb db)
        {
            super(db, LockerCache.CACHES.ARTIST, null);
        }

        @Override
        protected boolean dispatch(String cacheId, Progress p, String id)
                throws SQLiteException, IOException, LockerException
        {
            List<Artist> artists = mDb.mLocker.getArtists(p.mCount, p.mCurrentSet);
            return mDb.multiInsert(artists, p);
        }
    }

    static public class RefreshAlbumsTask extends RefreshTask
    {
        public RefreshAlbumsTask(LockerDb db)
        {
            super(db, LockerCache.CACHES.ALBUM, null);
        }

        @Override
        protected boolean dispatch(String cacheId, Progress p, String id)
                throws SQLiteException, IOException, LockerException
        {
            List<Album> albums = mDb.mLocker.getAlbums(p.mCount, p.mCurrentSet);
            return mDb.multiInsert(albums, p);
        }
    }
    
    static public class RefreshPlaylistsTask extends RefreshTask
    {
        public RefreshPlaylistsTask(LockerDb db)
        {
            super(db, LockerCache.CACHES.PLAYLIST, null);
        }

        @Override
        protected boolean dispatch(String cacheId, Progress p, String id)
                throws SQLiteException, IOException, LockerException
        {
            List<Playlist> playlists = mDb.mLocker.getPlaylists(p.mCount, p.mCurrentSet);
            return mDb.multiInsert(playlists, p);
        }
    }
    
    static public class RefreshTracksTask extends RefreshTask
    {
        public RefreshTracksTask(LockerDb db)
        {
            super(db, LockerCache.CACHES.TRACK, null);
        }

        @Override
        protected boolean dispatch(String cacheId, Progress p, String id)
                throws SQLiteException, IOException, LockerException
        {
            List<Track> tracks = mDb.mLocker.getTracks(p.mCount, p.mCurrentSet);
            return mDb.multiInsert(tracks, p);
        }
    }
    
    static public class RefreshPlaylistTracksTask extends RefreshTask
    {
        //this declaration hides mId in the parent class. 
        protected Id mId;
        public RefreshPlaylistTracksTask(LockerDb db, Id id)
        {
            super(db, id.asString(), id.asString());
            mId = id;
        }

        @Override
        protected boolean dispatch(String cacheId, Progress p, String id)
                throws SQLiteException, IOException, LockerException
        {
            List<Track> tracks = mDb.mLocker.getTracksForPlaylist(id, p.mCount, p.mCurrentSet);
            if (p.mCurrentSet == 0) mDb.deleteOldPlaylistTracks(id);
            return mDb.insertTracksForPlaylist(tracks, id, p);
        }
    }
}
