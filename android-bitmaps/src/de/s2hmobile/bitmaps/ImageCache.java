/* 
 * File modified by S2H Mobile, 2013.
 * 
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.s2hmobile.bitmaps;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Iterator;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;
import android.util.Log;
import de.s2hmobile.bitmaps.ImageCache.DiskCacheParams;
import de.s2hmobile.bitmaps.framework.DiskLruCache;

/**
 * Handles disk and memory caching of bitmaps in conjunction with the
 * {@link ImageLoader} class and its subclasses. Use
 * {@link ImageCache#getInstance(FragmentManager, DiskCacheParams)} to get an
 * instance of this class, although usually a cache should be added directly to
 * the loader by calling
 * {@link ImageLoader#addImageCache(FragmentManager, DiskCacheParams)}.
 */
public class ImageCache {

	/**
	 * A simple non-UI fragment that stores a single object and is retained over
	 * configuration changes. It will be used to retain the {@link ImageCache}
	 * object.
	 */
	public static class RetainFragment extends Fragment {
		private Object mObject = null;

		public RetainFragment() {
		}

		public Object getObject() {
			return mObject;
		}

		@Override
		public void onCreate(final Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			// this fragment is retained over a configuration change
			setRetainInstance(true);
		}

		/**
		 * Store a single object in this Fragment.
		 * 
		 * @param object
		 *            - the object to store
		 */
		public void setObject(final Object object) {
			mObject = object;
		}
	}

	/**
	 * A holder class that contains disk cache parameters.
	 */
	static class DiskCacheParams {

		private final File diskCacheDir;

		private final int diskCacheSize;

		/**
		 * Create a set of image cache parameters that can be provided to
		 * {@link ImageCache#getInstance(FragmentManager, DiskCacheParams)} or
		 * {@link ImageLoader#addImageCache(FragmentManager, DiskCacheParams)}.
		 * 
		 * @param context
		 *            A context to use.
		 * @param dirName
		 *            A unique subdirectory name that will be appended to the
		 *            application cache directory. Usually "cache" or "images"
		 *            is sufficient.
		 */
		public DiskCacheParams(final Context context, final String dirName,
				final int size) throws IOException {
			diskCacheDir = ExternalStorageHandler.getDiskCacheDir(context,
					dirName);
			diskCacheSize = size;
		}

		public File getDiskCacheDir() {
			return diskCacheDir;
		}

		public int getDiskCacheSize() {
			return diskCacheSize;
		}

	}

	private static final int DISK_CACHE_INDEX = 0x0;

	// TODO remove log statements
	private static final String TAG = "ImageCache";

	/** Final empty lock for synchronizing the cache access. */
	private final Object mDiskCacheLock = new Object();

	private boolean mDiskCacheStarting = true;

	private DiskLruCache mDiskLruCache = null;

	private ImageMemoryCache mMemoryCache = null;
	private DiskCacheParams mParams = null;
	private HashSet<SoftReference<Bitmap>> mReusableBitmaps = null;

	/**
	 * Create a new ImageCache object using the specified parameters. Initialize
	 * the memory LruCache, but NOT the disk cache. This should not be called
	 * directly by other classes, instead use
	 * {@link ImageCache#getInstance(FragmentManager, DiskCacheParams)} to fetch
	 * an ImageCache instance.
	 * 
	 * @param params
	 *            - the cache parameters to initialize the cache
	 */
	private ImageCache(final DiskCacheParams params, final int fraction) {
		mParams = params;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			mReusableBitmaps = new HashSet<SoftReference<Bitmap>>();
		}
		mMemoryCache = new ImageMemoryCache(mReusableBitmaps, fraction);
	}

	/**
	 * Clears both the memory and disk cache associated with this ImageCache
	 * object. Note that this includes disk access so this should not be
	 * executed on the main/UI thread.
	 */
	public void clearCache() throws IOException {
		if (mMemoryCache != null) {
			mMemoryCache.evictAll();
			if (BuildConfig.DEBUG) {
				Log.d(TAG, "Memory cache cleared");
			}
		}

		synchronized (mDiskCacheLock) {
			mDiskCacheStarting = true;
			if (mDiskLruCache != null && !mDiskLruCache.isClosed()) {

				mDiskLruCache.delete();
				if (BuildConfig.DEBUG) {
					Log.d(TAG, "Disk cache cleared");
				}

				mDiskLruCache = null;
				initDiskCache();
			}
		}
	}

	/**
	 * Closes the disk cache associated with this ImageCache object. Note that
	 * this includes disk access so this should not be executed on the main/UI
	 * thread.
	 */
	public void close() throws IOException {
		synchronized (mDiskCacheLock) {
			if (mDiskLruCache != null && !mDiskLruCache.isClosed()) {
				mDiskLruCache.close();
				mDiskLruCache = null;
			}
		}
	}

	/**
	 * Flushes the disk cache associated with this ImageCache object. Note that
	 * this includes disk access so this should not be executed on the main/UI
	 * thread.
	 * 
	 * @throws IOException
	 */
	public void flush() throws IOException {
		synchronized (mDiskCacheLock) {
			if (mDiskLruCache != null) {
				mDiskLruCache.flush();
				if (BuildConfig.DEBUG) {
					Log.d(TAG, "Disk cache flushed");
				}
			}
		}
	}

	/**
	 * Adds a bitmap to both memory and disk cache.
	 * 
	 * @param data
	 *            Unique identifier for the bitmap to store
	 * @param value
	 *            The bitmap drawable to store
	 * @throws IOException
	 */
	void addToCache(final String key, final BitmapDrawable value)
			throws IOException {
		if (TextUtils.isEmpty(key) || value == null) {
			return;
		}

		// add drawable to memory cache
		if (mMemoryCache != null) {
			if (RecyclingBitmapDrawable.class.isInstance(value)) {

				// The removed entry is a recycling drawable, so notify it
				// that it has been added into the memory cache
				((RecyclingBitmapDrawable) value).setIsCached(true);
			}
			mMemoryCache.put(key, value);
		}

		// write bitmap to disk cache
		synchronized (mDiskCacheLock) {
			if (mDiskLruCache != null) {
				writeToDisk(mDiskLruCache, key, value.getBitmap());
			}
		}
	}

	/**
	 * Get bitmap from memory cache.
	 * 
	 * @return The bitmap drawable if found in cache, null otherwise
	 */
	BitmapDrawable getBitmapDrawableFromMemCache(final String key) {
		return mMemoryCache != null ? mMemoryCache.get(key) : null;
	}

	/**
	 * Get from disk cache.
	 * 
	 * @param resId
	 *            Unique identifier for which item to get
	 * @return The bitmap if found in cache, null otherwise
	 */
	Bitmap getBitmapFromDiskCache(final String key) throws IOException {

		synchronized (mDiskCacheLock) {
			while (mDiskCacheStarting) {
				try {
					mDiskCacheLock.wait();
				} catch (final InterruptedException e) {
				}
			}
			return mDiskLruCache != null ? readFromDisk(key) : null;
		}
	}

	/**
	 * @param options
	 *            - BitmapFactory.Options with out* options populated
	 * @return Bitmap that case be used for inBitmap
	 */
	Bitmap getBitmapFromReusableSet(final BitmapFactory.Options options) {
		Bitmap bitmap = null;

		if (mReusableBitmaps != null && !mReusableBitmaps.isEmpty()) {
			final Iterator<SoftReference<Bitmap>> iterator = mReusableBitmaps
					.iterator();
			Bitmap item;

			while (iterator.hasNext()) {
				item = iterator.next().get();

				if (null != item && item.isMutable()) {
					// Check to see it the item can be used for inBitmap
					if (canUseForInBitmap(item, options)) {
						bitmap = item;

						// Remove from reusable set so it can't be used again
						iterator.remove();
						break;
					}
				} else {
					// Remove from the set if the reference has been cleared.
					iterator.remove();
				}
			}
		}

		return bitmap;
	}

	/**
	 * Initializes the disk cache. Note that this includes disk access so this
	 * should not be executed on the main/UI thread. By default an ImageCache
	 * does not initialize the disk cache when it is created, instead you should
	 * call initDiskCache() to initialize it on a background thread.
	 */
	void initDiskCache() throws IOException {
		synchronized (mDiskCacheLock) {
			if (mDiskLruCache == null || mDiskLruCache.isClosed()) {

				mDiskLruCache = createDiskCache(mParams);
			}
			mDiskCacheStarting = false;
			mDiskCacheLock.notifyAll();
		}
	}

	/**
	 * Decode and sample down a bitmap from a file input stream to the requested
	 * width and height.
	 * 
	 * @param fd
	 *            The file descriptor to read from
	 * @param reqWidth
	 *            The requested width of the resulting bitmap
	 * @param reqHeight
	 *            The requested height of the resulting bitmap
	 * @param cache
	 *            The ImageCache used to find candidate bitmaps for use with
	 *            inBitmap
	 * @return A bitmap sampled down from the original with the same aspect
	 *         ratio and dimensions that are equal to or greater than the
	 *         requested width and height
	 */
	private Bitmap decodeSampledBitmapFromDescriptor(final FileDescriptor fd) {
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inSampleSize = 1;
		options.inJustDecodeBounds = false;

		// If we're running on Honeycomb or newer, try to use inBitmap
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			options.inMutable = true;

			// Try and find a bitmap to use for inBitmap
			final Bitmap inBitmap = getBitmapFromReusableSet(options);
			if (inBitmap != null) {
				options.inBitmap = inBitmap;
			}
		}

		return BitmapFactory.decodeFileDescriptor(fd, null, options);
	}

	/**
	 * TODO might have to be synchronized
	 * 
	 * @param key
	 * @return
	 * @throws IOException
	 */
	private Bitmap readFromDisk(final String key) throws IOException {
		InputStream inputStream = null;
		try {
			final DiskLruCache.Snapshot snapshot = mDiskLruCache
					.get(hashKeyForDisk(key));
			if (snapshot == null) {
				return null;
			}

			inputStream = snapshot.getInputStream(DISK_CACHE_INDEX);
			if (inputStream == null) {
				return null;
			}

			final FileDescriptor fd = ((FileInputStream) inputStream).getFD();

			Log.i(TAG, "Disk cache hit for " + key);
			// Decode bitmap, but we don't want to sample so
			// give
			// MAX_VALUE as the target dimensions
			final Bitmap result = decodeSampledBitmapFromDescriptor(fd);
			return result;

		} finally {
			if (inputStream != null) {
				inputStream.close();
			}
		}
	}

	/**
	 * Return an {@link ImageCache} instance. A {@link RetainFragment} is used
	 * to retain the ImageCache object across configuration changes such as a
	 * change in device orientation.
	 * 
	 * @param fragmentManager
	 *            The fragment manager to use when dealing with the retained
	 *            fragment.
	 * @param cacheParams
	 *            The cache parameters to use if the ImageCache needs
	 *            instantiation.
	 * @return An existing retained ImageCache object or a new one if one did
	 *         not exist
	 */
	public static ImageCache getInstance(final FragmentManager fragmentManager,
			final DiskCacheParams cacheParams, final int fraction) {

		// Search for, or create an instance of the non-UI RetainFragment
		final RetainFragment mRetainFragment = findOrCreateRetainFragment(fragmentManager);

		// See if we already have an ImageCache stored in RetainFragment
		ImageCache imageCache = (ImageCache) mRetainFragment.getObject();

		// No existing ImageCache, create one and store it in RetainFragment
		if (imageCache == null) {
			imageCache = new ImageCache(cacheParams, fraction);
			mRetainFragment.setObject(imageCache);
		}

		return imageCache;
	}

	private static String bytesToHexString(final byte[] bytes) {

		// http://stackoverflow.com/questions/332079
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < bytes.length; i++) {
			final String hex = Integer.toHexString(0xFF & bytes[i]);
			if (hex.length() == 1) {
				sb.append('0');
			}
			sb.append(hex);
		}
		return sb.toString();
	}

	/**
	 * @param candidate
	 *            - Bitmap to check
	 * @param targetOptions
	 *            - Options that have the out* value populated
	 * @return true if <code>candidate</code> can be used for inBitmap re-use
	 *         with <code>targetOptions</code>
	 */
	private static boolean canUseForInBitmap(final Bitmap candidate,
			final BitmapFactory.Options targetOptions) {
		final int width = targetOptions.outWidth / targetOptions.inSampleSize;
		final int height = targetOptions.outHeight / targetOptions.inSampleSize;

		return candidate.getWidth() == width && candidate.getHeight() == height;
	}

	private static DiskLruCache createDiskCache(final DiskCacheParams params)
			throws IOException {
		final File diskCacheDir = params.getDiskCacheDir();
		if (diskCacheDir == null) {
			return null;
		}

		if (!diskCacheDir.exists()) {
			diskCacheDir.mkdirs();
		}

		final int diskCacheSize = params.getDiskCacheSize();
		if (getUsableSpace(diskCacheDir) <= diskCacheSize) {
			return null;
		}

		return DiskLruCache.open(diskCacheDir, 1, 1, diskCacheSize);
	}

	/**
	 * Locate an existing instance of this Fragment or if not found, create and
	 * add it using FragmentManager.
	 * 
	 * @param fm
	 *            The FragmentManager manager to use.
	 * @return The existing instance of the Fragment or the new instance if just
	 *         created.
	 */
	private static RetainFragment findOrCreateRetainFragment(
			final FragmentManager fm) {

		// Check to see if we have retained the worker fragment.
		RetainFragment mRetainFragment = (RetainFragment) fm
				.findFragmentByTag(TAG);

		// If not retained (or first time running), we need to create and add
		// it.
		if (mRetainFragment == null) {
			mRetainFragment = new RetainFragment();
			fm.beginTransaction().add(mRetainFragment, TAG)
					.commitAllowingStateLoss();
		}

		return mRetainFragment;
	}

	/**
	 * Check how much usable space is available at a given path.
	 * 
	 * @param path
	 *            The path to check
	 * @return The space available in bytes
	 */
	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	private static long getUsableSpace(final File path) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			return path.getUsableSpace();
		} else {
			final StatFs stats = new StatFs(path.getPath());

			@SuppressWarnings("deprecation")
			final int usableSpace = stats.getBlockSize()
					* stats.getAvailableBlocks();

			return usableSpace;
		}
	}

	/**
	 * A hashing method that changes a string (like a URL) into a hash suitable
	 * for using as a disk filename.
	 */
	private static String hashKeyForDisk(final String key) {
		String cacheKey;
		try {
			final MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(key.getBytes());
			cacheKey = bytesToHexString(md.digest());
		} catch (final NoSuchAlgorithmException e) {
			cacheKey = String.valueOf(key.hashCode());
		}
		return cacheKey;
	}

	private static void writeToDisk(final DiskLruCache cache, final String key,
			final Bitmap bitmap) throws IOException {
		final String hashKey = hashKeyForDisk(key);

		android.util.Log.i("ImageCache", "Hash for " + key + " is " + hashKey);

		OutputStream out = null;
		try {
			final DiskLruCache.Snapshot snapshot = cache.get(hashKey);
			if (snapshot == null) {
				final DiskLruCache.Editor editor = cache.edit(hashKey);
				if (editor != null) {

					// TODO remove
					android.util.Log.i("ImageCache", "Write " + key
							+ " to disk cache.");

					out = editor.newOutputStream(DISK_CACHE_INDEX);
					bitmap.compress(CompressFormat.JPEG, 100, out);
					editor.commit();
					out.close();
				}
			} else {
				snapshot.getInputStream(DISK_CACHE_INDEX).close();
			}

		} finally {
			if (out != null) {
				out.close();
			}
		}
	}
}