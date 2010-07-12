/***************************************************************************
 *   Copyright 2008 Casey Link <unnamedrambler@gmail.com>                  *
 *   Copyright 2005-2009 Last.fm Ltd.                                      *
 *   Portions contributed by Casey Link, Lukasz Wisniewski,                *
 *   Mike Jennings, and Michael Novak Jr.                                  *
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
package com.mp3tunes.android.player.activity;

import java.util.concurrent.ExecutionException;

import com.binaryelysium.mp3tunes.api.LockerException;
import com.binaryelysium.mp3tunes.api.Session.LoginException;
import com.mp3tunes.android.player.MP3tunesApplication;
import com.mp3tunes.android.player.R;
import com.mp3tunes.android.player.util.DevHttpClient;
import com.mp3tunes.android.player.util.LifetimeLoggingActivity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * This activity handles authentication.
 */
public class Login extends LifetimeLoggingActivity
{

    public static final String PREFS = "LoginPrefs";
    //private boolean mLoginShown;
    private EditText mPassField;
    private EditText mUserField;
    private Button mLoginButton;
    private AlertDialog mProgDialog;
    
    private static final int PROGRESS = 0;

    String authInfo;

    /** Called when the activity is first created. */
    @Override
    public void onCreate( Bundle icicle )
    {
        super.onCreate( icicle );
        requestWindowFeature( Window.FEATURE_NO_TITLE );
        SharedPreferences settings = getSharedPreferences( PREFS, 0 );
        String  user = settings.getString( "mp3tunes_user", "" );
        String  pass = settings.getString( "mp3tunes_pass", "" );
        boolean autologin = settings.getBoolean("auto_login", false);
        System.out.println("user: " + user + " pass: " + pass);
        
        if ( !user.equals( "" ) && !pass.equals( "" ) && autologin )
        {
            AsyncTask<String, Void, String> task = new LoginTask().execute( user, pass );
            try
            {
                task.get();
            }
            catch ( InterruptedException e )
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            catch ( ExecutionException e )
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        setContentView( R.layout.login );

        mPassField = ( EditText ) findViewById( R.id.password );
        mUserField = ( EditText ) findViewById( R.id.username );
        mLoginButton = ( Button ) findViewById( R.id.sign_in_button );
        mPassField.setOnKeyListener( mKeyListener );
        
        AlertDialog.Builder builder;
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.progress_dialog,
                                       (ViewGroup) findViewById(R.id.layout_root));

        TextView text = (TextView) layout.findViewById(R.id.progress_text);
        text.setText(R.string.loading_authentication);

        builder = new AlertDialog.Builder(this);
        builder.setView(layout);
        mProgDialog = builder.create();
        
        mUserField.setText(user);
        mPassField.setText(pass);
        
        //move the cursor so you can see the beginning of the login
        if (mUserField.getText().length() > 0) {
        	mUserField.setSelection(1);
        	mUserField.setSelection(0);
        }
        
        // restore text fields
        if ( icicle != null )
        {
            user = icicle.getString( "username" );
            pass = icicle.getString( "pass" );
            if ( user != null )
                mUserField.setText( user );

            if ( pass != null )
                mPassField.setText( pass );
        }
        mLoginButton.setOnClickListener( mClickListener );
        mLoginButton.requestFocus();
    }
    
    @Override
    protected Dialog onCreateDialog( int id )
    {
        switch ( id )
        {
        case PROGRESS:
            return mProgDialog;
        default:
            return null;
        }
    }

    OnClickListener mClickListener = new View.OnClickListener()
    {

        public void onClick( View v )
        {
            Log.w("Mp3tunes", "Called on click");
            String user = mUserField.getText().toString();
            String password = mPassField.getText().toString();

            if ( user.length() == 0 || password.length() == 0 )
            {
                MP3tunesApplication.getInstance().presentError( v.getContext(),
                        getResources().getString( R.string.ERROR_MISSINGINFO_TITLE ),
                        getResources().getString( R.string.ERROR_MISSINGINFO ) );
                return;
            }
            showDialog( PROGRESS );
            new LoginTask().execute( user, password );
        }
    };

    OnKeyListener mKeyListener = new OnKeyListener()
    {

        public boolean onKey( View v, int keyCode, KeyEvent event )
        {
            switch ( event.getKeyCode() )
            {
            case KeyEvent.KEYCODE_ENTER:
                if (event.getAction() == KeyEvent.ACTION_UP)
                    return mLoginButton.performClick();
            }
            return false;
        }
    };
    
    private class LoginTask extends AsyncTask<String, Void, String>
    {
        com.binaryelysium.mp3tunes.api.Locker locker;
        String user;
        String pass;
        @Override
        protected String doInBackground( String... params )
        {
            user = params[0];
            pass = params[1];
            if (!user.matches(".*@.*")) {
                Log.w("Mp3Tunes", "No site assuming mp3tunes.com");
                user += "@mp3tunes.com";
            }
            try 
            {
                locker = new com.binaryelysium.mp3tunes.api.Locker(user, pass /*, new DevHttpClient(Login.this)*/);
                
                return "";
            } catch (LockerException e) {
                Log.w("mp3tunes", Log.getStackTraceString(e));
                return e.getMessage();
            } catch (LoginException e) {
                Log.w("mp3tunes", Log.getStackTraceString(e));
                return "auth failure";
            } catch (Exception e) {
                Log.w("mp3tunes", Log.getStackTraceString(e));
                return "Logic Error";
            }
        }
        
        @Override
        protected void onPostExecute( String result )
        {
            try {
                dismissDialog( PROGRESS );    
            } catch (IllegalArgumentException e) {}
            
            if(result.equals("")) {
                MP3tunesApplication.getInstance().setLocker(locker);
                SharedPreferences settings = getSharedPreferences(PREFS, 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString("mp3tunes_user", user);
                editor.putString("mp3tunes_pass", pass);
                editor.putBoolean("auto_login", true);
                editor.commit();
                
                Intent intent = new Intent(Login.this, LockerList.class);
                startActivity(intent);
                finish();
            } else if (result.contains("auth failure")) {
                MP3tunesApplication.getInstance().presentError(Login.this,
                        getResources().getString(R.string.ERROR_AUTH_TITLE),
                        getResources().getString(R.string.ERROR_AUTH ));
                ((EditText)findViewById(R.id.password)).setText("");
            } else if (result.contains("connection issue")) {
                MP3tunesApplication.getInstance().presentError(Login.this,
                        getResources().getString(R.string.ERROR_SERVER_UNAVAILABLE_TITLE),
                        getResources().getString(R.string.ERROR_SERVER_UNAVAILABLE ));
            } else if (result.contains("Logic Error")) {
                MP3tunesApplication.getInstance().presentError( Login.this, "Bad Error", "This is likely a logic error");
            }
        }
        
    }

}
