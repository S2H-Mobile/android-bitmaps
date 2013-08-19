package de.s2hmobile.bitmaps;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.support.v4.util.LruCache;

public class FractionalMemCache {

	/**
	 * Fractional amount of VM memory available to the cache. According to our
	 * test results, the memory fraction should be 4 on the emulator and 4 or 8
	 * on a real device.
	 */
	private static final int DEFAULT_MEMORY_FRACTION = 4;

	/** The LRU cache that stores the bitmaps. */
	private final LruCache<String, Bitmap> mLruCache;

	public FractionalMemCache(final int memoryFraction) {

		// TODO verify and use the value of memoryFraction

		/*
		 * Determine the size of the cache from the maximum available VM memory.
		 * Exceeding the VM memory will throw an OutOfMemoryException, so we use
		 * only a fraction of it. Integer numbers represent sizes in kiloBytes.
		 */
		final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
		final int cacheSize = maxMemory / DEFAULT_MEMORY_FRACTION;

		mLruCache = new LruCache<String, Bitmap>(cacheSize) {

			/**
			 * Measure item size in kilobytes rather than units which is more
			 * practical for a bitmap cache.
			 */
			@Override
			protected int sizeOf(final String key, final Bitmap bitmap) {
				final int bitmapSize = FractionalMemCache.getBitmapSize(bitmap) / 1024;
				return bitmapSize == 0 ? 1 : bitmapSize;
			}

		};
	}

	/**
	 * Get the size in bytes of a bitmap.
	 * 
	 * @param bitmap
	 *            - the bitmap to measure
	 * @return The size of the bitmap in bytes.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
	public static int getBitmapSize(final Bitmap bitmap) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
			return bitmap.getByteCount();
		} else {
			return bitmap.getRowBytes() * bitmap.getHeight();
		}
	}

	public void addTaggedBitmapToCache(final TaggedBitmap taggedBitmap) {
		final Bitmap bitmap = taggedBitmap.getBitmap();
		final String key = taggedBitmap.getKey();

		if (getBitmapFromCache(key) == null && bitmap != null) {
			mLruCache.put(key, bitmap);
		}
	}

	public Bitmap getBitmapFromCache(final String key) {
		return mLruCache.get(key);
	}
}
