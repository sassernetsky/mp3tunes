package com.mp3tunes.android.player.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

import com.binaryelysium.mp3tunes.api.Track;
import com.binaryelysium.mp3tunes.api.HttpClientCaller;
import com.mp3tunes.android.player.Music;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.service.NanoHTTPD.Response;

public class HttpServer
{
    private PrivateServer   mServer;
    private String          mIp;
    private int             mPort;
    private String          mRoot;
    private PlaybackQueue   mQueue;
    
    private static HttpServer sServer = null;
    
    static HttpServer startServer(PlaybackQueue queue)
    {
        int i = 0;
        while (i < 5) {
            try {
                if (sServer != null) sServer.mServer.stop();
                String root = Music.getMP3tunesStorageRoot();
                sServer = new HttpServer(2222 + i, root, queue);
                return sServer;
            } catch (IOException e) {}
            i++;
        }
        return null;
    }
    
    private HttpServer(int port, String root, PlaybackQueue queue) throws IOException
    {
        mServer     = new PrivateServer(port, root);
        mQueue      = queue;
    }
    
    private class PrivateServer extends NanoHTTPD 
    {
        public PrivateServer(int port, String root) throws IOException
        {
            super(port);
            mPort = port;
            mRoot = root;
            mIp   = "127.0.0.1";
        }

        public Response serve( String uri, String method, Properties header, Properties parms )
        {
            Logger.log("HttpServer: " +  method + " '" + uri + "' " );

            Enumeration<?> e = header.propertyNames();
            while ( e.hasMoreElements()) {
                String value = (String)e.nextElement();
                Logger.log("HttpServer: " +   "  Request Header: '" + value + "' = '" + header.getProperty( value ) + "'" );
            }
            e = parms.propertyNames();
            while ( e.hasMoreElements()) {
                String value = (String)e.nextElement();
                Logger.log("HttpServer: " +   "  PRM: '" + value + "' = '" + parms.getProperty( value ) + "'" );
            }

            String fileKey = uri.substring(uri.lastIndexOf('/') + 1,uri.indexOf('_'));
            Response r = serveFile( uri, header, new File(mRoot), fileKey);
            Logger.log("HttpServer: " +  "Got response: " + r.status + " " + r.toString());
            if (r.status == NanoHTTPD.HTTP_INTERNALERROR)
                Logger.log("Error: " + r.errorMessage);
            e = r.header.propertyNames();
            while ( e.hasMoreElements()) {
                String value = (String)e.nextElement();
                Logger.log("HttpServer: " +   "  Response Header: '" + value + "' = '" + r.header.getProperty( value ) + "'" );
            }
            
            if (method.equals("HEAD"))
                    r.data = null;
            return r;
        }

        /**
         * Serves file from homeDir and its' subdirectories (only).
         * Uses only URI, ignores all headers and HTTP parameters.
         */
        public Response serveFile( String uri, Properties header, File homeDir, String fileKey)
        {
            Logger.log("HttpServer: in serve file");
            // Make sure we won't die of an exception later
            if ( !homeDir.isDirectory())
                return new Response(NanoHTTPD.HTTP_INTERNALERROR, NanoHTTPD.MIME_PLAINTEXT, "INTERNAL ERRROR: serveFile(): given homeDir is not a directory.");

            // Remove URL arguments
            uri = uri.trim().replace( File.separatorChar, '/' );
            if ( uri.indexOf( '?' ) >= 0 )
                uri = uri.substring(0, uri.indexOf( '?' ));

            // Prohibit getting out of current directory
            if ( uri.startsWith( ".." ) || uri.endsWith( ".." ) || uri.indexOf( "../" ) >= 0 )
                return new Response(NanoHTTPD.HTTP_FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "FORBIDDEN: Won't serve ../ for security reasons.");

            File f = new File( homeDir, uri );
            if ( !f.exists()) {
                Logger.log("HttpServer: " +  "No file at: " + f.getAbsolutePath());
                return new Response(NanoHTTPD.HTTP_NOTFOUND, NanoHTTPD.MIME_PLAINTEXT, "Error 404, file not found.");
            }
            
            // List the directory, if necessary
            if ( f.isDirectory()) {
                Logger.log("HttpServer: " +  "No file at: " + f.getAbsolutePath());
                return new Response(NanoHTTPD.HTTP_NOTFOUND, NanoHTTPD.MIME_PLAINTEXT, "Error 404, file not found.");
            }

            try {
                // Get MIME type from file name extension, if possible
                String mime = null;
                int dot = f.getCanonicalPath().lastIndexOf( '.' );
                if ( dot >= 0 )
                    mime = (String)NanoHTTPD.theMimeTypes.get( f.getCanonicalPath().substring( dot + 1 ).toLowerCase());
                if ( mime == null )
                    mime = NanoHTTPD.MIME_DEFAULT_BINARY;
            
                Logger.log(f.getCanonicalPath() + " Mime: " + mime);

                // Support (simple) skipping:
                long startFrom = 0;
                long end = 0;
                String range = header.getProperty( "range" );
                if ( range != null ) {
                    String from = "";
                    String to   = "";
                    if ( range.startsWith( "bytes=" )) {
                        range = range.substring( "bytes=".length());
                        Logger.log("HttpServer: " +  "range1: " + range);
                        int minus = range.indexOf( '-' );
                        if ( minus > 0 ) {
                            from = range.substring( 0, minus );
                            to   = range.substring(minus + 1);
                            Logger.log("HttpServer: " +  "range2: " + range);
                        }
                        try {
                            startFrom = Long.parseLong(from);
                            Logger.log("HttpServer: " +  "range2: " + startFrom);
                        } catch ( NumberFormatException nfe ) {
                            Logger.log("HttpServer: " +  "Range parse error");
                        }
                        try {
                            end = Long.parseLong(to);
                            Logger.log("HttpServer: " +  "range2: " + end);
                        } catch ( NumberFormatException nfe ) {
                            Logger.log("HttpServer: " +  "Range parse error");
                        }
                    }
                }

                CachedTrack track = mQueue.getTrackByFileKey(fileKey);
                long length = 0;
                Logger.log("HttpServer: waiting for track length");
                do {
                    if (track == null) 
                        return new Response(NanoHTTPD.HTTP_INTERNALERROR, NanoHTTPD.MIME_PLAINTEXT, "INTERNAL ERRROR: serveFile(): No current playback track.");
                    
                    int status = track.getStatus();
                    if (status == CachedTrack.Status.failed) {
                        return new Response("408 Request Timeout"/*NanoHTTPD.HTTP_RANGE_ERROR*/, NanoHTTPD.MIME_PLAINTEXT, "Download failed");
                    }
                    
                    if (status == CachedTrack.Status.finished) {
                        length = f.length();
                        break;
                    }
                    
                    length = track.getContentLength();
                } while (length == 0);
                //if (startFrom == 0) length += 30000;
                
                
                //The Http client in opencore does not behave itself.  It does not adjust its expected content size in
                //response to 416 errors nor does it adjust the amount of data it expects in response to a returned
                //Content-range header.  Since the server does not know the exact size of files that need
                //to be transcoded it often returns a content length that is slightly too long.  When this happens
                //opencore will wait forever for data that will never come.  I have been unable to come up with a
                //solution that is not fragile other than what is below.  We have included an MP3 that contains silence
                //and set the server up to return data from it until opencore has the ammount of data that it expects.
                Logger.log("HttpServer: " +  "File length: " + Long.toString(length) + " Start from: " + Long.toString(startFrom));
                InputStream fis = null;  
                if (startFrom >= length) {
                    if (startFrom > end) {
                        return new Response(NanoHTTPD.HTTP_RANGE_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Start of range greater than end of range");
                    }
                    length = end;
                    fis = mQueue.mContext.get().getResources().openRawResource(R.raw.silent);
                    fis.skip(100);
                } else {                
                    fis = new FileInputStream(f);
                    fis.skip( startFrom );
                }
                Response r = new Response(NanoHTTPD.HTTP_OK, mime, fis, track);
                r.addHeader("Content-length",  Long.toString(length/* - startFrom*/));
                r.addHeader("Content-range", Long.toString(startFrom) + "-" + Long.toString(length) + "/" + Long.toString(length));
            
                return r;
            } catch( IOException ioe ) {
                Logger.log("HttpServer: " +  "Server got IOException trying to read file");
                return new Response(NanoHTTPD.HTTP_FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "FORBIDDEN: Reading file failed.");
            }
        }
    }
    
    public static String pathToUrl(String path)
    {
        try {
            if (path.startsWith(sServer.mRoot)) {
                try {
                    URL url = new URL("http", sServer.mIp, sServer.mPort, path.replaceFirst(sServer.mRoot, ""));
                    return url.toString();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }
            Logger.log("Failed to turn path to url");
        } catch (Exception e) {
            Logger.log(e);
        }
        return null;
    };
    
    
}
