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

public class Artist {
	int mId;
	String mName;
	int mTrackCount;
	int mAlbumCount;
	int mSize;
	Album[] mAlbums;

	public int getId() {
		return mId;
	}

	public String getName() {
		return mName;
	}

	public int getTrackCount() {
		return mTrackCount;
	}

	public int getAlbumCount() {
		return mAlbumCount;
	}

	public int getSize() {
		return mSize;
	}

	private Artist() {
	}
	
    public static Artist artistFromJson(JSONObject obj)
    {
        Artist a = new Artist();
        try {
            a.mId         = obj.getInt("artistId");
            a.mSize       = obj.getInt("artistSize");
            a.mName       = obj.getString("artistName");
            a.mAlbumCount = obj.getInt("albumCount");
            a.mTrackCount = obj.getInt("trackCount");
            return a;
        } catch (JSONException e) {
            return null;
        }
    }

}
