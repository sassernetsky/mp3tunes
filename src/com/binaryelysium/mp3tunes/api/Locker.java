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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.HttpResponseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import android.os.Debug;
import android.util.Log;

import com.binaryelysium.mp3tunes.api.Authenticator.LoginException;
import com.binaryelysium.mp3tunes.api.results.DataResult;
import com.binaryelysium.mp3tunes.api.results.SearchResult;
import com.binaryelysium.mp3tunes.api.results.SetResult;
import com.mp3tunes.android.player.service.Logger;

public class Locker
{
    private SetQuery mDefaultQuery = new SetQuery(100, 0);
    Session          mSession;
    static String    mPartnerToken;

    public enum UpdateType {
        locker, playlist, preferences
    };

    public Locker()
    {}
    
    public Locker(String partnerToken, Session session) throws LockerException
    {
        //FIXME:
        if (mSession == null)
            throw (new LockerException("Invalid Session"));
        mSession = session;
        mPartnerToken = partnerToken;
    }

    public Locker(String partnerToken, String username, String password)
            throws LockerException, LoginException
    {
        mPartnerToken = partnerToken;
        refreshSession(username, password);

    }
    
    static public String getPartnerToken()
    {
        return mPartnerToken;
    }
    
    static public void setPartnerToken(String token)
    {
        mPartnerToken = token;
    }

    public Session getCurrentSession()
    {
        return mSession;
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
//        int tries = 3;
//        while (tries > 0) {
//            try {
//                mSession = Authenticator.getSession(mPartnerToken, username,
//                    password);
//            } catch (IOException e) {
//                throw (new LockerException("connection issue"));
//            }
//            Caller.getInstance().setSession(mSession);
//            if (mSession != null) return;
//            tries--;
//        }
//        throw (new LockerException("connection issue"));
    }

    public long getLastUpdate(UpdateType type) throws LockerException, InvalidSessionException
    {

        String m = "lastUpdate";
        Map<String, String> params = new HashMap<String, String>();
        params.put("type", type.toString());
        try {
            RestResult restResult = Caller.getInstance().call(m, params);

            if (!restResult.isSuccessful())
                throw (new LockerException("Call Failed: "
                        + restResult.getErrorMessage()));
            try {
                int event = restResult.getParser().nextTag();

                while (event != XmlPullParser.END_DOCUMENT) {
                    String name = restResult.getParser().getName();
                    switch (event) {
                        case XmlPullParser.START_TAG:
                            if (name.equals("status")) {
                                String stat = restResult.getParser().nextText();
                                if (!stat.equals("1"))
                                    throw (new LockerException(
                                            "Getting last update failed"));
                            } else if (name.equals("timestamp")) {
                                return Long.parseLong(restResult.getParser()
                                        .nextText());
                            }
                            break;
                    }
                    event = restResult.getParser().next();
                }
            } catch (Exception e) {
                throw (new LockerException("Getting last update failed: "
                        + e.getMessage()));
            }
        } catch (IOException e) {
            throw (new LockerException("connection issue"));
        }
        return 0;
    }

    public Artist getArtist(int id) throws LockerException, InvalidSessionException
    {
        DataResult<Artist> res = fetchArtists(Integer.toString(id), null);
        if (res.getData().length == 1)
            return res.getData()[0];
        else
            throw (new NoSuchEntryException("No such artist w/ id " + id));

    }

    public List<Artist> getArtistsFromJson() throws LockerException, InvalidSessionException, JSONException
    {
        String text;
        try {
            text = HttpClientCaller.getInstance()
                .call(new RemoteMethod.Builder(RemoteMethod.METHODS.LOCKER_DATA)
                .addParam("type", "artist")
                .addParam("count", "200")
                .addParam("set", "0")
                .create());
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
        
        //String m = "lockerData";
        //Map<String, String> params = new HashMap<String, String>();
        //params.put("type", "artist");
        //if (artistId != "")
        //    params.put("artist_id", artistId);
        //else if (setQuery != null) {
        //    params.put("count", setQuery.count);
        //    params.put("set", setQuery.set);
        //}
//        try {
//            System.out.println("Making GET ARTISTS call");
//            RestResult restResult = Caller.getInstance().call(m, params);
//            System.out.println("BACK FROM GET ARTISTS call");
//            if (!restResult.isSuccessful())
//                throw (new LockerException("Call Failed: "
//                        + restResult.getErrorMessage()));
//
//            DataResult<Artist> results = null;
//            if (setQuery != null)
//                results = parseSetSummary(restResult);
//
//            Artist[] artists = parseArtists(restResult);
//            if (results == null)
//                results = new DataResult<Artist>("artist", artists.length);
//            results.setData(artists);
//            return results;
//        } catch (IOException e) {
//            throw (new LockerException("connection issue"));
//        }
    }
    
    public DataResult<Artist> getArtists() throws LockerException, InvalidSessionException
    {
        return fetchArtists("", null/*mDefaultQuery*/);
    }

    /**
     * Fetch a subset of artists
     * 
     * @param count
     *            maximum number of results to be returned in a result set
     * @param set
     *            the result set number to retrieve, note that set numbers are 0
     *            based
     * @return a list of count artists
     * @throws LockerException
     * @throws InvalidSessionException 
     */
    public SetResult<Artist> getArtistsSet(int count, int set)
            throws LockerException, InvalidSessionException
    {

        return (SetResult<Artist>) fetchArtists("", new SetQuery(count, set));
    }

    private static DataResult<Artist> fetchArtists(String artistId,
            SetQuery setQuery) throws LockerException, InvalidSessionException
    {
        String m = "lockerData";
        Map<String, String> params = new HashMap<String, String>();
        params.put("type", "artist");
        if (artistId != "")
            params.put("artist_id", artistId);
        else if (setQuery != null) {
            params.put("count", setQuery.count);
            params.put("set", setQuery.set);
        }
        try {
            System.out.println("Making GET ARTISTS call");
            Logger.log("Starting API Call");
            long t1 = java.lang.System.currentTimeMillis();
            RestResult restResult = Caller.getInstance().call(m, params);
            long t2 = java.lang.System.currentTimeMillis();
            Logger.log("API call took " + Long.toString(t2 - t1));
            System.out.println("BACK FROM GET ARTISTS call");
            Logger.log("Starting parse");
            t1 = java.lang.System.currentTimeMillis();
            if (!restResult.isSuccessful())
                throw (new LockerException("Call Failed: "
                        + restResult.getErrorMessage()));

            DataResult<Artist> results = null;
            if (setQuery != null)
                results = parseSetSummary(restResult);

            Artist[] artists = parseArtists(restResult);
            if (results == null)
                results = new DataResult<Artist>("artist", artists.length);
            results.setData(artists);
            t2 = java.lang.System.currentTimeMillis();
            Logger.log("parse took " + Long.toString(t2 - t1));
            return results;
        } catch (IOException e) {
            throw (new LockerException("connection issue"));
        }
    }

    private static Artist[] parseArtists(RestResult restResult)
            throws LockerException
    {
        try {
            List<Artist> artists = new ArrayList<Artist>();
            int event = restResult.getParser().nextTag();
            boolean loop = true;
            while (loop && event != XmlPullParser.END_DOCUMENT) {
                String name = restResult.getParser().getName();
                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (name.equals("item")) {
                            Artist a = Artist.artistFromResult(restResult);
                            if (a != null)
                                artists.add(a);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (name.equals("artistList"))
                            loop = false;
                        break;
                }
                event = restResult.getParser().next();
            }
            return artists.toArray(new Artist[artists.size()]);
        } catch (Exception e) {
            throw (new LockerException("Getting all artists failed: "
                    + e.getMessage()));
        }

    }

    public Album getAlbum(int id) throws LockerException, InvalidSessionException
    {

        Album[] list = fetchAlbums("", "", Integer.toString(id), null)
                .getData();
        if (list.length == 1)
            return list[0];
        else
            throw (new NoSuchEntryException("No such album w/ id " + id));
    }

    public DataResult<Album> getAlbumsForArtist(int id) throws LockerException, InvalidSessionException
    {
        return fetchAlbums(Integer.toString(id), "", "", mDefaultQuery);
    }

    public DataResult<Album> getAlbumsforToken(String token)
            throws LockerException, InvalidSessionException
    {
        return fetchAlbums("", token, "", mDefaultQuery);
    }

    public SetResult<Album> getAlbumsSet(int count, int set)
            throws LockerException, InvalidSessionException
    {

        return (SetResult<Album>) fetchAlbums("", "", "", new SetQuery(count,
                set));
    }

    public DataResult<Album> getAlbums() throws LockerException, InvalidSessionException
    {
        return fetchAlbums("", "", "", mDefaultQuery);
    }

    protected static DataResult<Album> fetchAlbums(String artistId,
            String token, String albumId, SetQuery setQuery)
            throws LockerException, InvalidSessionException
    {

        String m = "lockerData";
        Map<String, String> params = new HashMap<String, String>();
        params.put("type", "album");
        if (artistId != "")
            params.put("artist_id", artistId);
        if (token != "")
            params.put("token", token);
        if (albumId != "")
            params.put("album_id", albumId);
        if (setQuery != null) {
            params.put("count", setQuery.count);
            params.put("set", setQuery.set);
        }
        try {
            RestResult restResult = Caller.getInstance().call(m, params);
            if (!restResult.isSuccessful())
                throw (new LockerException("Call Failed: "
                        + restResult.getErrorMessage()));

            DataResult<Album> results = null;
            if (setQuery != null)
                results = parseSetSummary(restResult);

            Album[] albums = parseAlbums(restResult);
            if (results == null)
                results = new DataResult<Album>("album", albums.length);
            results.setData(albums);
            return results;
        } catch (IOException e) {
            throw (new LockerException("connection issue"));
        }

    }

    private static Album[] parseAlbums(RestResult restResult)
            throws LockerException
    {
        try {
            List<Album> albums = new ArrayList<Album>();
            int event = restResult.getParser().nextTag();
            boolean loop = true;
            while (loop && event != XmlPullParser.END_DOCUMENT) {
                String name = restResult.getParser().getName();
                //Log.w("Mp3Tunes", "tag: " + name);
                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (name.equals("item")) {
                            Album a = Album.albumFromResult(restResult);
                            if (a != null)
                                albums.add(a);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (name.equals("albumList"))
                            loop = false;
                        break;
                }
                event = restResult.getParser().next();
            }
            return albums.toArray(new Album[albums.size()]);
        } catch (Exception e) {
            throw (new LockerException("Getting albums failed: "
                    + e.getMessage()));
        }
    }

    public DataResult<Track> getTracks() throws LockerException, InvalidSessionException
    {
        return fetchTracks("", "", "", "", mDefaultQuery);
    }
    
    public DataResult<Track> getTracks(int start, int count) throws LockerException, InvalidSessionException
    {
        return fetchTracks("", "", "", "", new SetQuery(count, start));
    }

    public DataResult<Track> getTracksForAlbum(int albumId)
            throws LockerException, InvalidSessionException
    {
        return fetchTracks("", "", Integer.toString(albumId), "", mDefaultQuery);
    }

    public DataResult<Track> getTracksForArtist(int artistId)
            throws LockerException, InvalidSessionException
    {
        return fetchTracks(Integer.toString(artistId), "", "", "", mDefaultQuery);
    }

    public DataResult<Track> getTracksForToken(String token)
            throws LockerException, InvalidSessionException
    {
        return fetchTracks("", token, "", "", mDefaultQuery);
    }

    public DataResult<Track> getTracksForPlaylist(String playlistId)
            throws LockerException, InvalidSessionException
    {
        return fetchTracks("", "", "", playlistId, mDefaultQuery);
    }

    public SetResult<Track> getTracksSet(int count, int set) throws LockerException, InvalidSessionException
    {
        return (SetResult<Track>) fetchTracks("", "", "", "", new SetQuery(
                count, set));
    }

    protected static DataResult<Track> fetchTracks(String artistId,
            String token, String albumId, String playlistId, SetQuery setQuery)
            throws LockerException, InvalidSessionException
    {

        String m = "lockerData";
        Map<String, String> params = new HashMap<String, String>();
        params.put("type", "track");
        if (artistId != "")
            params.put("artist_id", artistId);
        if (token != "")
            params.put("token", token);
        if (albumId != "")
            params.put("album_id", albumId);
        if (playlistId != "")
            params.put("playlist_id", playlistId);
        if (setQuery != null) {
            params.put("count", setQuery.count);
            params.put("set", setQuery.set);
        }
        try {
            Log.w("Mp3Tunes", "Starting API Call");
            long t1 = java.lang.System.currentTimeMillis();
            RestResult restResult = Caller.getInstance().call(m, params);
            if (!restResult.isSuccessful())
                throw (new LockerException("Call Failed: "
                        + restResult.getErrorMessage()));
            long t2 = java.lang.System.currentTimeMillis();
            Log.w("Mp3Tunes", "API call took " + Long.toString(t2 - t1));

            //Debug.startAllocCounting();
            // NOTE: when set and type=track are passed the tracklist comes
            // BEFORE the set summary
            // Hence why this parseTracks call is before the parseSetSummary
            Log.w("Mp3Tunes", "Starting parse tracks");
            t1 = java.lang.System.currentTimeMillis();
            Track[] tracks = parseTracks(restResult);
            t2 = java.lang.System.currentTimeMillis();
            Log.w("Mp3Tunes", "parse tracks took " + Long.toString(t2 - t1));
            //Debug.stopAllocCounting();
            //Log.w("Mp3Tunes", "Thread alloc count: " + Integer.toString(Debug.getThreadAllocCount()) + " size: " + Integer.toString(Debug.getThreadAllocSize()));
            Log.w("Mp3Tunes", "Starting parse set summary");
            t1 = java.lang.System.currentTimeMillis();
            DataResult<Track> results = null;
            if (setQuery != null)
                results = parseSetSummary(restResult);
            t2 = java.lang.System.currentTimeMillis();
            Log.w("Mp3Tunes", "parse set summary took "
                    + Long.toString(t2 - t1));

            Log.w("Mp3Tunes", "Starting setData");
            t1 = java.lang.System.currentTimeMillis();
            if (results == null)
                results = new DataResult<Track>("track", tracks.length);
            results.setData(tracks);
            t2 = java.lang.System.currentTimeMillis();
            Log.w("Mp3Tunes", "setData took " + Long.toString(t2 - t1));

            //Debug.stopAllocCounting();
            //Log.w("Mp3Tunes", "Thread alloc count: " + Integer.toString(Debug.getThreadAllocCount()) + " size: " + Integer.toString(Debug.getThreadAllocSize()));
            return results;
        } catch (IOException e) {
            throw (new LockerException("connection issue"));
        }

    }

    private static Track[] parseTracks(RestResult restResult)
            throws LockerException
    {
        // Debug.startMethodTracing();
        
        int trackFromResultAllocs = 0;
        int tracksAddAllocs       = 0;
        int parseNextAllocs       = 0;
        int tracksToArrayAllocs   = 0;
        int trackFromResultAllocsSize = 0;
        int tracksAddAllocsSize       = 0;
        int parseNextAllocsSize       = 0;
        int tracksToArrayAllocsSize   = 0;
        try {
            List<Track> tracks = new ArrayList<Track>();
            int event = restResult.getParser().nextTag();
            boolean loop = true;
            while (loop && event != XmlPullParser.END_DOCUMENT) {
                String name = restResult.getParser().getName();
                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (name.equals("item")) {
                            //Debug.startAllocCounting();
                            Track t = Track.trackFromResult(restResult,
                                    mPartnerToken);
                            //Debug.stopAllocCounting();
                            //trackFromResultAllocs     += Debug.getThreadAllocCount();
                            //trackFromResultAllocsSize += Debug.getThreadAllocSize();
                            if (t != null) {
                                Debug.startAllocCounting();
                                tracks.add(t);
                                Debug.stopAllocCounting();
                                tracksAddAllocs     += Debug.getThreadAllocCount();
                                tracksAddAllocsSize += Debug.getThreadAllocSize();
                            }
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (name.equals("trackList"))
                            loop = false;
                        break;
                }
                Debug.startAllocCounting();
                event = restResult.getParser().next();
                Debug.stopAllocCounting();
                parseNextAllocs     += Debug.getThreadAllocCount();
                parseNextAllocsSize += Debug.getThreadAllocSize();
            }
            // Debug.stopMethodTracing();
            Debug.startAllocCounting();
            Track[] tracksA = tracks.toArray(new Track[tracks.size()]);
            Debug.stopAllocCounting();
            tracksToArrayAllocs     += Debug.getThreadAllocCount();
            tracksToArrayAllocsSize += Debug.getThreadAllocSize();
            
            Log.w("Mp3Tunes", "Creating Track caused: " + Integer.toString(trackFromResultAllocs) + " allocs for : " + Integer.toString(trackFromResultAllocsSize) + " bytes");

            Log.w("Mp3Tunes", Integer.toString(Track.trackFromResultCalls) + " calls to parse track");
            
            Log.w("Mp3Tunes", "Creating Track caused:        " + Integer.toString(Track.trackAllocs) + " allocs for : " + Integer.toString(Track.trackAllocsSize) + " bytes");
            Log.w("Mp3Tunes", "Setting Track caused:         " + Integer.toString(Track.setDataAllocs) + " allocs for : " + Integer.toString(Track.setDataAllocsSize) + " bytes");
            Log.w("Mp3Tunes", "Getting Name caused:          " + Integer.toString(Track.getNameAllocs) + " allocs for : " + Integer.toString(Track.getNameAllocsSize) + " bytes");
            Log.w("Mp3Tunes", "Getting Next caused:          " + Integer.toString(Track.nextAllocs) + " allocs for : " + Integer.toString(Track.nextAllocsSize) + " bytes");
            Log.w("Mp3Tunes", "Getting Next Text caused:     " + Integer.toString(Track.nextTextAllocs) + " allocs for : " + Integer.toString(Track.nextTextAllocsSize) + " bytes");
            Log.w("Mp3Tunes", "Adding Track caused:          " + Integer.toString(tracksAddAllocs) + " allocs for : " + Integer.toString(tracksAddAllocsSize) + " bytes");
            Log.w("Mp3Tunes", "getting next token caused:    " + Integer.toString(parseNextAllocs) + " allocs for : " + Integer.toString(parseNextAllocsSize) + " bytes");
            Log.w("Mp3Tunes", "creating tracks array caused: " + Integer.toString(tracksToArrayAllocs) + " allocs for : " + Integer.toString(tracksToArrayAllocsSize) + " bytes");
            
            return tracksA;
        } catch (Exception e) {
            // Debug.stopMethodTracing();
            throw (new LockerException("Getting albums failed: "
                    + e.getMessage()));
        }
    }

    public DataResult<Playlist> getPlaylists(boolean playmix)
            throws LockerException, InvalidSessionException
    {

        String m = "lockerData";
        Map<String, String> params = new HashMap<String, String>();
        params.put("type", "playlist");
        if (!playmix)
            params.put("noplaymix", "1");
        try {
            RestResult restResult = Caller.getInstance().call(m, params);
            if (!restResult.isSuccessful())
                throw (new LockerException("Call Failed: "
                        + restResult.getErrorMessage()));
            try {
                List<Playlist> playlists = new ArrayList<Playlist>();
                int event = restResult.getParser().nextTag();
                boolean loop = true;
                while (loop && event != XmlPullParser.END_DOCUMENT) {
                    String name = restResult.getParser().getName();
                    switch (event) {
                        case XmlPullParser.START_TAG:
                            if (name.equals("item")) {
                                Playlist p = Playlist
                                        .playlistFromResult(restResult);
                                if (p != null)
                                    playlists.add(p);
                            }
                            break;
                        case XmlPullParser.END_TAG:
                            if (name.equals("playlistList"))
                                loop = false;
                            break;
                    }
                    event = restResult.getParser().next();
                }

                DataResult<Playlist> results = new DataResult<Playlist>(
                        "playlist", playlists.size());
                results.setData(playlists
                        .toArray(new Playlist[playlists.size()]));
                return results;
            } catch (Exception e) {
                throw (new LockerException("Getting albums failed: "
                        + e.getMessage()));
            }
        } catch (IOException e) {
            Log.w("Mp3Tunes", Log.getStackTraceString(e));
        }
        return null;
    }
    
    public DataResult<Token> getArtistTokens() throws LockerException, InvalidSessionException
    {
        return fetchTokens("artist");
    }

    public DataResult<Token> getAlbumTokens() throws LockerException, InvalidSessionException
    {
        return fetchTokens("album");
    }

    public DataResult<Token> getTrackTokens() throws LockerException, InvalidSessionException
    {
        return fetchTokens("track");
    }

    protected static DataResult<Token> fetchTokens(String type)
            throws LockerException, InvalidSessionException
    {
        String m = "lockerData";
        Map<String, String> params = new HashMap<String, String>();
        params.put("type", type + "_token");
        try {
            RestResult restResult = Caller.getInstance().call(m, params);
            if (!restResult.isSuccessful())
                throw (new LockerException("Call Failed: "
                        + restResult.getErrorMessage()));
            List<Token> tokens = new ArrayList<Token>();
            try {
                int event = restResult.getParser().nextTag();
                boolean loop = true;
                while (loop && event != XmlPullParser.END_DOCUMENT) {
                    String name = restResult.getParser().getName();
                    switch (event) {
                        case XmlPullParser.START_TAG:
                            if (name.equals("item")) {
                                Token t = Token.tokenFromResult(restResult);
                                if (t != null)
                                    tokens.add(t);
                            }
                            break;
                        case XmlPullParser.END_TAG:
                            if (name.equals("tokenList"))
                                loop = false;
                            break;
                    }
                    event = restResult.getParser().next();
                }
            } catch (Exception e) {
                throw (new LockerException("Getting albums failed: "
                        + e.getMessage()));
            }

            DataResult<Token> results = new DataResult<Token>(type + "_token",
                    tokens.size());
            results.setData(tokens.toArray(new Token[tokens.size()]));
            return results;
        } catch (IOException e) {
            throw (new LockerException("connection issue"));
        }
    }

    private static <E> SetResult<E> parseSetSummary(RestResult restResult) throws LockerException
    {
        try {
            SetResult<E> result = new SetResult<E>();
            int event = restResult.getParser().nextTag();
            boolean loop = true;
            while (loop && event != XmlPullParser.END_DOCUMENT) {
                String name = restResult.getParser().getName();
                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (name.equals("type")) {
                            result.setType(restResult.getParser().nextText());
                        } else if (name.equals("totalResults")) {
                            result.setTotalResults(Integer.parseInt(restResult
                                    .getParser().nextText()));
                        } else if (name.equals("set")) {
                            result.setSet(Integer.parseInt(restResult
                                    .getParser().nextText()));
                        } else if (name.equals("count")) {
                            result.setCount(Integer.parseInt(restResult
                                    .getParser().nextText()));
                        } else if (name.equals("totalResultSets")) {
                            result
                                    .setTotalResultSets(Integer
                                            .parseInt(restResult.getParser()
                                                    .nextText()));
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (name.equals("summary"))
                            loop = false;
                        break;
                }
                event = restResult.getParser().next();
            }
            return result;
        } catch (Exception e) {
            throw (new LockerException("Getting set summary failed: "
                    + e.getMessage()));
        }
    }

    private class SetQuery
    {
        String count;
        String set;

        public SetQuery(int count, int set)
        {
            this.count = Integer.toString(count);
            this.set = Integer.toString(set);
        }

    }

    public SearchResult search(String query, boolean artist, boolean album,
            boolean track, int count, int set) throws LockerException, InvalidSessionException
    {
        if (!artist && !album && !track)
            return null;
        String m = "lockerSearch";
        Map<String, String> params = new HashMap<String, String>();

        params.put("s", query);
        String type = "";
        if (artist)
            type += "artist,";
        if (album)
            type += "album,";
        if (track)
            type += "track,";
        type = type.substring(0, type.length() - 1); // remove trailing comma
        params.put("type", type);

        if (count > 0)
            params.put("count", Integer.toString(count));
        if (set >= 0)
            params.put("set", Integer.toString(set));

        try {
            RestResult restResult = Caller.getInstance().call(m, params);
            if (!restResult.isSuccessful())
                throw (new LockerException("Call Failed: "
                        + restResult.getErrorMessage()));

            SearchResult result = parseSearchSummary(restResult);

            if (artist)
                result.setArtists(parseArtists(restResult));
            if (album)
                result.setAlbums(parseAlbums(restResult));
            if (track)
                result.setTracks(parseTracks(restResult));

            return result;
        } catch (IOException e) {
            throw (new LockerException("connection issue"));
        }
    }

    private static SearchResult parseSearchSummary(RestResult restResult) throws LockerException
    {
        try {
            SearchResult result = new SearchResult();
            int event = restResult.getParser().nextTag();
            boolean loop = true;
            while (loop && event != XmlPullParser.END_DOCUMENT) {
                String name = restResult.getParser().getName();
                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (name.equals("type")) {
                            result.setType(restResult.getParser().nextText());
                        } else if (name.equals("artist")) {
                            result
                                    .setTotalArtistResults((Integer
                                            .parseInt(restResult.getParser()
                                                    .nextText())));
                        } else if (name.equals("album")) {
                            result
                                    .setTotalAlbumResults((Integer
                                            .parseInt(restResult.getParser()
                                                    .nextText())));
                        } else if (name.equals("track")) {
                            result
                                    .setTotalTrackResults((Integer
                                            .parseInt(restResult.getParser()
                                                    .nextText())));
                        } else if (name.equals("set")) {
                            result.setSet(Integer.parseInt(restResult
                                    .getParser().nextText()));
                        } else if (name.equals("count")) {
                            result.setCount(Integer.parseInt(restResult
                                    .getParser().nextText()));
                        } else if (name.equals("totalResultSets")) {
                            result
                                    .setTotalResultSets(Integer
                                            .parseInt(restResult.getParser()
                                                    .nextText()));
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (name.equals("summary"))
                            loop = false;
                        break;
                }
                event = restResult.getParser().next();
            }
            return result;
        } catch (Exception e) {
            throw (new LockerException("Getting set summary failed: "
                    + e.getMessage()));
        }
    }

    public void setSession(Session session)
    {
        mSession = session;
    }

}
