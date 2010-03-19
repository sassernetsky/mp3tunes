/***************************************************************************
 *   Copyright 2008 Casey Link <unnamedrambler@gmail.com>                  *
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

package com.binaryelysium.mp3tunes.api;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class ConcreteTrack implements Track
{
    private Id       mId;
    private String   mTitle;
    protected String mFileKey;
    String           mPlayUrl;
    int mAlbumId;
    String mAlbumTitle;

    int mArtistId;
    String mArtistName;

    private ConcreteTrack()
    {
    }
    
    public ConcreteTrack(Track t)
    {
        mId          = t.getId();
        mPlayUrl     = t.getPlayUrl(0);
        mTitle       = t.getTitle();
        mArtistId    = t.getArtistId();
        mArtistName  = t.getArtistName();
        mAlbumId     = t.getAlbumId();
        mAlbumTitle  = t.getAlbumTitle();
    }

    public ConcreteTrack(Id id, String title, String url)
    {
        mId      = id;
        mTitle   = title;
        mPlayUrl = url;
                
    }
    
    public ConcreteTrack(Id id, String play_url, String title, int artist_id, String artist_name, int album_id, String album_name)
    {
        mId = id;
        mPlayUrl = play_url;
        mTitle = title;
        mArtistId = artist_id;
        mArtistName = artist_name;
        mAlbumId = album_id;
        mAlbumTitle = album_name;
    }
    
    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("Title: ");
        builder.append(mTitle);
        builder.append(" by: ");
        builder.append(mArtistName);
        return builder.toString();
    }

    public Id getId()
    {
        return mId;
    }

    public String getTitle()
    {
        return mTitle;
    }
    
    public String getName()
    {
        return mTitle;
    }

    public String getFileKey()
    {
        if (mFileKey == null) {
            if (mPlayUrl != null) {
                mFileKey = mPlayUrl.replaceFirst("^.*lockerPlay/", "").replaceFirst("\\?.*", "");
            }
        }
        return mFileKey;
    }

    public String getPlayUrl(int requestedBitrate)
    {
        if (LockerId.class.isInstance(mId)) {
            if (mFileKey == null) getFileKey();
            RemoteMethod method;
            try {
                method = new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_PLAY)
                    .addFileKey(mFileKey)
                    .addParam("fileformat", "mp3")
                    .addParam("bitrate", Integer.toString(requestedBitrate))
                    .create();
            } catch (InvalidSessionException e) {
                e.printStackTrace();
                return null;
            }
            return method.getCall();
        } else {
            return mPlayUrl;
        }
    }

    public int getAlbumId()
    {
        return mAlbumId;
    }

    public String getAlbumTitle()
    {
        return mAlbumTitle;
    }

    public int getArtistId()
    {
        return mArtistId;
    }

    public String getArtistName()
    {
        return mArtistName;
    }

    public static Track trackFromJson(JSONObject obj)
    {
        ConcreteTrack t = new ConcreteTrack();
        try {
            t.mId          = new LockerId(obj.getInt("trackId"));
            try {
                t.mAlbumId     = obj.getInt("albumId");
            } catch (JSONException e) {}
            try {
                t.mArtistId    = obj.getInt("artistId");
            } catch (JSONException e) {}
            t.mTitle       = obj.getString("trackTitle");
            t.mFileKey     = obj.getString("trackFileKey");
            t.mAlbumTitle  = obj.getString("albumTitle");
            t.mArtistName  = obj.getString("artistName");
            t.mPlayUrl     = obj.getString("playURL");

            return t;
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e("Mp3Tunes", obj.toString());
            return null;
        }
    }

    private boolean compare(String first, String second)
    {
        if (first != null && second != null)
            return first.equals(second);
        return first == null && second == null;
    }
    
    public boolean sameMainMetaData(Track t)
    {
        if (t == null) return false;
        
        return (compare(t.getTitle(), mTitle) && compare(t.getAlbumTitle(), mAlbumTitle) && compare(t.getArtistName(), mArtistName));
    }
}
