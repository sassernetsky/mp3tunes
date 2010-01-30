package com.binaryelysium.mp3tunes.api;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.util.Log;

import com.binaryelysium.util.StringUtilities;

public class HttpClientCaller
{
    @SuppressWarnings("serial")
    public class CallerException extends RuntimeException {

        public CallerException() {
        }

        public CallerException(Throwable cause) {
            super(cause);
        }

        public CallerException(String message) {
            super(message);
        }

        public CallerException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final HttpClientCaller instance = new HttpClientCaller();

    private String mUserAgent = "libmp3tunes-java";

    private Session mSession;

    private boolean mDebugMode = true;

    private HttpClientCaller() {
    }

    /**
     * Returns the single instance of the <code>Caller</code> class.
     * 
     * @return a <code>Caller</code>
     */
    public static HttpClientCaller getInstance() {
        return instance;
    }


    /**
     * Sets a User Agent this Caller will use for all upcoming HTTP requests. If
     * you distribute your application use an identifiable User-Agent.
     * 
     * @param userAgent
     *            a User-Agent string
     */
    public void setUserAgent(String userAgent) {
        this.mUserAgent = userAgent;
    }

    public void setSession(Session session) {
        mSession = session;
    }

    /**
     * Performs the web-service call. If the <code>session</code> parameter is
     * <code>non-null</code> then an authenticated call is made. If it's
     * <code>null</code> then an unauthenticated call is made.<br/>
     * The <code>apiKey</code> parameter is always required, even when a valid
     * session is passed to this method.
     * 
     * @param method
     *            The method to call
     * @param params
     *            Parameters
     * @param session
     *            A Session instance or <code>null</code>
     * @return the result of the operation
     * @throws InvalidSessionException 
     * @throws XmlPullParserException
     */
    public String call(RemoteMethod method) throws IOException, InvalidSessionException 
    {
        try {
            HttpClient client = new DefaultHttpClient();
            HttpGet get = new HttpGet(method.getCall());
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            String response = client.execute(get, responseHandler);
            client.getConnectionManager().shutdown();
            return response;
        } catch (UnknownHostException e) {
            Log.e("Mp3Tunes", "UnknownHostException: what do we do?");
            throw e;
        } catch (SocketException e) {
            Log.e("Mp3Tunes", "SocketException: what do we do?");
            throw e;
        } catch (IOException e) {
            Log.e("Mp3Tunes", Log.getStackTraceString(e));
            throw e;
        }
//        
//        try {
//        try {
//            OutputStream outputStream; 
//            URL url = new URL(method.getCall());
//            HttpURLConnection urlConnection;
//            if(mApiRootUrl.startsWith( "https" ))
//            {
//                //We are going to force the Hostname verification, because the android sdk's default
//                // Verifier is broken
//                HttpsURLConnection urlsConnection = (HttpsURLConnection) url.openConnection();
//                urlsConnection.setHostnameVerifier ( new HostnameVerifier() {
//                    public boolean verify ( String hostname, SSLSession session) {
//                        return true;
//                    }
//                });
//                urlsConnection.setRequestMethod("GET");
//                urlsConnection.setDoOutput(true);
//                urlConnection = urlsConnection;
//                outputStream = urlConnection.getOutputStream();
//            } else {
//                urlConnection = openConnection(mApiRootUrl + get);
//                
//                urlConnection.setRequestMethod("GET");
//                urlConnection.setDoOutput(true);
//                outputStream = urlConnection.getOutputStream();
//            }
//            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
//                    outputStream), 4196);
//
//            writer.write(get);
//            writer.close();
//            int responseCode = urlConnection.getResponseCode();
//            InputStream httpInput;
//            String errorHeader = urlConnection.getHeaderField("X-MP3tunes-ErrorNo");
//            if (responseCode == HttpURLConnection.HTTP_FORBIDDEN
//                    || responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
//                httpInput = urlConnection.getErrorStream();
//            } else if (responseCode != HttpURLConnection.HTTP_OK) {
//                return RestResult.createHttpErrorResult(responseCode, urlConnection
//                        .getResponseMessage());
//            } else if (errorHeader != null) {
//                String errorMsg = urlConnection.getHeaderField("X-MP3tunes-ErrorString");
//                return RestResult.createRestErrorResult(Integer.parseInt(errorHeader), errorMsg);
//            } else {
//                httpInput = urlConnection.getInputStream();
//            } 
//            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
//            factory.setNamespaceAware(true);
//            XmlPullParser xpp = factory.newPullParser();
//            {
//                xpp.setInput(httpInput, "utf-8");
//            }
//            return RestResult.createOkResult(xpp);
//        } catch (XmlPullParserException e) {
//            return RestResult.createRestErrorResult(RestResult.FAILURE, e.getMessage());
//        }
//        } catch (IOException e) {
//            Log.w("Mp3Tunes", Log.getStackTraceString(e));
//            //FIXME there should be a way to not have to do a string compare here
//            //But I do not have time to figure it out right now
//            if (e.getMessage().equals("Received authentication challenge is null")) {
//                throw new InvalidSessionException();
//            }
//            throw e;
//        }
    }

    /**
     * Creates a new {@link HttpURLConnection}, sets the proxy, if available,
     * and sets the User-Agent property.
     * 
     * @param url
     *            URL to connect to
     * @return a new connection.
     * @throws IOException
     *             if an I/O exception occurs.
     */
    public HttpURLConnection openConnection(String url) throws IOException {
        if (mDebugMode)
            System.out.println("open: " + url);
        URL u = new URL(url);
        HttpURLConnection urlConnection;
        /*
         * if (proxy != null) urlConnection = (HttpURLConnection)
         * u.openConnection(proxy); else
         */
        urlConnection = (HttpURLConnection) u.openConnection();
        urlConnection.setRequestProperty("User-Agent", mUserAgent);
        return urlConnection;
    }

    private String buildParameterQueue(String method,
            Map<String, String> params, String... strings) {
        StringBuilder builder = new StringBuilder(100);
        builder.append(method);
        builder.append('?');
        for (Iterator<Entry<String, String>> it = params.entrySet().iterator(); it
                .hasNext();) {
            Entry<String, String> entry = it.next();
            builder.append(entry.getKey());
            builder.append('=');
            builder.append(StringUtilities.encode(entry.getValue()));
            if (it.hasNext() || strings.length > 0)
                builder.append('&');
        }
        int count = 0;
        for (String string : strings) {
            builder.append(count % 2 == 0 ? string : StringUtilities
                    .encode(string));
            count++;
            if (count != strings.length) {
                if (count % 2 == 0) {
                    builder.append('&');
                } else {
                    builder.append('=');
                }
            }
        }
        return builder.toString();
    }
}
