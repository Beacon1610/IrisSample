package com.iritech.iris;

import android.os.Parcel;
import android.os.Parcelable;

import com.iritech.mqel704.ImageData;

public class ImageDataParcelable extends ImageData implements Parcelable {
    protected ImageDataParcelable(ImageData imageData) {
        setFormat(imageData.getFormat());
        setKind(imageData.getKind());
        setWidth(imageData.getWidth());
        setImageHeight(imageData.getHeight());
        setData(imageData.getData());
    }

    protected ImageDataParcelable(Parcel in) {
        setFormat(in.readInt());
        setKind(in.readInt());
        setWidth(in.readInt());
        setImageHeight(in.readInt());
        int dataLength = in.readInt();
        if (dataLength > 0) {
            byte[] data = new byte[dataLength];
            in.readByteArray(data);
            setData(data);
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(getFormat());
        dest.writeInt(getKind());
        dest.writeInt(getWidth());
        dest.writeInt(getHeight());
        if (getData() != null) {
            dest.writeInt(getData().length);
            dest.writeByteArray(getData());
        } else {
            dest.writeInt(0);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ImageDataParcelable> CREATOR = new Creator<ImageDataParcelable>() {
        @Override
        public ImageDataParcelable createFromParcel(Parcel in) {
            return new ImageDataParcelable(in);
        }

        @Override
        public ImageDataParcelable[] newArray(int size) {
            return new ImageDataParcelable[size];
        }
    };
}
