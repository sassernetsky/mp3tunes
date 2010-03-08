package com.mp3tunes.android.player;

import android.os.Parcel;
import android.os.Parcelable;

import com.binaryelysium.mp3tunes.api.ConcreteTrack;
import com.binaryelysium.mp3tunes.api.Track;

public class ParcelableTrack extends ConcreteTrack implements Parcelable
{

    public ParcelableTrack(Track t)
    {
        super(t);
    }
    
    public int describeContents()
    {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags)
    {
        out.writeParcelable(new IdParcel(getId()), flags);
        out.writeString(getPlayUrl(0));
        out.writeString(getTitle());
        out.writeInt(getArtistId());
        out.writeString(getArtistName());
        out.writeInt(getAlbumId());
        out.writeString(getAlbumTitle());
        out.writeString(getFileKey());
    }

    public static final Parcelable.Creator<ParcelableTrack> CREATOR
        = new Parcelable.Creator<ParcelableTrack>() {
        public ParcelableTrack createFromParcel(Parcel in) {
            return new ParcelableTrack(in);
            }

        public ParcelableTrack[] newArray(int size) {
            return new ParcelableTrack[size];
        }
    };
    
    private static Track parcelToTrack(Parcel in)
    {
        IdParcel id        = in.readParcelable(null);
        String playUrl     = in.readString();
        String title       = in.readString();
        int    artistId    = in.readInt();
        String artistName  = in.readString();
        int    albumId     = in.readInt();
        String albumTitle  = in.readString();
        String fileKey     = in.readString();
        
        return new ConcreteTrack(id.getId(), playUrl, title, artistId, artistName, albumId, albumTitle);
    }
    
    private ParcelableTrack(Parcel in) 
    {
        super(parcelToTrack(in));
    }
}
