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
    private int      mId;
    private String   mTitle;
    int              mNumber;
    protected double mDuration;
    protected String mFileName;
    protected String mFileKey;
    protected int    mFileSize;
    String           mDownloadUrl;
    String           mPlayUrl;

    int mAlbumId;
    String mAlbumTitle;
    protected String mAlbumYear; // stored as a string cause we hardly use it as an int

    int mArtistId;
    String mArtistName;

    String mAlbumArt;

    private ConcreteTrack()
    {
    }
    
    public ConcreteTrack(Track t)
    {
        mId          = t.getId();
        mPlayUrl     = t.getPlayUrl();
        mDownloadUrl = t.getDownloadUrl();
        mTitle       = t.getTitle();
        mNumber      = t.getNumber();
        mArtistId    = t.getArtistId();
        mArtistName  = t.getArtistName();
        mAlbumId     = t.getAlbumId();
        mAlbumTitle  = t.getAlbumTitle();
        mAlbumArt    = t.getAlbumArt();
        mDuration    = t.getDuration();
        mFileName    = t.getFileName();
        mFileKey     = t.getFileKey();
        mFileSize    = t.getFileSize();
        mAlbumYear   = t.getAlbumYear();
    }

    public ConcreteTrack(int id, String play_url, String download_url, String title,
            int track, int artist_id, String artist_name, int album_id,
            String album_name, String cover_url)
    {
        mId = id;
        mPlayUrl = play_url;
        mDownloadUrl = download_url;
        mTitle = title;
        mNumber = track;
        mArtistId = artist_id;
        mArtistName = artist_name;
        mAlbumId = album_id;
        mAlbumTitle = album_name;
        mAlbumArt = cover_url;
    }
    
    public ConcreteTrack(int id, String playUrl, String downloadUrl, String title, int number,
                 int artistId, String artistName, int albumId, String albumTitle, 
                 String albumArt, double duration, String fileName, String fileKey,
                 int fileSize, String albumYear)
    {
        mId          = id;
        mPlayUrl     = playUrl;
        mDownloadUrl = downloadUrl;
        mTitle       = title;
        mNumber      = number;
        mArtistId    = artistId;
        mArtistName  = artistName;
        mAlbumId     = albumId;
        mAlbumTitle  = albumTitle;
        mAlbumArt    = albumArt;
        mDuration    = duration;
        mFileName    = fileName;
        mFileKey     = fileKey;
        mFileSize    = fileSize;
        mAlbumYear   = albumYear;
    }
    
    /* (non-Javadoc)
     * @see com.binaryelysium.mp3tunes.api.TrackInterface#toString()
     */
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

    /* (non-Javadoc)
     * @see com.binaryelysium.mp3tunes.api.TrackInterface#getId()
     */
    public int getId()
    {
        return mId;
    }

    /* (non-Javadoc)
     * @see com.binaryelysium.mp3tunes.api.TrackInterface#getTitle()
     */
    public String getTitle()
    {
        return mTitle;
    }

    /* (non-Javadoc)
     * @see com.binaryelysium.mp3tunes.api.TrackInterface#getNumber()
     */
    public int getNumber()
    {
        return mNumber;
    }

    /* (non-Javadoc)
     * @see com.binaryelysium.mp3tunes.api.TrackInterface#getDuration()
     */
    public Double getDuration()
    {
        return mDuration;
    }

    /* (non-Javadoc)
     * @see com.binaryelysium.mp3tunes.api.TrackInterface#getFileName()
     */
    public String getFileName()
    {
        return mFileName;
    }

    /* (non-Javadoc)
     * @see com.binaryelysium.mp3tunes.api.TrackInterface#getFileKey()
     */
    public String getFileKey()
    {
        if (mFileKey == null) {
            if (mPlayUrl != null) {
                mFileKey = mPlayUrl.replaceFirst("^.*lockerplay/", "").replaceFirst("\\?.*", "");
            } else if (mDownloadUrl != null) {
                mFileKey = mDownloadUrl.replaceFirst("^.*lockerget/", "").replaceFirst("\\?.*", "");
            }
        }
        return mFileKey;
    }

    /* (non-Javadoc)
     * @see com.binaryelysium.mp3tunes.api.TrackInterface#getFileSize()
     */
    public int getFileSize()
    {
        return mFileSize;
    }

    /* (non-Javadoc)
     * @see com.binaryelysium.mp3tunes.api.TrackInterface#getDownloadUrl()
     */
    public String getDownloadUrl()
    {
        return mDownloadUrl;
    }

    /* (non-Javadoc)
     * @see com.binaryelysium.mp3tunes.api.TrackInterface#getPlayUrl()
     */
    public String getPlayUrl()
    {
        return mPlayUrl;
    }

    /* (non-Javadoc)
     * @see com.binaryelysium.mp3tunes.api.TrackInterface#getAlbumId()
     */
    public int getAlbumId()
    {
        return mAlbumId;
    }

    /* (non-Javadoc)
     * @see com.binaryelysium.mp3tunes.api.TrackInterface#getAlbumTitle()
     */
    public String getAlbumTitle()
    {
        return mAlbumTitle;
    }

    /* (non-Javadoc)
     * @see com.binaryelysium.mp3tunes.api.TrackInterface#getAlbumYear()
     */
    public String getAlbumYear()
    {
        return mAlbumYear;
    }

    /* (non-Javadoc)
     * @see com.binaryelysium.mp3tunes.api.TrackInterface#getArtistId()
     */
    public int getArtistId()
    {
        return mArtistId;
    }

    /* (non-Javadoc)
     * @see com.binaryelysium.mp3tunes.api.TrackInterface#getArtistName()
     */
    public String getArtistName()
    {
        return mArtistName;
    }

    /* (non-Javadoc)
     * @see com.binaryelysium.mp3tunes.api.TrackInterface#getAlbumArt()
     */
    public String getAlbumArt()
    {
        return mAlbumArt;
    }

    /* (non-Javadoc)
     * @see com.binaryelysium.mp3tunes.api.TrackInterface#sameRemoteFile(com.binaryelysium.mp3tunes.api.Track)
     */
    public boolean sameRemoteFile(Track t)
    {
        String myFileKey = null, otherFileKey = null;
        
        if (mFileKey == null || mFileKey.equals("")) {
            if (mPlayUrl == null || mPlayUrl.equals("")) {
                return false;
            }
            myFileKey = mPlayUrl.replaceFirst("^.*lockerPlay/", "").replaceFirst("\\?.*", "");
        } else {
            myFileKey = mFileKey;
        }
        
        if (t.getFileKey() == null || t.getFileKey().equals("")) {
            if (t.getPlayUrl() == null || t.getPlayUrl().equals("")) {
                return false;
            }
            otherFileKey = mPlayUrl.replaceFirst("^.*lockerPlay/", "").replaceFirst("\\?.*", "");
            
        } else {
            otherFileKey = mFileKey;
        }
        
        return myFileKey.equals(otherFileKey);
    }

    public static Track trackFromJson(JSONObject obj)
    {
        ConcreteTrack t = new ConcreteTrack();
        try {
            t.mId          = obj.getInt("trackId");
            t.mFileSize    = obj.getInt("trackFileSize");
            t.mNumber      = obj.getInt("trackNumber");
            t.mAlbumId     = obj.getInt("albumId");
            t.mArtistId    = obj.getInt("artistId");
            t.mTitle       = obj.getString("trackTitle");
            t.mFileName    = obj.getString("trackFileName");
            t.mFileKey     = obj.getString("trackFileKey");
            t.mAlbumTitle  = obj.getString("albumTitle");
            t.mAlbumYear   = obj.getString("albumYear");
            t.mArtistName  = obj.getString("artistName");
            t.mDownloadUrl = obj.getString("downloadURL");
            t.mPlayUrl     = obj.getString("playURL");
            t.mDuration    = obj.getDouble("trackLength");

            return t;
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e("Mp3Tunes", obj.toString());
            return null;
        }
    }
}
