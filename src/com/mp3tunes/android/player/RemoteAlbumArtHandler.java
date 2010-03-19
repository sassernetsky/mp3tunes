/***************************************************************************
 *   Copyright 2005-2009 Last.fm Ltd.                                      *
 *   Portions contributed by Casey Link, Lukasz Wisniewski,                *
 *   Mike Jennings, and Michael Novak Jr.                                  *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
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
package com.mp3tunes.android.player;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.binaryelysium.mp3tunes.api.InvalidSessionException;
import com.binaryelysium.mp3tunes.api.LockerId;
import com.binaryelysium.mp3tunes.api.RemoteMethod;
import com.binaryelysium.mp3tunes.api.Track;
import com.mp3tunes.android.player.content.TrackGetter;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.util.Log;


public class RemoteAlbumArtHandler extends Handler
{
    Handler mParentHandler;
    
    Track    mTrack;
    Context  mContext;
    
    public static final int GET_REMOTE_IMAGE = 3;
    public static final int REMOTE_IMAGE_DECODED = 4;
    
    public RemoteAlbumArtHandler(Looper looper, Handler parentHandler, Context c, Track t)
    {
        super( looper );
        mParentHandler = parentHandler;
        mContext = c;
        mTrack   = t;
    }

    public void handleMessage(Message msg)
    {
        
        Track t = (Track)msg.obj;
        if (t.sameMainMetaData(mTrack)) {
            Log.w("Mp3Tunes", "Have this track");
            return;
        }
        mTrack = t;
        //String url = getArtUrl(mTrack);
        //if (url == null) return;
        if (msg.what == GET_REMOTE_IMAGE)
        {
            // while decoding the new image, show the default album art
            Message numsg = mParentHandler.obtainMessage(REMOTE_IMAGE_DECODED, null);
            mParentHandler.removeMessages(REMOTE_IMAGE_DECODED);
            mParentHandler.sendMessageDelayed(numsg, 300);
            Bitmap bm = getArtwork(mTrack);
            if (bm != null)
            {
                numsg = mParentHandler.obtainMessage(REMOTE_IMAGE_DECODED, bm);
                mParentHandler.removeMessages(REMOTE_IMAGE_DECODED);
                mParentHandler.sendMessage(numsg);
            }
        }
    }
    
    private String getRemoteArtworkForLocalTrack(Track t)
    {
        String id     = "stYqie5s3hGAz_VW3cXxwQ";
        String render = "json";
        String album  = t.getAlbumTitle();
        String artist = t.getArtistName();
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("_id",     id));
        params.add(new BasicNameValuePair("_render", render));
        params.add(new BasicNameValuePair("album",   album));
        params.add(new BasicNameValuePair("artist",  artist));
        
        try {
            URI uri = URIUtils.createURI("http", "pipes.yahoo.com", -1, "/pipes/pipe.run", 
                    URLEncodedUtils.format(params, "UTF-8"), null);
            
            HttpGet get = new HttpGet(uri);
            Log.w("Mp3Tunes", "Url: " + get.getURI().toString());
            
            HttpClient client = new DefaultHttpClient();
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            String response = client.execute(get, responseHandler);
            client.getConnectionManager().shutdown();
            
            JSONObject obj   = new JSONObject(response);
            JSONObject value = obj.getJSONObject("value");
            JSONArray  items = value.getJSONArray("items");
            JSONObject item  = items.getJSONObject(0);
            JSONObject image = item.getJSONObject("image");
            
            return image.getString("url");
            
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    private String getArtworkFromStore(Track t)
    {
        Uri uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
        String[] projection = new String[] {MediaStore.Audio.Albums._ID , MediaStore.Audio.Albums.ALBUM_ART};
        Log.w("Mp3Tunes", "Artist: " + t.getArtistName() + " Album: " + t.getAlbumTitle() + " Title: " + t.getTitle());
        
        String where = MediaStore.Audio.Media.ALBUM  + "=?";
        String[] args  = new String[] {t.getAlbumTitle()};
        Log.w("Mp3tunes", "Album: " + t.getAlbumTitle());
        Cursor cursor = mContext.getContentResolver().query(uri, projection, where, args, null);
        if (cursor.moveToFirst()) {
            String url = cursor.getString(1);
            Log.w("Mp3tunes", "url: " + url);
            return cursor.getString(1);
        }
        Log.w("Mp3tunes", "Returning null for local album art");
        return null;
    }
    
    private String getArtUrl(Track t) 
    {
        if (LocalId.class.isInstance(t.getId())) {
            return getRemoteArtworkForLocalTrack(t);
        } 
        
        String fileKey = t.getFileKey();
        if (fileKey == null) return null;
        try {
            return new RemoteMethod.Builder(RemoteMethod.METHODS.ALBUM_ART_GET)
                    .addFileKey(fileKey)
                    .create().getCall();
        } catch (InvalidSessionException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    private String getArtFile(Track t)
    {
        if (LocalId.class.isInstance(t.getId())) {
            return getArtworkFromStore(t);
        }
        return null;
    }
    
    private Bitmap getArtwork(Track t)
    {
        String      urlstr = null;
        InputStream is     = null;
        String      file   = getArtFile(t);
        
        if (file != null) {
            try {
                is = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            try {
                urlstr = getArtUrl(t);
                URL url = new URL(urlstr);

                HttpURLConnection c = (HttpURLConnection)url.openConnection();
                c.setDoInput(true);
                c.connect();
                is = c.getInputStream();
            } catch (MalformedURLException e) {
                Log.d("RemoteImageHandler", "RemoteImageWorker passed invalid URL: " + urlstr);
            } catch (IOException e) {}

        }
        if (is == null) return null;
        Bitmap img;
        img = BitmapFactory.decodeStream( is );
        return img;
    }
    
}
