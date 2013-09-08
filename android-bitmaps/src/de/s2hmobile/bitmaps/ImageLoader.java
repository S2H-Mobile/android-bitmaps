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

import java.lang.ref.WeakReference;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.widget.ImageView;
import de.s2hmobile.bitmaps.ImageCache.ImageCacheParams;
import de.s2hmobile.bitmaps.framework.AsyncTask;

/**
 * This class wraps up completing some arbitrary long running work when loading
 * a bitmap to an ImageView. It handles things like using a memory and disk
 * cache, running the work in a background thread and setting a placeholder
 * image.
 */
public abstract class ImageLoader {

	/**
	 * A custom Drawable that will be attached to the imageView while the work
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

	/**
	 * The actual AsyncTask that will asynchronously process the image.
	 */
	private class BitmapWorkerTask extends
			AsyncTask<Integer, Void, BitmapDrawable> {

		int resId = 0;

		private final WeakReference<ImageView> imageViewReference;

		public BitmapWorkerTask(final ImageView imageView) {
			imageViewReference = new WeakReference<ImageView>(imageView);
		}

		@Override
		protected BitmapDrawable doInBackground(final Integer... params) {
			resId = params[0];
			Bitmap bitmap = null;
			BitmapDrawable drawable = null;

			// wait here if work is paused and the task is not cancelled
			synchronized (mPauseWorkLock) {
				while (mPauseWork && !isCancelled()) {
					try {
						mPauseWorkLock.wait();
					} catch (final InterruptedException e) {
					}
				}
			}

			/*
			 * If the image cache is available and this task has not been
			 * cancelled by another thread and the ImageView that was originally
			 * bound to this task is still bound back to this task and our
			 * "exit early" flag is not set then try and fetch the bitmap from
			 * the cache.
			 */
			if (mImageCache != null && !isCancelled()
					&& getAttachedImageView() != null && !mExitTasksEarly) {
				bitmap = mImageCache.getBitmapFromDiskCache(resId);
			}

			/*
			 * If the bitmap was not found in the cache and this task has not
			 * been cancelled by another thread and the ImageView that was
			 * originally bound to this task is still bound back to this task
			 * and our "exit early" flag is not set, then call the main process
			 * method (as implemented by a subclass)
			 */
			if (bitmap == null && !isCancelled()
					&& getAttachedImageView() != null && !mExitTasksEarly) {
				bitmap = ImageLoader.this.processBitmap(resId);
			}

			/*
			 * If the bitmap was processed and the image cache is available,
			 * then add the processed bitmap to the cache for future use. Note
			 * we don't check if the task was cancelled here. If it was, and the
			 * thread is still running, we may as well add the processed bitmap
			 * to our cache as it might be used again in the future.
			 */
			if (bitmap != null) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {

					// wrap in a standard BitmapDrawable
					drawable = new BitmapDrawable(mResources, bitmap);
				} else {

					// wrap in a RecyclingBitmapDrawable which will recycle
					// automagically
					drawable = new RecyclingBitmapDrawable(mResources, bitmap);
				}

				if (mImageCache != null) {
					mImageCache.addBitmapToCache(resId, drawable);
				}
			}
			return drawable;
		}

		/**
		 * Returns the ImageView associated with this task as long as the
		 * ImageView's task still points to this task as well. Returns null
		 * otherwise.
		 */
		private ImageView getAttachedImageView() {
			final ImageView imageView = imageViewReference.get();
			final BitmapWorkerTask bitmapWorkerTask = ImageLoader
					.getBitmapWorkerTask(imageView);

			if (this == bitmapWorkerTask) {
				return imageView;
			}

			return null;
		}

		@Override
		protected void onCancelled(final BitmapDrawable value) {
			super.onCancelled(value);
			synchronized (mPauseWorkLock) {
				mPauseWorkLock.notifyAll();
			}
		}

		/**
		 * Once the image is processed, associates it to the imageView
		 */
		@Override
		protected void onPostExecute(BitmapDrawable result) {

			/*
			 * If cancel was called on this task or the "exit early" flag is set
			 * then we're done.
			 */
			if (isCancelled() || mExitTasksEarly) {
				result = null;
			}

			final ImageView imageView = BitmapWorkerTask.this
					.getAttachedImageView();
			if (result != null && imageView != null) {
				ImageLoader.this.setImageDrawable(imageView, result);
			}
		}
	}

	protected class CacheAsyncTask extends AsyncTask<Integer, Void, Void> {

		@Override
		protected Void doInBackground(final Integer... params) {
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
			return null;
		}
	}

	private static final int MESSAGE_CLEAR = 0;

	private static final int MESSAGE_CLOSE = 3;
	private static final int MESSAGE_FLUSH = 2;
	private static final int MESSAGE_INIT_DISK_CACHE = 1;

	/**
	 * Returns true if the current work has been canceled or if there was no
	 * work in progress on this image view. Returns false if the work in
	 * progress deals with the same data. The work is not stopped in that case.
	 */
	public static boolean cancelPotentialWork(final Object data,
			final ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

		if (bitmapWorkerTask != null) {
			final Object bitmapData = bitmapWorkerTask.resId;

			if (bitmapData == null || !bitmapData.equals(data)) {
				bitmapWorkerTask.cancel(true);
			} else {

				// The same work is already in progress.
				return false;
			}
		}
		return true;
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
	 * @param imageView
	 *            Any imageView
	 * @return Retrieve the currently active work task (if any) associated with
	 *         this imageView. null if there is no such task.
	 */
	private static BitmapWorkerTask getBitmapWorkerTask(
			final ImageView imageView) {
		if (imageView != null) {
			final Drawable drawable = imageView.getDrawable();
			if (drawable instanceof AsyncDrawable) {
				final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
				return asyncDrawable.getBitmapWorkerTask();
			}
		}
		return null;
	}

	private boolean mExitTasksEarly = false;

	private ImageCache mImageCache = null;

	private ImageCache.ImageCacheParams mImageCacheParams = null;

	private Bitmap mLoadingBitmap = null;

	protected boolean mPauseWork = false;

	private final Object mPauseWorkLock = new Object();

	protected final Resources mResources;

	protected ImageLoader(final Context context) {
		mResources = context.getResources();
	}

	/**
	 * Adds an {@link ImageCache} to this {@link ImageWorker} to handle disk and
	 * memory bitmap caching.
	 * 
	 * @param activity
	 * @param diskCacheDirectoryName
	 *            See
	 *            {@link ImageCache.ImageCacheParams#ImageCacheParams(Context, String)}
	 *            .
	 */
	public void addImageCache(final FragmentActivity activity,
			final String diskCacheDirectoryName) {
		mImageCacheParams = new ImageCache.ImageCacheParams(activity,
				diskCacheDirectoryName);
		mImageCache = ImageCache.getInstance(
				activity.getSupportFragmentManager(), mImageCacheParams);
		new CacheAsyncTask().execute(MESSAGE_INIT_DISK_CACHE);
	}

	/**
	 * Adds an {@link ImageCache} to this {@link ImageWorker} to handle disk and
	 * memory bitmap caching.
	 * 
	 * @param fragmentManager
	 * @param cacheParams
	 *            The cache parameters to use for the image cache.
	 */
	public void addImageCache(final FragmentManager fragmentManager,
			final ImageCache.ImageCacheParams cacheParams) {
		mImageCacheParams = cacheParams;
		mImageCache = ImageCache
				.getInstance(fragmentManager, mImageCacheParams);
		new CacheAsyncTask().execute(MESSAGE_INIT_DISK_CACHE);
	}

	public void clearCache() {
		new CacheAsyncTask().execute(MESSAGE_CLEAR);
	}

	protected void clearCacheInternal() {
		if (mImageCache != null) {
			mImageCache.clearCache();
		}
	}

	public void closeCache() {
		new CacheAsyncTask().execute(MESSAGE_CLOSE);
	}

	protected void closeCacheInternal() {
		if (mImageCache != null) {
			mImageCache.close();
			mImageCache = null;
		}
	}

	public void flushCache() {
		new CacheAsyncTask().execute(MESSAGE_FLUSH);
	}

	protected void flushCacheInternal() {
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

	protected void initDiskCacheInternal() {
		if (mImageCache != null) {
			mImageCache.initDiskCache();
		}
	}

	/**
	 * Load an image specified by the data parameter into an ImageView (override
	 * {@link ImageWorker#processBitmap(Object)} to define the processing
	 * logic). A memory and disk cache will be used if an {@link ImageCache} has
	 * been added using
	 * {@link ImageWorker#addImageCache(FragmentManager, ImageCacheParams)}. If
	 * the image is found in the memory cache, it is set immediately, otherwise
	 * an {@link AsyncTask} will be created to asynchronously load the bitmap.
	 * 
	 * @param resId
	 *            - the URL of the image to download
	 * @param imageView
	 *            - the ImageView to bind the downloaded image to
	 */
	public void loadImage(final int resId, final ImageView imageView) {
		if (resId == 0) {
			return;
		}

		BitmapDrawable value = null;

		if (mImageCache != null) {
			value = mImageCache.getBitmapFromMemCache(resId);
		}

		if (value != null) {

			// bitmap found in memory cache
			imageView.setImageDrawable(value);

		} else if (ImageLoader.cancelPotentialWork(resId, imageView)) {

			final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
			final AsyncDrawable asyncDrawable = new AsyncDrawable(mResources,
					mLoadingBitmap, task);
			imageView.setImageDrawable(asyncDrawable);

			task.executeOnExecutor(AsyncTask.DUAL_THREAD_EXECUTOR, resId);
		}
	}

	/**
	 * Subclasses should override this to define any processing or work that
	 * must happen to produce the final bitmap. This will be executed in a
	 * background thread and be long running. For example, you could resize a
	 * large bitmap here, or pull down an image from the network.
	 * 
	 * @param data
	 *            The data to identify which image to process, as provided by
	 *            {@link ImageWorker#loadImage(Object, ImageView)}
	 * @return The processed bitmap
	 */
	protected abstract Bitmap processBitmap(final int resId);

	public void setExitTasksEarly(final boolean exitTasksEarly) {
		mExitTasksEarly = exitTasksEarly;
		setPauseWork(false);
	}

	/**
	 * Called when the processing is complete and the final drawable should be
	 * set on the ImageView.
	 * 
	 * @param imageView
	 * @param drawable
	 */
	private void setImageDrawable(final ImageView imageView,
			final Drawable drawable) {
		imageView.setImageDrawable(drawable);

	}

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
	public void setPauseWork(final boolean pauseWork) {
		synchronized (mPauseWorkLock) {
			mPauseWork = pauseWork;
			if (!mPauseWork) {
				mPauseWorkLock.notifyAll();
			}
		}
	}
}