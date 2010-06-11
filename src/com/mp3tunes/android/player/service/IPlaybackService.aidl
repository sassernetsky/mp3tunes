package com.mp3tunes.android.player.service;

import com.mp3tunes.android.player.ParcelableTrack;
import com.mp3tunes.android.player.IdParcel;
import com.mp3tunes.android.player.service.PlaybackState;

interface IPlaybackService {  

	PlaybackState getPlaybackState();

	/* Pause playback */
	void pause(); 
	
	/* Stop playback */
	void stop();
	
	/* Play the prev song in the playlist */
	void prev(); 
	
	/* Play the next song in the playlist */
	void next(); 
	
	/* Play the current selected item in the playlist */
	void start();

	/* Play the track at a particular position in the playlist */
	void startAt(int pos);
	
	ParcelableTrack getTrack();
	ParcelableTrack nextTrack();
	
	/* Returns the duration of the current track */
	long   getDuration();
	
	/* Returns the position of the current track */
	long   getPosition(); 
	
	/* Set the position of the currently played track. Returns true 
	   if the operation was successful.*/
	boolean setPosition(in int msec);
	
	/* Returns the percentage the track has buffered */
	int	   getBufferPercent();
	
	/* Returns true if a track is currently playing
	 * however the player might be paused, or buffering.  */
	boolean isPlaying();
	
	/* Returns true if a track is currently playing but paused */
	boolean isPaused();

	int getQueuePosition();
	void createPlaybackList(in IdParcel[] track_ids);
	void addToPlaybackList(in IdParcel[] track_ids);
	void togglePlayback();
	IdParcel[] getTrackIds();
	
} 