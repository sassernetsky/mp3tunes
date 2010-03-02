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
        out.writeString(getDownloadUrl());
        out.writeString(getTitle());
        out.writeInt(getNumber());
        out.writeInt(getArtistId());
        out.writeString(getArtistName());
        out.writeInt(getAlbumId());
        out.writeString(getAlbumTitle());
        out.writeString(getAlbumArt());
        out.writeDouble(getDuration());
        out.writeString(getFileName());
        out.writeString(getFileKey());
        out.writeInt(getFileSize());
        out.writeString(getAlbumYear());
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
        String downloadUrl = in.readString();
        String title       = in.readString();
        int    number      = in.readInt();
        int    artistId    = in.readInt();
        String artistName  = in.readString();
        int    albumId     = in.readInt();
        String albumTitle  = in.readString();
        String albumArt    = in.readString();
        double duration    = in.readDouble();
        String fileName    = in.readString();
        String fileKey     = in.readString();
        int    fileSize    = in.readInt();
        String albumYear   = in.readString();
        
        return new ConcreteTrack(id.getId(), playUrl, downloadUrl, title, number,
                         artistId, artistName, albumId, albumTitle,
                         albumArt, duration, fileName, fileKey, fileSize,
                         albumYear);
    }
    
    private ParcelableTrack(Parcel in) 
    {
        super(parcelToTrack(in));
    }
}
