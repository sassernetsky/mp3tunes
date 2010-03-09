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

import java.util.HashMap;
import java.util.Map;

import com.binaryelysium.mp3tunes.api.Playlist;
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
            db.updateCache(entry.getKey(), item.mUpdate, item.mState, item.mProgress);
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
}
