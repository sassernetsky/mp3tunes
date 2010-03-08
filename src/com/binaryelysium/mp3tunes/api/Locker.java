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
import java.io.OutputStream;
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

    public long getLastUpdate(UpdateType type) throws LockerException, LoginException, InvalidSessionException, JSONException
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
    
    public List<Artist> getArtist(int id) throws LockerException, InvalidSessionException, JSONException, LoginException
    {
        return getArtistsList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
        .addParam("type", "artist")
        .addParam("artist_id", Integer.toString(id))
        .create());
    }
    
    public List<Artist> getArtistsFromJson() throws LockerException, InvalidSessionException, JSONException, LoginException
    {
        return getArtistsList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
                .addParam("type", "artist")
                .addParam("count", "200")
                .addParam("set", "0")
                .create());
    }
    
    public List<Artist> getArtistsList(RemoteMethod method) throws LockerException, InvalidSessionException, JSONException, LoginException
    {
        String text;
        try {
            text = HttpClientCaller.getInstance().call(method);
        } catch (IOException e) {
            throw new LockerException("download failed");
        }
        
        JSONObject json = new JSONObject(text);
        int numResults = json.getJSONObject("summary").getInt("totalResults");
        Log.w("Mp3Tunes", "Get artists call got: " + Integer.toString(numResults) + " results");
        JSONArray jsonArtists = json.getJSONArray("artistList");
        ArrayList<Artist> artists = new ArrayList<Artist>();
        for (int i = 0; i < jsonArtists.length(); i++) {
            JSONObject obj = jsonArtists.getJSONObject(i);
            Artist a = Artist.artistFromJson(obj);
            if (a != null)
                artists.add(a);
        }
        return artists;
    }
    
    public List<Album> getAlbum(int id) throws LockerException, InvalidSessionException, JSONException, LoginException
    {
        return getAlbumsList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
        .addParam("type", "album")
        .addParam("album_id", Integer.toString(id))
        .create());
    }
    
    public List<Album> getAlbumsForArtist(int id) throws LockerException, InvalidSessionException, JSONException, LoginException
    {
        return getAlbumsList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
        .addParam("type", "album")
        .addParam("artist_id", Integer.toString(id))
        .create());
    }

    public List<Album> getAlbumsFromJson() throws LockerException, InvalidSessionException, JSONException, LoginException
    {
        return getAlbumsList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
                            .addParam("type", "album")
                            .addParam("count", "200")
                            .addParam("set", "0")
                            .create());
    }
    
    public List<Album> getAlbumsList(RemoteMethod method) throws LockerException, InvalidSessionException, JSONException, LoginException
    {
        String text;
        try {
            text = HttpClientCaller.getInstance().call(method);
        } catch (IOException e) {
            throw new LockerException("download failed");
        }
        
        JSONObject json = new JSONObject(text);
        int numResults = json.getJSONObject("summary").getInt("totalResults");
        Log.w("Mp3Tunes", "Get artists call got: " + Integer.toString(numResults) + " results");
        JSONArray jsonAlbums = json.getJSONArray("albumList");
        ArrayList<Album> albums = new ArrayList<Album>();
        for (int i = 0; i < jsonAlbums.length(); i++) {
            JSONObject obj = jsonAlbums.getJSONObject(i);
            Album a = Album.albumFromJson(obj);
            if (a != null)
                albums.add(a);
        }
        return albums;
    }

    public List<Track> getTrackList(RemoteMethod method) throws LockerException, InvalidSessionException, JSONException, LoginException
    {
        String text;
        try {
            text = HttpClientCaller.getInstance().call(method);
        } catch (IOException e) {
            throw new LockerException("download failed");
        }
        
        JSONObject json = new JSONObject(text);
        int numResults = json.getJSONObject("summary").getInt("totalResults");
        Log.w("Mp3Tunes", "Get Tracks call got: " + Integer.toString(numResults) + " results");
        JSONArray jsonTracks = json.getJSONArray("trackList");
        ArrayList<Track> tracks = new ArrayList<Track>();
        for (int i = 0; i < jsonTracks.length(); i++) {
            JSONObject obj = jsonTracks.getJSONObject(i);
            Track t = ConcreteTrack.trackFromJson(obj);
            if (t != null) 
                tracks.add(t);
            else
                Log.e("Mp3tunes", "Got null track. Now why did that happen");
        }
        return tracks;
    }
    
    public List<Track> getTracksForArtistFromJson(int id) throws LockerException, InvalidSessionException, JSONException, LoginException
    {
        return getTrackList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
                .addParam("type", "track")
                .addParam("artist_id", Integer.toString(id))
                .create());
    }
    
    public List<Track> getTracksForAlbumFromJson(int id) throws LockerException, InvalidSessionException, JSONException, LoginException
    {
        return getTrackList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
                .addParam("type", "track")
                .addParam("album_id", Integer.toString(id))
                .create());
    }
    
    public List<Track> getTracksForPlaylistFromJson(String id) throws LockerException, InvalidSessionException, JSONException, LoginException
    {
        return getTrackList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
                .addParam("type", "track")
                .addParam("playlist_id", id)
                .addParam("count", "75")
                .addParam("set", "0")
                .create());
    }

    public List<Playlist> getPlaylistList(RemoteMethod method) throws LockerException, InvalidSessionException, JSONException, LoginException
    {
        String text;
        try {
            text = HttpClientCaller.getInstance().call(method);
        } catch (IOException e) {
            throw new LockerException("download failed");
        }
        
        JSONObject json = new JSONObject(text);
        int numResults = json.getJSONObject("summary").getInt("totalResults");
        Log.w("Mp3Tunes", "Get Playlists call got: " + Integer.toString(numResults) + " results");
        JSONArray jsonPlaylists = json.getJSONArray("playlistList");
        ArrayList<Playlist> playlists = new ArrayList<Playlist>();
        for (int i = 0; i < jsonPlaylists.length(); i++) {
            JSONObject obj = jsonPlaylists.getJSONObject(i);
            Playlist p = Playlist.playlistFromJson(obj);
            if (p != null) 
                playlists.add(p);
            else
                Log.e("Mp3tunes", "Got null playlist. Now why did that happen");
        }
        return playlists;
    }
    
    public List<Playlist> getPlaylists() throws LockerException, InvalidSessionException, JSONException, LoginException
    {
        return getPlaylistList(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
                .addParam("type", "playlist")
                .create());
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

    public boolean getTrack(String key, CreateStreamCallback callback) throws InvalidSessionException, LockerException, LoginException
    {
        RemoteMethod method = 
            new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_GET)
                                    .addFileKey(key)
                                    .create();
        try {
            return HttpClientCaller.getInstance().callStream(method, callback);
        } catch (IOException e) {
            throw new LockerException("download failed");
        }
    }
}
