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

	public static Album albumFromResult(RestResult restResult) {
		try {
			Album a = new Album();
			int event = restResult.getParser().nextTag();
			boolean loop = true;
			while (loop) {
				String name = restResult.getParser().getName();
				switch (event) {
				case XmlPullParser.START_TAG:
					if (name.equals("albumId")) {
						a.mId = Integer.parseInt(restResult.getParser().nextText());
					} else if (name.equals("albumSize")) {
						a.mSize = Integer.parseInt(restResult.getParser()
								.nextText());
					} else if (name.equals("albumTitle")) {
						a.mName = restResult.getParser().nextText();
					} else if (name.equals("artistId")) {
						a.mArtistId = Integer.parseInt(restResult.getParser()
								.nextText());
					} else if (name.equals("trackCount")) {
						a.mTrackCount = Integer.parseInt(restResult.getParser()
								.nextText());
					} else if (name.equals("artistName")) {
						a.mArtistName = restResult.getParser().nextText();
					} else if (name.equals("hasArt")) {
						a.mHasArt = Integer.parseInt(restResult.getParser()
								.nextText());
					} else if (name.equals("purchaseDate")) {
						a.mPurhaseDate = restResult.getParser().nextText();
					} else if (name.equals("releaseDate")) {
						a.mReleaseDate = restResult.getParser().nextText();
					}
					break;
				case XmlPullParser.END_TAG:
					if (name.equals("item"))
						loop = false;
					break;
				}
				event = restResult.getParser().next();
			}
			return a;
		} catch (Exception e) {
		}
		return null;
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
