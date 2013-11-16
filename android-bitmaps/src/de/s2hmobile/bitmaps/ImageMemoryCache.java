package de.s2hmobile.bitmaps;

import java.lang.ref.SoftReference;
import java.util.HashSet;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.support.v4.util.LruCache;

/**
 * 
 * @author Stephan Hoehne
 * 
 */
class ImageMemoryCache {

	/**
	 * Fractional amount of VM memory available to the cache. According to our
	 * test results, the memory fraction should be 4 on the emulator and 4 or 8
	 * on a real device.
	 */
	private static final int DEFAULT_FRACTION = 4;

	private final LruCache<String, BitmapDrawable> mMemoryCache;
	private final HashSet<SoftReference<Bitmap>> mReusableBitmaps;

	protected ImageMemoryCache(
			final HashSet<SoftReference<Bitmap>> reusableBitmaps,
			final int fraction) {

		mReusableBitmaps = reusableBitmaps;

		final int cacheSize = getCacheSize(fraction);

		android.util.Log.i("ImageMemoryCache", "create size --- " + cacheSize);

		mMemoryCache = new LruCache<String, BitmapDrawable>(cacheSize) {

			/**
			 * Notify the removed entry that is no longer being cached.
			 */
			@Override
			protected void entryRemoved(final boolean evicted,
					final String key, final BitmapDrawable oldValue,
					final BitmapDrawable newValue) {
				if (RecyclingBitmapDrawable.class.isInstance(oldValue)) {

					/*
					 * The removed entry is a recycling drawable, so notify it
					 * that it has been removed from the memory cache
					 */
					((RecyclingBitmapDrawable) oldValue).setIsCached(false);
				} else if (mReusableBitmaps != null) {

					/*
					 * We're running on Honeycomb or later, so add the old
					 * bitmap to a SoftRefrence set for possible use with
					 * inBitmap later.
					 */
					final Bitmap oldBitmap = oldValue.getBitmap();
					mReusableBitmaps.add(new SoftReference<Bitmap>(oldBitmap));
				}
			}

			/**
			 * Measure item size in kilobytes rather than units which is more
			 * practical for a bitmap cache.
			 */
			@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
			@Override
			protected int sizeOf(final String key, final BitmapDrawable value) {
				return getBitmapSize(value) / 1024;
			}

		};
	}

	protected void evictAll() {
		mMemoryCache.evictAll();
	}

	/**
	 * Get a bitmap from the memory cache.
	 * 
	 * @param key
	 *            - the key
	 * @return The bitmap associated to the key.
	 */
	protected BitmapDrawable get(final String key) {

		android.util.Log.i("ImageMemoryCache", "get bitmap " + key);

		return mMemoryCache.get(key);
	}

	/**
	 * Put a bitmap to the memory cache.
	 * 
	 * @param key
	 *            - the key
	 * @param value
	 *            -the bitmap to be cached
	 */
	protected void put(final String key, final BitmapDrawable value) {
		if (get(key) == null && value != null) {

			android.util.Log.i("ImageMemoryCache", "put bitmap " + key);

			mMemoryCache.put(key, value);
		}
	}

	/**
	 * Get the size in bytes of a {@link BitmapDrawable}.
	 * 
	 * @param bitmapDrawable
	 *            - the bitmap drawable
	 * @return The size of the bitmap in bytes.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
	private static int getBitmapSize(final BitmapDrawable bitmapDrawable) {
		final Bitmap bitmap = bitmapDrawable.getBitmap();

		if (bitmap == null) {
			return 0;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
			return bitmap.getByteCount();
		}

		return bitmap.getRowBytes() * bitmap.getHeight();
	}

	/**
	 * Cache size is stored in kilobytes instead of bytes as this will
	 * eventually be passed to construct a LruCache which takes an int in its
	 * constructor.
	 * 
	 * This value should be chosen carefully based on a number of factors Refer
	 * to the corresponding Android Training class for more discussion:
	 * http://developer.android.com/training/displaying-bitmaps/
	 * 
	 * @param fraction
	 *            - fraction of available app memory to use to size memory cache
	 */
	private static int getCacheSize(final int fraction) {
		int memoryFraction = DEFAULT_FRACTION;
		if (fraction > DEFAULT_FRACTION) {
			memoryFraction = fraction;
		}

		/*
		 * Determine the size of the cache from the maximum available VM memory.
		 * Exceeding the VM memory will throw an OutOfMemoryException, so we use
		 * only a fraction of it. Numbers represent sizes in kiloBytes.
		 */
		final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024L);
		return maxMemory / memoryFraction;
	}
}