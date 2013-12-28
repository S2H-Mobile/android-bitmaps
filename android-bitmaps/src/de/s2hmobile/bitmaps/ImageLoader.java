/* File modified by S2H Mobile, 2013.
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
import java.io.IOException;
import java.lang.ref.WeakReference;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.app.FragmentManager;
import android.widget.ImageView;
import de.s2hmobile.bitmaps.framework.AsyncTask;

/**
 * This class wraps up completing some arbitrary long running work when loading
 * a bitmap to an ImageView. It handles things like using a memory and disk
 * cache, running the work in a background thread and setting a placeholder
 * image.
 */
public class ImageLoader {

	protected class CacheAsyncTask extends AsyncTask<Integer, Void, Void> {

		@Override
		protected Void doInBackground(final Integer... params) {
			try {
				switch (params[0]) {
				case MESSAGE_CLEAR:
					clearCacheInternal();
					break;
				case MESSAGE_INIT_DISK_CACHE:
					initDiskCacheInternal();
					break;
				case MESSAGE_FLUSH:
					flushCacheInternal();
					break;
				case MESSAGE_CLOSE:
					closeCacheInternal();
					break;
				}
			} catch (final IOException e) {
			}
			return null;
		}
	}

	/**
	 * A custom Drawable that will be attached to the image view while the work
	 * is in progress. Contains a reference to the actual worker task, so that
	 * it can be stopped if a new binding is required, and makes sure that only
	 * the last started worker process can bind its result, independently of the
	 * finish order.
	 */
	private static class AsyncDrawable extends BitmapDrawable {

		private final WeakReference<BitmapWorkerTask> mBitmapTaskReference;

		public AsyncDrawable(final Resources res, final Bitmap bitmap,
				final BitmapWorkerTask bitmapWorkerTask) {
			super(res, bitmap);
			mBitmapTaskReference = new WeakReference<BitmapWorkerTask>(
					bitmapWorkerTask);
		}

		public BitmapWorkerTask getBitmapWorkerTask() {
			return mBitmapTaskReference.get();
		}
	}

	private static final int MESSAGE_CLEAR = 0x10;
	private static final int MESSAGE_CLOSE = 0x13;
	private static final int MESSAGE_FLUSH = 0x12;
	private static final int MESSAGE_INIT_DISK_CACHE = 0x11;

	// protected boolean mPauseWork = false;
	// private boolean mExitTasksEarly = false;

	private ImageCache mImageCache = null;
	private Bitmap mLoadingBitmap = null;

	// private final Object mPauseWorkLock = new Object();

	private final Resources mResources;

	public ImageLoader(final Resources resources) {
		mResources = resources;
	}

	/**
	 * Adds a cache to handle disk and memory bitmap caching.
	 * 
	 * @param fm
	 *            - to handle the retain fragment
	 * @param params
	 *            - for the disk cache
	 * @param fraction
	 *            - the memory fraction to use for the cache
	 */
	public void addCache(final FragmentManager fm, final Context context,
			final String directory, final int size, final int fraction) {

		DiskCacheParams params = null;
		try {
			params = new DiskCacheParams(context, directory, size);
		} catch (final IOException e) {
		}

		mImageCache = ImageCache.getInstance(fm, params, fraction);
		new CacheAsyncTask().execute(MESSAGE_INIT_DISK_CACHE);
	}

	public void clearCache() {
		new CacheAsyncTask().execute(MESSAGE_CLEAR);
	}

	public void closeCache() {
		new CacheAsyncTask().execute(MESSAGE_CLOSE);
	}

	public void flushCache() {
		new CacheAsyncTask().execute(MESSAGE_FLUSH);
	}

	public void loadBitmapFromFile(final ImageView imageView, final File file,
			final int targetWidth, final int targetHeight) {
		// check for file
		if (file == null || !file.exists()) {
			return;
		}
		final String path = file.getAbsolutePath();

		// TODO make key a parameter
		final String key = new StringBuilder().append(path).append("_")
				.append(targetWidth).append("_").append(targetHeight)
				.toString();

		final BitmapDrawable drawable = mImageCache == null ? null
				: mImageCache.getBitmapDrawableFromMemCache(key);

		if (drawable != null) {

			// bitmap found in memory cache
			imageView.setImageDrawable(drawable);

		} else if (cancelPotentialWork(key, imageView)) {

			// instantiate the task
			final BitmapFileTask task = new BitmapFileTask(imageView, key,
					mResources, mImageCache, path);

			// set a loading indicator as background
			final AsyncDrawable placeHolder = new AsyncDrawable(mResources,
					mLoadingBitmap, task);
			imageView.setImageDrawable(placeHolder);

			// start the task with parameters
			final Integer[] params = { targetWidth, targetHeight };
			task.executeOnExecutor(AsyncTask.DUAL_THREAD_EXECUTOR, params);
		}
	}

	/**
	 * Load an image specified by the data parameter into an ImageView (override
	 * {@link ImageWorker#processBitmap(Object)} to define the processing
	 * logic). A memory and disk cache will be used if an {@link ImageCache} has
	 * been added using
	 * {@link ImageWorker#addImageCache(FragmentManager, DiskCacheParams)}. If
	 * the image is found in the memory cache, it is set immediately, otherwise
	 * an {@link AsyncTask} will be created to asynchronously load the bitmap.
	 * 
	 * @param resId
	 *            - the URL of the image to download
	 * @param imageView
	 *            - the ImageView to bind the downloaded image to
	 */
	public void loadImageFromResource(final ImageView imageView,
			final int resId, final int targetWidth, final int targetHeight) {
		if (resId == 0) {
			return;
		}

		// TODO make key a parameter
		final String key = new StringBuilder().append(resId).append("_")
				.append(targetWidth).append("_").append(targetHeight)
				.toString();

		final BitmapDrawable drawable = mImageCache == null ? null
				: mImageCache.getBitmapDrawableFromMemCache(key);

		if (drawable != null) {

			// bitmap found in memory cache
			imageView.setImageDrawable(drawable);

		} else if (cancelPotentialWork(key, imageView)) {

			final BitmapResourceTask task = new BitmapResourceTask(imageView,
					key, mResources, mImageCache, resId);

			// set a loading indicator as background
			final AsyncDrawable placeHolder = new AsyncDrawable(mResources,
					mLoadingBitmap, task);
			imageView.setImageDrawable(placeHolder);

			// start the task with parameters
			final Integer[] params = { targetWidth, targetHeight };
			task.executeOnExecutor(AsyncTask.DUAL_THREAD_EXECUTOR, params);
		}
	}

	// public void setExitTasksEarly(final boolean exitTasksEarly) {
	// mExitTasksEarly = exitTasksEarly;
	// setPauseWork(false);
	// }

	/**
	 * Set placeholder bitmap that shows when the the background thread is
	 * running.
	 * 
	 * @param bitmap
	 */
	public void setLoadingImage(final Bitmap bitmap) {
		mLoadingBitmap = bitmap;
	}

	/**
	 * Set placeholder bitmap that shows when the the background thread is
	 * running.
	 * 
	 * @param resId
	 */
	public void setLoadingImage(final int resId) {
		mLoadingBitmap = BitmapFactory.decodeResource(mResources, resId);
	}

	/**
	 * Pause any ongoing background work. This can be used as a temporary
	 * measure to improve performance. For example background work could be
	 * paused when a ListView or GridView is being scrolled using a
	 * {@link android.widget.AbsListView.OnScrollListener} to keep scrolling
	 * smooth.
	 * <p>
	 * If work is paused, be sure setPauseWork(false) is called again before
	 * your fragment or activity is destroyed (for example during
	 * {@link android.app.Activity#onPause()}), or there is a risk the
	 * background thread will never finish.
	 */
	// public void setPauseWork(final boolean pauseWork) {
	// synchronized (mPauseWorkLock) {
	// mPauseWork = pauseWork;
	// if (!mPauseWork) {
	// mPauseWorkLock.notifyAll();
	// }
	// }
	// }

	protected void clearCacheInternal() throws IOException {
		if (mImageCache != null) {
			mImageCache.clearCache();
		}
	}

	protected void closeCacheInternal() throws IOException {
		if (mImageCache != null) {
			mImageCache.close();
			mImageCache = null;
		}
	}

	protected void flushCacheInternal() throws IOException {
		if (mImageCache != null) {
			mImageCache.flush();
		}
	}

	/**
	 * @return The {@link ImageCache} object currently being used by this
	 *         ImageWorker.
	 */
	protected ImageCache getImageCache() {
		return mImageCache;
	}

	protected void initDiskCacheInternal() throws IOException {
		if (mImageCache != null) {
			mImageCache.initDiskCache();
		}
	}

	/**
	 * Cancels any pending work attached to the provided ImageView.
	 * 
	 * @param imageView
	 */
	public static void cancelWork(final ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
		if (bitmapWorkerTask != null) {
			bitmapWorkerTask.cancel(true);
		}
	}

	/**
	 * Returns true if the current work has been canceled or if there was no
	 * work in progress on this image view. Returns false if the work in
	 * progress deals with the same data. The work is not stopped in that case.
	 */
	static boolean cancelPotentialWork(final String key,
			final ImageView imageView) {

		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
		if (bitmapWorkerTask == null) {
			return true;
		}

		// cancel task
		final String bitmapData = bitmapWorkerTask.getKey();
		if (bitmapData == null || !bitmapData.equals(key)) {
			bitmapWorkerTask.cancel(true);
			return true;
		}

		// The same work is already in progress.
		return false;
	}

	/**
	 * Retrieves the currently active work task (if any) associated with this
	 * view. Returns null if there is no such task.
	 */
	protected static BitmapWorkerTask getBitmapWorkerTask(
			final ImageView imageView) {
		if (imageView == null) {
			return null;
		}

		final Drawable drawable = imageView.getDrawable();
		if (!(drawable instanceof AsyncDrawable)) {
			return null;
		}

		final AsyncDrawable placeHolder = (AsyncDrawable) drawable;
		return placeHolder.getBitmapWorkerTask();
	}
}