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

public class Album {
	int mId;
	String mName;
	String mYear;
	int mTrackCount;
	int mSize;
	String mReleaseDate;
	String mPurhaseDate;
	int mHasArt;
	int mArtistId;
	String mArtistName;
	Track[] mTracks;

	private Album() {
	}
	
	public Album( int id, String name, String year, int trackCount, int size, String releaseDate,
            String purhaseDate, int hasArt, int artistId, String artistName )
    {
        super();
        mId = id;
        mName = name;
        mYear = year;
        mTrackCount = trackCount;
        mSize = size;
        mReleaseDate = releaseDate;
        mPurhaseDate = purhaseDate;
        mHasArt = hasArt;
        mArtistId = artistId;
        mArtistName = artistName;
    }

    public int getId() {
		return mId;
	}

	public String getName() {
		return mName;
	}

	public String getYear() {
		return mYear;
	}

	public int getTrackCount() {
		return mTrackCount;
	}

	public int getSize() {
		return mSize;
	}

	public String getReleaseDate() {
		return mReleaseDate;
	}

	public String getPurhaseDate() {
		return mPurhaseDate;
	}

	public int getHasArt() {
		return mHasArt;
	}

	public int getArtistId() {
		return mArtistId;
	}

	public String getArtistName() {
		return mArtistName;
	}

    public static Album albumFromJson(JSONObject obj)
    {
        Album a = new Album();
        try {
            a.mId          = obj.getInt("albumId");
            a.mSize        = obj.getInt("albumSize");
            a.mArtistId    = obj.getInt("artistId");
            a.mTrackCount  = obj.getInt("trackCount");
            a.mHasArt      = obj.getInt("hasArt");
            a.mName        = obj.getString("albumTitle");
            a.mArtistName  = obj.getString("artistName");
            a.mPurhaseDate = obj.getString("purchaseDate");
            a.mReleaseDate = obj.getString("releaseDate");
            return a;
        } catch (JSONException e) {
            return null;
        }
    }

}
