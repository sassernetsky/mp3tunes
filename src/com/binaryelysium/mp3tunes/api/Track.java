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
import org.xmlpull.v1.XmlPullParser;

import android.os.Debug;

public class Track
{

    public static int trackFromResultCalls = 0;
    public static int trackAllocs = 0;
    public static int setDataAllocs = 0;
    public static int trackAllocsSize = 0;
    public static int setDataAllocsSize = 0;
    public static int nextAllocs = 0;
    public static int nextAllocsSize = 0;
    public static int nextTextAllocs = 0;
    public static int nextTextAllocsSize = 0;
    public static int getNameAllocs = 0;
    public static int getNameAllocsSize = 0;

    private int mId;
    private String mTitle;
    int mNumber;
    protected double mDuration;
    protected String mFileName;
    protected String mFileKey;
    protected int mFileSize;
    String mDownloadUrl;
    String mPlayUrl;

    int mAlbumId;
    String mAlbumTitle;
    protected String mAlbumYear; // stored as a string cause we hardly use it as an int

    int mArtistId;
    String mArtistName;

    String mAlbumArt;

    private Track()
    {
    }
    
    public Track(Track t)
    {
        mId          = t.mId;
        mPlayUrl     = t.mPlayUrl;
        mDownloadUrl = t.mDownloadUrl;
        mTitle       = t.mTitle;
        mNumber      = t.mNumber;
        mArtistId    = t.mArtistId;
        mArtistName  = t.mArtistName;
        mAlbumId     = t.mAlbumId;
        mAlbumTitle  = t.mAlbumTitle;
        mAlbumArt    = t.mAlbumArt;
        mDuration    = t.mDuration;
        mFileName    = t.mFileName;
        mFileKey     = t.mFileKey;
        mFileSize    = t.mFileSize;
        mAlbumYear   = t.mAlbumYear;
    }

    public Track(int id, String play_url, String download_url, String title,
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
    
    public Track(int id, String playUrl, String downloadUrl, String title, int number,
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

    public static Track trackFromResult(RestResult restResult,
            String partner_token)
    {
        trackFromResultCalls++;
        try {
            Debug.startAllocCounting();
            Track t = new Track();
            Debug.stopAllocCounting();
            trackAllocs += Debug.getThreadAllocCount();
            trackAllocsSize += Debug.getThreadAllocSize();

            int event = restResult.getParser().nextTag();
            boolean loop = true;
            while (loop) {
                Debug.startAllocCounting();
                String name = restResult.getParser().getName();
                Debug.stopAllocCounting();
                getNameAllocs     += Debug.getThreadAllocCount();
                getNameAllocsSize += Debug.getThreadAllocSize();
                switch (event) {
                    case XmlPullParser.START_TAG:
                        
                        Debug.startAllocCounting();
                        String text = restResult.getParser().nextText();
                        Debug.stopAllocCounting();
                        nextTextAllocs     += Debug.getThreadAllocCount();
                        nextTextAllocsSize += Debug.getThreadAllocSize();
                        
                        Debug.startAllocCounting();
                        if (name.equals("trackId")) {
                            t.mId = Integer.parseInt(text);
                        } else if (name.equals("trackFileSize")) {
                            t.mFileSize = Integer.parseInt(text);
                        } else if (name.equals("trackTitle")) {
                            t.mTitle = text;
                        } else if (name.equals("trackFileName")) {
                            t.mFileName = text;
                        } else if (name.equals("trackFileKey")) {
                            t.mFileKey = text;
                        } else if (name.equals("trackNumber")) {
                            t.mNumber = Integer.parseInt(text);
                        } else if (name.equals("trackLength")) {
                            t.mDuration = Double.parseDouble(text);
                        } else if (name.equals("albumTitle")) {
                            t.mAlbumTitle = text;
                        } else if (name.equals("albumYear")) {
                            t.mAlbumYear = text;
                        } else if (name.equals("albumId")) {
                            t.mAlbumId = Integer.parseInt(text);
                        } else if (name.equals("artistId")) {
                            t.mArtistId = Integer.parseInt(text);
                        } else if (name.equals("artistName")) {
                            t.mArtistName = text;
                        } else if (name.equals("albumArtURL")) {
                            t.mAlbumArt = text;
                        } else if (name.equals("downloadURL")) {
                            t.mDownloadUrl = text;
                            t.mDownloadUrl += partner_token;
                        } else if (name.equals("playURL")) {
                            t.mPlayUrl = text;
                            t.mPlayUrl += partner_token;
                        }
                        Debug.stopAllocCounting();
                        setDataAllocs     += Debug.getThreadAllocCount();
                        setDataAllocsSize += Debug.getThreadAllocSize();
                        break;
                    case XmlPullParser.END_TAG:
                        if (name.equals("item"))
                            loop = false;
                        break;
                }
                Debug.startAllocCounting();
                event = restResult.getParser().next();
                Debug.stopAllocCounting();
                nextAllocs     += Debug.getThreadAllocCount();
                nextAllocsSize += Debug.getThreadAllocSize();
            }
            return t;
        } catch (Exception e) {
            Debug.stopAllocCounting();
        }
        return null;
    }

    public int getId()
    {
        return mId;
    }

    public String getTitle()
    {
        return mTitle;
    }

    public int getNumber()
    {
        return mNumber;
    }

    public Double getDuration()
    {
        return mDuration;
    }

    public String getFileName()
    {
        return mFileName;
    }

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

    public int getFileSize()
    {
        return mFileSize;
    }

    public String getDownloadUrl()
    {
        return mDownloadUrl;
    }

    public String getPlayUrl()
    {
        return mPlayUrl;
    }

    public int getAlbumId()
    {
        return mAlbumId;
    }

    public String getAlbumTitle()
    {
        return mAlbumTitle;
    }

    public String getAlbumYear()
    {
        return mAlbumYear;
    }

    public int getArtistId()
    {
        return mArtistId;
    }

    public String getArtistName()
    {
        return mArtistName;
    }

    public String getAlbumArt()
    {
        return mAlbumArt;
    }

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
        Track t = new Track();
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
            //t.mAlbumArt    = obj.getString("albumArtURL");
            t.mDownloadUrl = obj.getString("downloadURL");
            t.mPlayUrl     = obj.getString("playURL");
            t.mDuration    = obj.getDouble("trackLength");

            return t;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
}
