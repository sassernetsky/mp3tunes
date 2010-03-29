package com.mp3tunes.android.player;

import com.binaryelysium.mp3tunes.api.Id;
import com.binaryelysium.mp3tunes.api.LockerId;

import android.os.Parcel;
import android.os.Parcelable;

public class IdParcel implements Parcelable
{
    Id mId;
    
    private static final int LOCAL_ID  = 0;
    private static final int LOCKER_ID = 1;
    
    public static Id idParcelToId(Parcelable p)
    {
         try {
             if (p != null) {
                 IdParcel parcel = (IdParcel)p;
                 return parcel.getId();
             } 
         } catch (Exception e) {}
         return null;
    }
    
    public IdParcel(Id id)
    {
        //TODO: remove defensive copy.  I do not think we need it anymore.
        mId = id.copyId();
    }
    
    public Id getId()
    {
        return mId;
    }
    
    
    public int describeContents()
    {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags)
    {
        out.writeString(mId.asString());
        if (LocalId.class.isInstance(mId))
            out.writeInt(LOCAL_ID);
        else if(LockerId.class.isInstance(mId))
            out.writeInt(LOCKER_ID);
        
    }

    public static final Parcelable.Creator<IdParcel> CREATOR
        = new Parcelable.Creator<IdParcel>() {
        public IdParcel createFromParcel(Parcel in) {
            return new IdParcel(in);
        }

        public IdParcel[] newArray(int size) {
            return new IdParcel[size];
        }
    };

    private static Id parcelToId(Parcel in)
    {
        String id   = in.readString();
        int    type = in.readInt();

        if (type == LOCAL_ID)
            return new LocalId(Integer.parseInt(id));
        else if (type == LOCKER_ID) 
            return new LockerId(id);
        return null;
    }
    
    private IdParcel(Parcel in) 
    {
        mId = parcelToId(in);
    }
    
}
