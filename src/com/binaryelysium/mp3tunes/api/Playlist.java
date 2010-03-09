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

import java.util.Collection;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

public class Playlist implements LockerData
{
    Id     mId;
	String mName;
	String mFileName;
	int mCount;
	String mDateModified;
	int mSize;
	List<Track> mTracks;
	
	
	public final static String RANDOM_TRACKS = "RANDOM_TRACKS";
	
    public final static String NEWEST_TRACKS = "NEWEST_TRACKS";
    
    public final static String RECENTLY_PLAYED = "RECENTLY_PLAYED";
    
    public final static String INBOX = "INBOX";


	private Playlist() {}
	
	public Playlist(Id id, String name, String fileName, int count, String dateModified, int size )
    {
        mId = id;
        mName = name;
        mFileName = fileName;
        mCount = count;
        mDateModified = dateModified;
        mSize = size;
    }

	
	public static Playlist randomTracks()
	{
		return new Playlist();
	}
	
	public static Playlist newestTracks()
	{
		return new Playlist();
	}
	
	public static Playlist recentlyPlayed()
	{
		return new Playlist();
	}
	
	public Id getId() {
		return mId;
	}

	public String getName() {
		return mName;
	}

	public String getFileName() {
		return mFileName;
	}

	public int getCount() {
		return mCount;
	}

	public String getDateModified() {
		return mDateModified;
	}

	public int getSize() {
		return mSize;
	}

	public Collection<Track> getTracks() {
		return mTracks;
	}

    public static Playlist playlistFromJson(JSONObject obj)
    {
        Playlist p = new Playlist();
        try {
            p.mId       = new LockerId(obj.getString("playlistId"));
            p.mName     = obj.getString("playlistTitle");
            p.mFileName = obj.getString("fileName");
            p.mCount    = obj.getInt("fileCount");
        } catch (JSONException e) {
        e.printStackTrace();
        return null;
        }
        return p;
    }

    public static boolean isDynamicPlaylist(String playlistId)
    {
        return (playlistId.equals(RANDOM_TRACKS)   ||
                playlistId.equals(NEWEST_TRACKS)   ||
                playlistId.equals(RECENTLY_PLAYED) ||
                playlistId.equals(INBOX)            ||
                playlistId.startsWith("BYO_"));
    }

}
