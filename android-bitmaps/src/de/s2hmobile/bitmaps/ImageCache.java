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
import android.support.v4.util.LruCache;
import android.util.Log;
import de.s2hmobile.bitmaps.framework.DiskLruCache;

/**
 * Handles disk and memory caching of bitmaps in conjunction with the
 * {@link ImageLoader} class and its subclasses. Use
 * {@link ImageCache#getInstance(FragmentManager, ImageCacheParams)} to get an
 * instance of this class, although usually a cache should be added directly to
 * the loader by calling
 * {@link ImageLoader#addImageCache(FragmentManager, ImageCacheParams)}.
 */
public class ImageCache {

	/**
	 * A holder class that contains cache parameters.
	 */
	public static class ImageCacheParams {

		/**
		 * Fractional amount of VM memory available to the cache. According to
		 * our test results, the memory fraction should be 4 on the emulator and
		 * 4 or 8 on a real device.
		 */
		private static final int DEFAULT_MEMORY_FRACTION = 4;

		public CompressFormat compressFormat = CompressFormat.JPEG;
		public int compressQuality = 70;

		/*
		 * Parameters for disk cache.
		 */
		public File diskCacheDir = null;

		public boolean diskCacheEnabled = DEFAULT_DISK_CACHE_ENABLED;
		public int diskCacheSize = DEFAULT_DISK_CACHE_SIZE;

		/*
		 * Parameters for memory cache.
		 */
		public int mCacheSize = DEFAULT_MEM_CACHE_SIZE;
		public boolean memoryCacheEnabled = DEFAULT_MEM_CACHE_ENABLED;

		/**
		 * Create a set of image cache parameters that can be provided to
		 * {@link ImageCache#getInstance(FragmentManager, ImageCacheParams)} or
		 * {@link ImageLoader#addImageCache(FragmentManager, ImageCacheParams)}.
		 * 
		 * @param context
		 *            A context to use.
		 * @param diskCacheDirectoryName
		 *            A unique subdirectory name that will be appended to the
		 *            application cache directory. Usually "cache" or "images"
		 *            is sufficient.
		 */
		public ImageCacheParams(final Context context,
				final String diskCacheDirectoryName) {
			diskCacheDir = ExternalStorageHandler.getDiskCacheDir(context,
					diskCacheDirectoryName);
		}

		/**
		 * Cache size is stored in kilobytes instead of bytes as this will
		 * eventually be passed to construct a LruCache which takes an int in
		 * its constructor.
		 * 
		 * This value should be chosen carefully based on a number of factors
		 * Refer to the corresponding Android Training class for more
		 * discussion: http://developer.android.com/training/displaying-bitmaps/
		 * 
		 * @param fraction
		 *            - fraction of available app memory to use to size memory
		 *            cache
		 */
		public void setMemCacheFraction(final int fraction) {
			int memoryFraction = DEFAULT_MEMORY_FRACTION;
			if (fraction > DEFAULT_MEMORY_FRACTION) {
				memoryFraction = fraction;
			}

			/*
			 * Determine the size of the cache from the maximum available VM
			 * memory. Exceeding the VM memory will throw an
			 * OutOfMemoryException, so we use only a fraction of it. Numbers
			 * represent sizes in kiloBytes.
			 */
			final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024L);
			mCacheSize = maxMemory / memoryFraction;
		}

	}

	/**
	 * A simple non-UI fragment that stores a single object and is retained over
	 * configuration changes. It will be used to retain the {@link ImageCache}
	 * object.
	 */
	public static class RetainFragment extends Fragment {
		private Object mObject = null;

		public RetainFragment() {
		}

		/**
		 * Get the stored object.
		 * 
		 * @return The stored object.
		 */
		public Object getObject() {
			return mObject;
		}

		@Override
		public void onCreate(final Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			// this fragment is retained over a configuration change
			RetainFragment.this.setRetainInstance(true);
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

	private static final boolean DEFAULT_DISK_CACHE_ENABLED = true;

	// Default disk cache size in bytes
	private static final int DEFAULT_DISK_CACHE_SIZE = 1024 * 1024 * 20; // 20MB

	// Constants to easily toggle various caches
	private static final boolean DEFAULT_MEM_CACHE_ENABLED = true;

	// Default memory cache size in kilobytes
	private static final int DEFAULT_MEM_CACHE_SIZE = 1024 * 5; // 5MB

	private static final int DISK_CACHE_INDEX = 0;

	// TODO remove log statements
	private static final String TAG = "ImageCache";

	private ImageCacheParams mCacheParams = null;

	/*
	 * final empty object for synchronizing
	 */
	private final Object mDiskCacheLock = new Object();

	private boolean mDiskCacheStarting = true;

	private DiskLruCache mDiskLruCache = null;

	private LruCache<String, BitmapDrawable> mMemoryCache = null;

	private HashSet<SoftReference<Bitmap>> mReusableBitmaps = null;

	/**
	 * Create a new ImageCache object using the specified parameters. Initialize
	 * the memory LruCache, but NOT the disk cache. This should not be called
	 * directly by other classes, instead use
	 * {@link ImageCache#getInstance(FragmentManager, ImageCacheParams)} to
	 * fetch an ImageCache instance.
	 * 
	 * @param cacheParams
	 *            - the cache parameters to initialize the cache
	 */
	private ImageCache(final ImageCacheParams cacheParams) {
		mCacheParams = cacheParams;

		// Set up memory cache
		if (mCacheParams.memoryCacheEnabled) {
			if (BuildConfig.DEBUG) {
				Log.i(TAG, "Memory cache created (size = "
						+ mCacheParams.mCacheSize + ")");
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				mReusableBitmaps = new HashSet<SoftReference<Bitmap>>();
			}

			mMemoryCache = new LruCache<String, BitmapDrawable>(
					mCacheParams.mCacheSize) {

				/**
				 * Notify the removed entry that is no longer being cached
				 */
				@Override
				protected void entryRemoved(final boolean evicted,
						final String key, final BitmapDrawable oldValue,
						final BitmapDrawable newValue) {
					if (RecyclingBitmapDrawable.class.isInstance(oldValue)) {
						// The removed entry is a recycling drawable, so notify
						// it
						// that it has been removed from the memory cache
						((RecyclingBitmapDrawable) oldValue).setIsCached(false);
					} else {
						// The removed entry is a standard BitmapDrawable

						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
							// We're running on Honeycomb or later, so add the
							// bitmap
							// to a SoftRefrence set for possible use with
							// inBitmap later
							mReusableBitmaps.add(new SoftReference<Bitmap>(
									oldValue.getBitmap()));
						}
					}
				}

				/**
				 * Measure item size in kilobytes rather than units which is
				 * more practical for a bitmap cache.
				 */
				@Override
				protected int sizeOf(final String key,
						final BitmapDrawable value) {
					final int bitmapSize = ImageCache.getBitmapSize(value) / 1024;
					return bitmapSize == 0 ? 1 : bitmapSize;
				}

			};
		}
	}

	/**
	 * Adds a bitmap to both memory and disk cache.
	 * 
	 * @param data
	 *            Unique identifier for the bitmap to store
	 * @param value
	 *            The bitmap drawable to store
	 */
	public void addBitmapToCache(final int resId, final BitmapDrawable value) {
		if (resId == 0 || value == null) {
			return;
		}

		// final int height = value.getIntrinsicHeight();
		// final int width = value.getIntrinsicWidth();

		final String key = String.valueOf(resId);
		// + "_" + String.valueOf(height)
		// + "_" + String.valueOf(width);

		// Add to memory cache
		if (mMemoryCache != null) {
			if (RecyclingBitmapDrawable.class.isInstance(value)) {
				// The removed entry is a recycling drawable, so notify it
				// that it has been added into the memory cache
				((RecyclingBitmapDrawable) value).setIsCached(true);
			}

			if (BuildConfig.DEBUG) {
				android.util.Log.i("ImageCache", "Add " + key
						+ " to memory cache.");
			}

			mMemoryCache.put(key, value);
		}

		synchronized (mDiskCacheLock) {
			// Add to disk cache
			if (mDiskLruCache != null) {
				final String hashKey = hashKeyForDisk(key);
				OutputStream out = null;
				try {
					final DiskLruCache.Snapshot snapshot = mDiskLruCache
							.get(hashKey);
					if (snapshot == null) {
						final DiskLruCache.Editor editor = mDiskLruCache
								.edit(hashKey);
						if (editor != null) {

							if (BuildConfig.DEBUG) {
								android.util.Log.i("ImageCache", "Add " + key
										+ " to disk cache.");
								android.util.Log.i("ImageCache", "Hash for "
										+ key + " is " + hashKey);
							}

							out = editor.newOutputStream(DISK_CACHE_INDEX);
							value.getBitmap().compress(
									mCacheParams.compressFormat,
									mCacheParams.compressQuality, out);
							editor.commit();
							out.close();
						}
					} else {
						snapshot.getInputStream(DISK_CACHE_INDEX).close();
					}
				} catch (final IOException e) {
					Log.e(TAG, "addBitmapToCache - " + e);
				} catch (final Exception e) {
					Log.e(TAG, "addBitmapToCache - " + e);
				} finally {
					try {
						if (out != null) {
							out.close();
						}
					} catch (final IOException e) {
					}
				}
			}
		}
	}

	/**
	 * Clears both the memory and disk cache associated with this ImageCache
	 * object. Note that this includes disk access so this should not be
	 * executed on the main/UI thread.
	 */
	public void clearCache() {
		if (mMemoryCache != null) {
			mMemoryCache.evictAll();
			if (BuildConfig.DEBUG) {
				Log.d(TAG, "Memory cache cleared");
			}
		}

		synchronized (mDiskCacheLock) {
			mDiskCacheStarting = true;
			if (mDiskLruCache != null && !mDiskLruCache.isClosed()) {
				try {
					mDiskLruCache.delete();
					if (BuildConfig.DEBUG) {
						Log.d(TAG, "Disk cache cleared");
					}
				} catch (final IOException e) {
					Log.e(TAG, "clearCache - " + e);
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
	public void close() {
		synchronized (mDiskCacheLock) {
			if (mDiskLruCache != null) {
				try {
					if (!mDiskLruCache.isClosed()) {
						mDiskLruCache.close();
						mDiskLruCache = null;
						if (BuildConfig.DEBUG) {
							Log.d(TAG, "Disk cache closed");
						}
					}
				} catch (final IOException e) {
					Log.e(TAG, "close - " + e);
				}
			}
		}
	}

	/**
	 * Flushes the disk cache associated with this ImageCache object. Note that
	 * this includes disk access so this should not be executed on the main/UI
	 * thread.
	 */
	public void flush() {
		synchronized (mDiskCacheLock) {
			if (mDiskLruCache != null) {
				try {
					mDiskLruCache.flush();
					if (BuildConfig.DEBUG) {
						Log.d(TAG, "Disk cache flushed");
					}
				} catch (final IOException e) {
					Log.e(TAG, "flush - " + e);
				}
			}
		}
	}

	/**
	 * Get from disk cache.
	 * 
	 * @param resId
	 *            Unique identifier for which item to get
	 * @return The bitmap if found in cache, null otherwise
	 */
	public Bitmap getBitmapFromDiskCache(final int resId) {
		final String key = hashKeyForDisk(String.valueOf(resId));
		Bitmap bitmap = null;

		synchronized (mDiskCacheLock) {
			while (mDiskCacheStarting) {
				try {
					mDiskCacheLock.wait();
				} catch (final InterruptedException e) {
				}
			}
			if (mDiskLruCache != null) {
				InputStream inputStream = null;
				try {
					final DiskLruCache.Snapshot snapshot = mDiskLruCache
							.get(key);
					if (snapshot != null) {
						if (BuildConfig.DEBUG) {
							Log.i(TAG, "Disk cache hit for " + resId);
						}
						inputStream = snapshot.getInputStream(DISK_CACHE_INDEX);
						if (inputStream != null) {
							final FileDescriptor fd = ((FileInputStream) inputStream)
									.getFD();

							// Decode bitmap, but we don't want to sample so
							// give
							// MAX_VALUE as the target dimensions
							bitmap = ImageResizer
									.decodeSampledBitmapFromDescriptor(fd,
											Integer.MAX_VALUE,
											Integer.MAX_VALUE, this);
						}
					}
				} catch (final IOException e) {
					Log.e(TAG, "getBitmapFromDiskCache - " + e);
				} finally {
					try {
						if (inputStream != null) {
							inputStream.close();
						}
					} catch (final IOException e) {
					}
				}
			}
			return bitmap;
		}
	}

	/**
	 * Get from memory cache.
	 * 
	 * @param resId
	 *            - unique identifier for which item to get
	 * @return The bitmap drawable if found in cache, null otherwise
	 */
	public BitmapDrawable getBitmapFromMemCache(final int resId) {
		BitmapDrawable memValue = null;

		// TODO proper key
		if (mMemoryCache != null) {
			memValue = mMemoryCache.get(String.valueOf(resId));
		}

		if (BuildConfig.DEBUG && memValue != null) {
			Log.i(TAG, "Memory cache hit for image " + resId);
		}

		return memValue;
	}

	/**
	 * Initializes the disk cache. Note that this includes disk access so this
	 * should not be executed on the main/UI thread. By default an ImageCache
	 * does not initialize the disk cache when it is created, instead you should
	 * call initDiskCache() to initialize it on a background thread.
	 */
	public void initDiskCache() {

		// Set up disk cache
		synchronized (mDiskCacheLock) {
			if (mDiskLruCache == null || mDiskLruCache.isClosed()) {
				final File diskCacheDir = mCacheParams.diskCacheDir;
				if (mCacheParams.diskCacheEnabled && diskCacheDir != null) {
					if (!diskCacheDir.exists()) {
						diskCacheDir.mkdirs();
					}
					if (getUsableSpace(diskCacheDir) > mCacheParams.diskCacheSize) {
						try {
							mDiskLruCache = DiskLruCache.open(diskCacheDir, 1,
									1, mCacheParams.diskCacheSize);
							if (BuildConfig.DEBUG) {
								Log.i(TAG, "Disk cache initialized");
							}
						} catch (final IOException e) {
							mCacheParams.diskCacheDir = null;
							Log.e(TAG, "initDiskCache - " + e);
						}
					}
				}
			}
			mDiskCacheStarting = false;
			mDiskCacheLock.notifyAll();
		}
	}

	/**
	 * @param options
	 *            - BitmapFactory.Options with out* options populated
	 * @return Bitmap that case be used for inBitmap
	 */
	protected Bitmap getBitmapFromReusableSet(
			final BitmapFactory.Options options) {
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
	 * Get the size in bytes of a bitmap in a BitmapDrawable.
	 * 
	 * @param value
	 *            - the bitmap drawable
	 * @return The size of the bitmap in bytes.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
	public static int getBitmapSize(final BitmapDrawable value) {
		final Bitmap bitmap = value.getBitmap();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
			return bitmap.getByteCount();
		} else {
			return bitmap.getRowBytes() * bitmap.getHeight();
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
			final ImageCacheParams cacheParams) {

		// Search for, or create an instance of the non-UI RetainFragment
		final RetainFragment mRetainFragment = findOrCreateRetainFragment(fragmentManager);

		// See if we already have an ImageCache stored in RetainFragment
		ImageCache imageCache = (ImageCache) mRetainFragment.getObject();

		// No existing ImageCache, create one and store it in RetainFragment
		if (imageCache == null) {
			imageCache = new ImageCache(cacheParams);
			mRetainFragment.setObject(imageCache);
		}

		return imageCache;
	}

	/**
	 * Check how much usable space is available at a given path.
	 * 
	 * @param path
	 *            The path to check
	 * @return The space available in bytes
	 */
	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public static long getUsableSpace(final File path) {
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
	public static String hashKeyForDisk(final String key) {
		String cacheKey;
		try {
			final MessageDigest mDigest = MessageDigest.getInstance("MD5");
			mDigest.update(key.getBytes());
			cacheKey = bytesToHexString(mDigest.digest());
		} catch (final NoSuchAlgorithmException e) {
			cacheKey = String.valueOf(key.hashCode());
		}
		return cacheKey;
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

}
