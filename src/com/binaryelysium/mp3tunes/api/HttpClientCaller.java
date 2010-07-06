package com.binaryelysium.mp3tunes.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.UnknownHostException;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import com.binaryelysium.mp3tunes.api.Session.LoginException;


import android.util.Log;

public class HttpClientCaller
{
    public interface Progress
    {
        void run(long progress, long total);
    }

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

    private HttpClientCaller() {
    }

    public static HttpClientCaller getInstance() {
        return instance;
    }
    
    class MyEntity extends InputStreamEntity
    {

        public MyEntity(InputStream strm, long length)
        {
            super(strm, length);
        }
        
        public InputStream getContent() throws IOException
        {
            Log.w("Mp3Tunes", "Enering: getContent");
            try {
                return super.getContent();
            } finally {
                Log.w("Mp3Tunes", "Leaving: getContent");
            }
        }
        
        public long getContentLength()
        {
            Log.w("Mp3Tunes", "Enering: getContentLength");
            try {
                return super.getContentLength();
            } finally {
                Log.w("Mp3Tunes", "Leaving: getContentLength");
            }
        }
        
        public boolean isRepeatable() 
        {
            Log.w("Mp3Tunes", "Enering: isRepeatable");
            try {
                return super.isRepeatable();
            } finally {
                Log.w("Mp3Tunes", "Leaving: isRepeatable");
            }
        }
        
        public boolean isStreaming()
        {
            Log.w("Mp3Tunes", "Enering: isStreaming");
            try {
                return super.isStreaming();
            } finally {
                Log.w("Mp3Tunes", "Leaving: isStreaming");
            }
        }
        
        public void writeTo(OutputStream outstream) throws IOException
        {
            Log.w("Mp3Tunes", "Enering: writeTo");
            try {
                super.writeTo(outstream);
            } finally {
                Log.w("Mp3Tunes", "Leaving: writeTo");
            }
        }
    }
    
    class MyFileInputStream extends FileInputStream
    {
        long mFileSize;
        long mCurrent;
        int  mProgress; 
        Progress mProgressCallback;
        public MyFileInputStream(File file, Progress progress) throws FileNotFoundException
        {
            super(file);
            mFileSize = file.length();
            if (mFileSize == 0) throw new FileNotFoundException();
            mCurrent  = 0;
            mProgressCallback = progress;
        }
        
        public int  read(byte[] buffer, int offset, int count) throws IOException
        {
            mCurrent += count - offset;
            int progress = (int)((mCurrent * 100) / mFileSize);
            if (progress > mProgress) {
                Log.w("Mp3Tunes", "Upload progress: " + Integer.toString(progress));
                mProgress = progress;
                mProgressCallback.run(mProgress, 100);
            }
            try {
                return super.read(buffer, offset, count);
            } finally {
                //Log.w("Mp3Tunes", "Leaving: read(byte[] buffer, int offset, int count)");
            }
        }

        public int  read(byte[] buffer) throws IOException
        {
            Log.w("Mp3Tunes", "Enering: read(byte[] buffer)");
            try {
                return super.read(buffer);
            } finally {
                Log.w("Mp3Tunes", "Leaving: read(byte[] buffer)");
            }
        }
        
        public int  read() throws IOException
        {
            Log.w("Mp3Tunes", "Enering: read()");
            try {
                return super.read();
            } finally {
                Log.w("Mp3Tunes", "Leaving: read()");
            }
        }
    }
    
    public boolean put(RemoteMethod method, String file, Progress progress) throws IOException
    {
        HttpClient client = null;
        try {
            client = new DefaultHttpClient();
            String url = method.getCall();
            Log.w("Mp3tunes", "Calling: " + url);
            
            HttpPut           put    = new HttpPut(url);
            File              f      = new File(file);
            FileInputStream   strm   = new MyFileInputStream(f, progress);
            InputStreamEntity entity = new MyEntity(strm, f.length());
            put.setEntity(entity);
            
            Log.w("Mp3Tunes", "Starting put");
            HttpResponse response = client.execute(put);
            Log.w("Mp3Tunes", "Put done");
            if (response.containsHeader("X-MP3tunes-ErrorNo")) {
                return false;
            }
            return true;
        } catch (UnknownHostException e) {
            Log.e("Mp3Tunes", "UnknownHostException: what do we do?");
            throw e;
        } catch (SocketException e) {
            Log.e("Mp3Tunes", "SocketException: what do we do?");
            throw e;
        } finally {
            if (client != null)
                client.getConnectionManager().shutdown();
        }
    }
    
    public String callNoFixSession(RemoteMethod method) throws IOException, InvalidSessionException, LockerException, LoginException 
    {
        try {
            HttpClient client = new DefaultHttpClient();
            String url = method.getCall();
            Log.w("Mp3tunes", "Calling: " + url);
            HttpGet get = new HttpGet(url);
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
        }
    }

    public String call(RemoteMethod method) throws IOException, InvalidSessionException, LockerException, LoginException 
    {
        try {
            HttpClient client = new DefaultHttpClient();
            
            
            
            
//            KeyStore trustStore  = KeyStore.getInstance(KeyStore.getDefaultType());
//            SSLSocketFactory socketFactory = new SSLSocketFactory(trustStore);
//            socketFactory.setHostnameVerifier(new AllowAllHostnameVerifier());
//            Scheme sch = new Scheme("https", socketFactory, 443);
//            client.getConnectionManager().getSchemeRegistry().register(sch);
            
            
            String url = method.getCall();
            Log.w("Mp3tunes", "Calling: " + url);
            HttpGet get = new HttpGet(url);
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            String response = client.execute(get, responseHandler);
            client.getConnectionManager().shutdown();
            return response;
        } catch (HttpResponseException e) {
            Log.e("Mp3Tunes", "Status code: " + Integer.toString(e.getStatusCode()));
            if (e.getStatusCode() == 401) {
                if (!method.isLogin()) {
                    if (handleBadSession()) {
                        method.updateSession();
                        return call(method);
                    }
                }
            }
            throw e;
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
//        } catch (KeyStoreException e) {
//            e.printStackTrace();
//        } catch (KeyManagementException e) {
//            e.printStackTrace();
//        } catch (NoSuchAlgorithmException e) {
//            e.printStackTrace();
//        } catch (UnrecoverableKeyException e) {
//            e.printStackTrace();
//        }
//        throw new RuntimeException();
    }
    
    public byte[] callBytes(RemoteMethod method) throws IOException, InvalidSessionException, LockerException, LoginException 
    {
        try {
            HttpClient client = new DefaultHttpClient();
            String url = method.getCall();
            HttpGet get = new HttpGet(url);
            ResponseHandler<byte[]> responseHandler = new BytesResponseHandler();
            byte[] response = client.execute(get, responseHandler);
            client.getConnectionManager().shutdown();
            return response;
        } catch (HttpResponseException e) {
            Log.e("Mp3Tunes", "Status code: " + Integer.toString(e.getStatusCode()));
            if (e.getStatusCode() == 401) {
                if (!method.isLogin()) {
                    if (handleBadSession()) {
                        method.updateSession();
                        return callBytes(method);
                    }
                }
            }
            throw e;
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
    }
    
    
    
    private boolean handleBadSession() throws LockerException, LoginException 
    {
            LockerContext c = LockerContext.instance();
            
            String userName = c.getUserName();
            String password = c.getPassword();
            
            if (password == null || userName == null) 
                return false;
            
            Locker l = new Locker();
            l.refreshSession(userName, password);
            return true;
    }
  
    private class BytesResponseHandler implements ResponseHandler<byte[]>  
    {
        public byte[] handleResponse(HttpResponse response) throws ClientProtocolException, IOException 
        {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                return EntityUtils.toByteArray(entity);
            } else {
                return null;
            }
        }

    }

    public interface CreateStreamCallback
    {
        public void handleContentType(String contentType);
        public OutputStream createStream();
    }
    public boolean callStream(String url, CreateStreamCallback fileCreator, Progress progress) throws IOException
    {
        try {
            HttpClient client = new DefaultHttpClient();
            Log.w("Mp3tunes", "Calling: " + url);
            HttpGet get = new HttpGet(url);
            ResponseHandler<Boolean> responseHandler = new OutputStreamResponseHandler(fileCreator, progress);
            boolean response = client.execute(get, responseHandler);
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
    };
    
    static public abstract class CancellableResponseHandler implements ResponseHandler<Boolean>
    {
        HttpRequestBase mRequest;
        
        public abstract Boolean handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException;
        
        void setRequest(HttpRequestBase request)
        {
            mRequest = request;
        }
        
        public void abort()
        {
            if (mRequest != null)
                mRequest.abort();
        }
    };
    
    
    public boolean callStream(String url, CancellableResponseHandler handler, HttpRequestRetryHandler retry) throws IOException
    {
        try {
            DefaultHttpClient client = new DefaultHttpClient();
            if (retry != null)
                client.setHttpRequestRetryHandler(retry);
            HttpParams params = client.getParams();
            HttpConnectionParams.setConnectionTimeout(params, 15000);
            HttpConnectionParams.setSoTimeout(params, 15000);
            Log.w("Mp3tunes", "Calling: " + url);
            HttpGet get = new HttpGet(url);
            handler.setRequest(get);
            boolean response = client.execute(get, handler);
            client.getConnectionManager().shutdown();
            return response;
        }catch (HttpResponseException e) {
            //TODO: add this kind of code to a HttpRequestRetryHandler
            Log.e("Mp3Tunes", "Status code: " + Integer.toString(e.getStatusCode()));
            if (e.getStatusCode() == 401) {
                try {
                    if (handleBadSession()) {
                        int sidStart = url.indexOf("sid=") + 4;
                        assert (sidStart != -1);
                        int sidEnd   = url.indexOf('&', sidStart);
                        if (sidEnd != -1)
                            url.replace(url.substring(sidStart, sidEnd), LockerContext.instance().getSessionId());
                        else 
                            url.replace(url.substring(sidStart), LockerContext.instance().getSessionId());
                        return callStream(url, handler, retry);
                    }
                } catch (LockerException e1) {
                    e1.printStackTrace();
                } catch (LoginException e1) {
                    e1.printStackTrace();
                } catch (InvalidSessionException e1) {
                    e1.printStackTrace();
                }
            }
            throw e;
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
    };
    
    public boolean callStream(RemoteMethod method, CreateStreamCallback fileCreator, Progress progress) throws IOException
    {
        return callStream(method.getCall(), fileCreator, progress);
    };
    
    private class OutputStreamResponseHandler implements ResponseHandler<Boolean>  
    {
        CreateStreamCallback mCallback;
        Progress             mProgress;
        OutputStreamResponseHandler(CreateStreamCallback callback, Progress progress)
        {
            mCallback = callback;
            mProgress = progress;
        }
        
        public Boolean handleResponse(HttpResponse response) throws ClientProtocolException, IOException 
        {
            String contentType = "";
            for (Header h :response.getAllHeaders()) {
                if (h.getName().equals("Content-Type")) contentType = h.getValue();
            }
            mCallback.handleContentType(contentType);
            
            OutputStream stream = mCallback.createStream();
            
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                Long length = entity.getContentLength();
                InputStream input = entity.getContent();
                byte[] buffer = new byte[4096];
                int size  = 0;
                int total = 0;
                while (true) {
                    size = input.read(buffer);
                    if (size == -1) break;
                    stream.write(buffer, 0, size);
                    total += size;
                    mProgress.run(total, length);
                }
                return true;
            } else {
                return false;
            }
        }

    }
    
    private class OutputStreamResponseHandler2 implements ResponseHandler<Boolean>  
    {
        OutputStream mStream;
        Progress     mProgress;
        String       mContentType;
        OutputStreamResponseHandler2(OutputStream stream, Progress progress)
        {
            mStream   = stream;
            mProgress = progress;
        }
        
        public String getContentType()
        {
            return mContentType;
        }
        
        public Boolean handleResponse(HttpResponse response) throws ClientProtocolException, IOException 
        {
            String contentType = "";
            for (Header h :response.getAllHeaders()) {
                if (h.getName().equals("Content-Type")) mContentType = h.getValue();
            }
            
            //OutputStream stream = new FileOutputStream(mFile);
            
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                Long length = entity.getContentLength();
                InputStream input = entity.getContent();
                byte[] buffer = new byte[4096];
                int size  = 0;
                int total = 0;
                while (true) {
                    size = input.read(buffer);
                    if (size == -1) break;
                    mStream.write(buffer, 0, size);
                    total += size;
                    mProgress.run(total, length);
                }
                return true;
            } else {
                return false;
            }
        }

    }
}
