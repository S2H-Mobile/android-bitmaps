package de.s2hmobile.bitmaps;

import android.graphics.Bitmap;

public class TaggedBitmap {
	private final Bitmap mBitmap;
	private final String mKey;

	TaggedBitmap(final Bitmap bitmap, final String key) {
		mBitmap = bitmap;
		mKey = key;
	}

	public Bitmap getBitmap() {
		return mBitmap;
	}

	public String getKey() {
		return mKey;
	}
}
