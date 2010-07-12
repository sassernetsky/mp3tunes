package com.mp3tunes.android.player.util;

import java.io.InputStream;
import java.security.KeyStore;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;

import com.mp3tunes.android.player.R;

import android.content.Context;

public class DevHttpClient extends DefaultHttpClient 
{        
    final Context context;

    public DevHttpClient(Context context) 
    {
      this.context = context;
    }

    @Override 
    protected ClientConnectionManager createClientConnectionManager() 
    {
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(
                new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        registry.register(new Scheme("https", newSslSocketFactory(), 443));
        return new SingleClientConnManager(getParams(), registry);
    }

    private SSLSocketFactory newSslSocketFactory() 
    {
        try {
            KeyStore trusted = KeyStore.getInstance("BKS");
            InputStream in = context.getResources().openRawResource(R.raw.mystore);
            try {
                trusted.load(in, "ez24get".toCharArray());
            } finally {
                in.close();
            }
            SSLSocketFactory factory = new SSLSocketFactory(trusted);
            factory.setHostnameVerifier(new AllowAllHostnameVerifier());
            return factory;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
