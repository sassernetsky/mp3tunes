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
import android.util.Log;

public class Session {
	String mUsername;
	String mSessionId;

	public Session(String user, String session) {
		mUsername = user;
		mSessionId = session;
	}

	public Session() {
	}
	
	public static Session sessionFromJson(String text) throws JSONException, LockerException, LoginException
	{
	    Session session = new Session();
        JSONObject json = new JSONObject(text);
        int result = json.getInt("status");
	    if (result != 1) {
	        Log.e("Mp3Tunes", "Error login request returned: \"" + text + "\"");
	        String error     = json.getString("errorMessage");
	        int    errorCode = json.getInt("errorCode");
	        if (error.equals("Login failed")) throw new LoginException();
	        throw new LockerException("Error Code: " + Integer.toString(errorCode) + " Error Message: " + error);
	    }
	    session.mSessionId = json.getString("session_id");
	    session.mUsername  = json.getString("username");

	    return session;
	}
	
	public String getUsername() {
		return mUsername;
	}

	public String getSessionId() {
		return mSessionId;
	}

	public void setUsername(String username) {
		mUsername = username;
	}

	public void setSessionId(String sessionId) {
		mSessionId = sessionId;
	}

	public static class LoginException extends Exception
	{
        private static final long serialVersionUID = -3731217232625893463L;
	}
	
}
