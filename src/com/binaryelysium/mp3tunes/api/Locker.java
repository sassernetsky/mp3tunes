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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.client.HttpResponseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.binaryelysium.mp3tunes.api.HttpClientCaller.CreateStreamCallback;
import com.binaryelysium.mp3tunes.api.Session.LoginException;
import com.binaryelysium.mp3tunes.api.results.SearchResult;

public class Locker
{
    public enum UpdateType {
        locker, playlist, preferences
    };

    public Locker()
    {}
    
    public Locker(String username, String password)
            throws LockerException, LoginException
    {
        refreshSession(username, password);
    }
    
    public boolean testSession()
    {
        String text;
        try {
            text = HttpClientCaller.getInstance().callNoFixSession(new RemoteMethod.Builder(RemoteMethod.METHODS.LAST_UPDATE)
            .addParam("type", UpdateType.locker.toString())
            .create());
        } catch (HttpResponseException e) {
            if (e.getStatusCode() == 401) {
                return false;
            }
            e.printStackTrace();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return true;
        } catch (InvalidSessionException e) {
            e.printStackTrace();
            return false;
        } catch (LockerException e) {
            e.printStackTrace();
            return false;
        } catch (LoginException e) {
            e.printStackTrace();
            return false;
        }

        try {
            JSONObject json = new JSONObject(text);
            if (json.getInt("status") == 1)
                return true;
            else {
                int error = json.getInt("errorCode");
                if (error == 401001) return false;
                String errorStr = Integer.toString(error);
                Log.e("Mp3Tunes", "Got error " + errorStr + " testing for valid session");
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return true;
        }

        return false;
    }
    
    public void refreshSession(String username, String password) throws LockerException, LoginException
    {
        Log.w("Mp3Tunes", "Called refresh session");
        
        try {
            HttpClientCaller caller = HttpClientCaller.getInstance();
            String response = 
                caller.call(new RemoteMethod.Builder(RemoteMethod.METHODS.LOGIN)
                            .addParam("username", username)
                            .addParam("password", password)
                            .create());
            try {
                LockerContext.instance().setSession(Session.sessionFromJson(response));
            } catch (JSONException e) {
                throw new LockerException("Server returned corrupt data");
            } 
        } catch (IllegalArgumentException e) {
            throw new LoginException();
        } catch (HttpResponseException e) {
            Log.w("Mp3Tunes", "Error code: " + Integer.toString(e.getStatusCode()) + "\n" + Log.getStackTraceString(e));
            throw new LockerException("connection issue");
        } catch (IOException e) {
            Log.w("Mp3Tunes", Log.getStackTraceString(e));
            throw new LockerException("connection issue");
        } catch (InvalidSessionException e) {
            throw new LoginException();
        }
    }

    public long getLastUpdate(UpdateType type) throws LockerException, InvalidSessionException, LoginException, JSONException
    {
        String text;
        try {
            text = HttpClientCaller.getInstance().call(new RemoteMethod.Builder(RemoteMethod.METHODS.LAST_UPDATE)
            .addParam("type", type.toString())
            .create());
        } catch (IOException e) {
            throw new LockerException("download failed");
        }

        JSONObject json = new JSONObject(text);
        if (json.getInt("status") == 1)
            return json.getLong("timestamp");

        return 0;
    }
    
    public List<Artist> getArtist(int id) throws LockerException
    {
        try {
            return getArtistsList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
                .addParam("type", "artist")
                .addParam("artist_id", Integer.toString(id))
                .create());
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        }
    }
    
    public List<Artist> getArtists(int count, int set) throws LockerException 
    {
        try {
            return getArtistsList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
                .addParam("type",  "artist")
                .addParam("count", Integer.toString(count))
                .addParam("set",   Integer.toString(set))
                .create());
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        }
    }
    
    public List<Artist> getArtistsList(RemoteMethod method) throws LockerException
    {
        try {
            String text;
            try {
                text = HttpClientCaller.getInstance().call(method);
            } catch (IOException e) {
                throw new LockerException("download failed");
            }
        
            JSONObject json = new JSONObject(text);
            int numResults = json.getJSONObject("summary").getInt("totalResults");
            Log.w("Mp3Tunes", "Get artists call got: " + Integer.toString(numResults) + " results");
            ArrayList<Artist> artists = new ArrayList<Artist>();
            if (numResults == 0) return artists;
        
            JSONArray jsonArtists = json.optJSONArray("artistList");
            if (jsonArtists == null) return artists;
            for (int i = 0; i < jsonArtists.length(); i++) {
                JSONObject obj = jsonArtists.getJSONObject(i);
                Artist a = Artist.artistFromJson(obj);
                if (a != null)
                    artists.add(a);
            }
            if (artists.size() < 1) throw new LockerException("Sever Sent Corrupt Data");
            return artists;
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        } catch (JSONException e) {
            e.printStackTrace();
            throw new LockerException("Sever Sent Corrupt Data");
        } catch (LoginException e) {
            throw new LockerException("Unable to refresh session");
        }
    }
    
    public List<Album> getAlbum(int id) throws LockerException
    {
        try {
            return getAlbumsList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
                .addParam("type", "album")
                .addParam("album_id", Integer.toString(id))
                .create());
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        }
    }
    
    public List<Album> getAlbumsForArtist(int id) throws LockerException
    {
        try {
            return getAlbumsList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
                .addParam("type", "album")
                .addParam("artist_id", Integer.toString(id))
                .create());
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        }
    }

    public List<Album> getAlbums(int count, int set) throws LockerException
    {
        try {
            return getAlbumsList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
                            .addParam("type",  "album")
                            .addParam("count", Integer.toString(count))
                            .addParam("set",   Integer.toString(set))
                            .create());
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        }
            
    }
    
    public List<Album> getAlbumsList(RemoteMethod method) throws LockerException
    {
        try {
            String text;
            try {
                text = HttpClientCaller.getInstance().call(method);
            } catch (IOException e) {
                throw new LockerException("download failed");
            }

            JSONObject json = new JSONObject(text);
            int numResults = json.getJSONObject("summary").getInt("totalResults");
            Log.w("Mp3Tunes", "Get artists call got: " + Integer.toString(numResults) + " results");
            ArrayList<Album> albums = new ArrayList<Album>();
            if (numResults == 0) return albums;
        
            JSONArray jsonAlbums = json.optJSONArray("albumList");
            if (jsonAlbums == null) return albums;
            for (int i = 0; i < jsonAlbums.length(); i++) {
                JSONObject obj = jsonAlbums.getJSONObject(i);
                Album a = Album.albumFromJson(obj);
                if (a != null)
                    albums.add(a);
            }
        
            if (albums.size() < 1) throw new LockerException("Sever Sent Corrupt Data");
            return albums;
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        } catch (JSONException e) {
            e.printStackTrace();
            throw new LockerException("Sever Sent Corrupt Data");
        } catch (LoginException e) {
            throw new LockerException("Unable to refresh session");
        }
    }

    public List<Track> getTrackList(RemoteMethod method) throws LockerException
    {
        try {
            String text;
            try {
                text = HttpClientCaller.getInstance().call(method);
            } catch (IOException e) {
                throw new LockerException("download failed");
            }
        
            //We get the extra data here because the sets for generated playlists do not behave
            //the same way as no
            JSONObject json  = new JSONObject(text);
            JSONObject obj   = json.getJSONObject("summary");
            int numResults   = obj.optInt("totalResults");
            int set          = obj.optInt("set");
            //int count        = obj.optInt("count");
            double totalSets = obj.optDouble("totalResultSets");
        
            Log.w("Mp3Tunes", "Get Tracks call got: " + Integer.toString(numResults) + " results");
            ArrayList<Track> tracks = new ArrayList<Track>();
            if (numResults == 0 || set > totalSets) return tracks;
        
            JSONArray jsonTracks = json.optJSONArray("trackList");
            if (jsonTracks == null) return tracks;
            for (int i = 0; i < jsonTracks.length(); i++) {
                JSONObject track = jsonTracks.getJSONObject(i);
                Track t = ConcreteTrack.trackFromJson(track);
                if (t != null) 
                    tracks.add(t);
                else
                    Log.e("Mp3tunes", "Got null track. Now why did that happen");
            }
            if (tracks.size() < 1) throw new LockerException("Sever Sent Corrupt Data");
            return tracks;
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        } catch (JSONException e) {
            e.printStackTrace();
            throw new LockerException("Sever Sent Corrupt Data");
        } catch (LoginException e) {
            throw new LockerException("Unable to refresh session");
        }
    }
    
    public List<Track> getTracks(int count, int set) throws LockerException
    {
            try {
                return getTrackList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
                    .addParam("type", "track")
                    .addParam("count", Integer.toString(count))
                    .addParam("set",   Integer.toString(set))
                    .create());
            } catch (InvalidSessionException e) {
                throw new LockerException("Bad Session Data");
            }
    }
    
    public List<Track> getTracksForArtistFromJson(int id) throws LockerException
    {
        try {
            return getTrackList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
                .addParam("type", "track")
                .addParam("artist_id", Integer.toString(id))
                .create());
    } catch (InvalidSessionException e) {
        throw new LockerException("Bad Session Data");
    }
    }
    
    public List<Track> getTracksForAlbumFromJson(int id) throws LockerException
    {
        try {
            return getTrackList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
                .addParam("type", "track")
                .addParam("album_id", Integer.toString(id))
                .create());
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        }
    }
    
    public List<Track> getTracksForPlaylist(String id, int count, int set) throws LockerException
    {
        try {
            return getTrackList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
                .addParam("type", "track")
                .addParam("playlist_id", id)
                .addParam("count", Integer.toString(count))
                .addParam("set", Integer.toString(set))
                .create());
        } catch (InvalidSessionException e) {
                    throw new LockerException("Bad Session Data");
                }
    }

    public List<Playlist> getPlaylistList(RemoteMethod method) throws LockerException
    {
        try {
            String text;
            try {
                text = HttpClientCaller.getInstance().call(method);
            } catch (IOException e) {
                throw new LockerException("download failed");
            }
        
            JSONObject json = new JSONObject(text);
            int numResults = json.getJSONObject("summary").getInt("totalResults");
            Log.w("Mp3Tunes", "Get Playlists call got: " + Integer.toString(numResults) + " results");
            ArrayList<Playlist> playlists = new ArrayList<Playlist>();
            if (numResults == 0) return playlists;
        
            JSONArray jsonPlaylists = json.optJSONArray("playlistList");
            if (jsonPlaylists == null) return playlists;
            for (int i = 0; i < jsonPlaylists.length(); i++) {
                JSONObject obj = jsonPlaylists.getJSONObject(i);
                Playlist p = Playlist.playlistFromJson(obj);
                if (p != null) 
                    playlists.add(p);
                else
                    Log.e("Mp3tunes", "Got null playlist. Now why did that happen");
            }
        
            if (playlists.size() < 1) throw new LockerException("Sever Sent Corrupt Data");
            return playlists;
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        } catch (JSONException e) {
            e.printStackTrace();
            throw new LockerException("Sever Sent Corrupt Data");
        } catch (LoginException e) {
            throw new LockerException("Unable to refresh session");
        }
    }
    
    public List<Playlist> getPlaylists(int count, int set) throws LockerException
    {
        try {
            return getPlaylistList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
                .addParam("type", "playlist")
                .addParam("count", Integer.toString(count))
                .addParam("set",   Integer.toString(set))
                .create());
        } catch (InvalidSessionException e) {
            throw new LockerException("Bad Session Data");
        }
    }
    
    public SearchResult search(String query) throws LockerException, InvalidSessionException, JSONException, LoginException
    {
        return search(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_SEARCH)
        .addParam("type", "artist,album,track")
        .addParam("s", query)
        .addParam("count", "200")
        .addParam("set", "0")
        .create());
    }

    public SearchResult search(RemoteMethod method) throws LockerException, InvalidSessionException, JSONException, LoginException
    {
        String text;
        try {
            text = HttpClientCaller.getInstance().call(method);
        } catch (IOException e) {
            throw new LockerException("download failed");
        }
        
        JSONObject json = new JSONObject(text);
        JSONObject results = json.getJSONObject("summary").getJSONObject("totalResults");
        
        int numArtists = results.getInt("artist");
        int numAlbums  = results.getInt("album");
        int numTracks  = results.getInt("track");
        
        Log.w("Mp3Tunes", "Get search call got: " + Integer.toString(numArtists) + " artists: " +
              Integer.toString(numAlbums) + " albums: " + Integer.toString(numTracks) + " tracks");
        
        SearchResult result = new SearchResult();
        if (numArtists > 0) {
            ArrayList<Artist> artists = new ArrayList<Artist>();
            JSONArray jsonArray = json.getJSONArray("artistList");
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                Artist a = Artist.artistFromJson(obj);
                if (a != null) 
                    artists.add(a);
                else
                    Log.e("Mp3tunes", "Got null artist. Now why did that happen");
            }
            result.setArtists(artists);
        }
        
        if (numAlbums > 0) {
            ArrayList<Album> albums = new ArrayList<Album>();
            JSONArray jsonArray = json.getJSONArray("albumList");
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                Album a = Album.albumFromJson(obj);
                if (a != null) 
                    albums.add(a);
                else
                    Log.e("Mp3tunes", "Got null artist. Now why did that happen");
            }
            result.setAlbums(albums);
        }
        
        if (numTracks > 0) {
            ArrayList<Track> tracks = new ArrayList<Track>();
            JSONArray jsonArray = json.getJSONArray("trackList");
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                Track t = ConcreteTrack.trackFromJson(obj);
                if (t != null) 
                    tracks.add(t);
                else
                    Log.e("Mp3tunes", "Got null artist. Now why did that happen");
            }
            result.setTracks(tracks);
        }
        return result;
    }

    public Bitmap getAlbumArtFromFileKey(String key) throws InvalidSessionException, LockerException, LoginException
    {
        RemoteMethod method = 
            new RemoteMethod.Builder(RemoteMethod.METHODS.ALBUM_ART_GET)
                                     .addFileKey(key)
                                     .create();     
        try {
            byte[] data = HttpClientCaller.getInstance().callBytes(method);
            Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length); 
            return bm;
        } catch (IOException e) {
            throw new LockerException("download failed");
        }
    }

    public boolean getTrack(String key, CreateStreamCallback callback, HttpClientCaller.Progress progressCallback) throws InvalidSessionException, LockerException, LoginException
    {
        RemoteMethod method = 
            new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_GET)
                                    .addFileKey(key)
                                    .create();
        try {
            return HttpClientCaller.getInstance().callStream(method, callback, progressCallback);
        } catch (IOException e) {
            throw new LockerException("download failed");
        }
    }
}
