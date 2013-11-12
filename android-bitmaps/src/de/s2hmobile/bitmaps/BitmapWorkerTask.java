package de.s2hmobile.bitmaps;

import java.io.IOException;
import java.lang.ref.WeakReference;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.widget.ImageView;
import de.s2hmobile.bitmaps.framework.AsyncTask;

/**
 * The actual AsyncTask that will asynchronously process the image.
 */
class BitmapWorkerTask extends AsyncTask<Integer, Void, BitmapDrawable> {
	final ImageCache mImageCache;
	final String mKey;
	final Resources mResources;

	private final WeakReference<ImageView> mViewReference;

	public BitmapWorkerTask(final ImageView imageView, final String key,
			final Resources res, final ImageCache cache) {
		mViewReference = new WeakReference<ImageView>(imageView);
		mKey = key;
		mResources = res;
		mImageCache = cache;
	}

	@Override
	protected BitmapDrawable doInBackground(final Integer... params) {

		// TODO params width height

		// wait here if work is paused and the task is not cancelled
		// synchronized (mPauseWorkLock) {
		// while (mPauseWork && !isCancelled()) {
		// try {
		// mPauseWorkLock.wait();
		// } catch (final InterruptedException e) {
		// }
		// }
		// }

		/*
		 * If the image cache is available and this task has not been cancelled
		 * by another thread and the ImageView that was originally bound to this
		 * task is still bound back to this task.
		 * 
		 * We could also evaluate the "exit early" flag is not set then try and
		 * fetch the bitmap from the cache.
		 */
		Bitmap bitmap = null;
		if (mImageCache != null && !isCancelled()
				&& getAttachedImageView() != null) {
			try {
				bitmap = mImageCache.getBitmapFromDiskCache(mKey);
			} catch (IOException e) {
			}
		}

		/*
		 * If the bitmap was not found in the cache and this task has not been
		 * cancelled by another thread and the ImageView that was originally
		 * bound to this task is still bound back to this task and our
		 * "exit early" flag is not set, then call the main process method (as
		 * implemented by a subclass)
		 */
		if (bitmap == null && !isCancelled() && getAttachedImageView() != null) {
			// bitmap = processBitmap(mKey);

			// TODO this is decodeBitmapFromResource(); from derived classes
		}

		if (bitmap == null) {
			return null;
		}

		/*
		 * If the bitmap was processed and the image cache is available, then
		 * add the processed bitmap to the cache for future use. Note we don't
		 * check if the task was cancelled here. If it was, and the thread is
		 * still running, we may as well add the processed bitmap to our cache
		 * as it might be used again in the future.
		 * 
		 * On new platforms we wrap the bitmap in a standard BitmapDrawable. On
		 * Gingerbread and below we wrap in a RecyclingBitmapDrawable which will
		 * recycle automagically.
		 */
		final BitmapDrawable drawable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ? new BitmapDrawable(
				mResources, bitmap) : new RecyclingBitmapDrawable(mResources,
				bitmap);

		// add the drawable to the cache
		if (mImageCache != null) {
			try {
				mImageCache.addToCache(mKey, drawable);
			} catch (IOException e) {
			}
		}
		return drawable;
	}

	// @Override
	// protected void onCancelled(final BitmapDrawable value) {
	// super.onCancelled(value);
	// synchronized (mPauseWorkLock) {
	// mPauseWorkLock.notifyAll();
	// }
	// }

	/**
	 * Once the image is processed, associates it to the imageView
	 */
	@Override
	protected void onPostExecute(BitmapDrawable result) {

		/*
		 * If cancel was called on this task or the "exit early" flag is set
		 * then we're done. if (isCancelled() || mExitTasksEarly) {
		 */
		if (isCancelled()) {
			result = null;
		}

		if (result != null) {
			final ImageView imageView = getAttachedImageView();
			if (imageView != null) {
				imageView.setImageDrawable(result);
			}
		}
	}

	/**
	 * Returns the ImageView associated with this task as long as the
	 * ImageView's task still points to this task as well. Returns null
	 * otherwise.
	 */
	private ImageView getAttachedImageView() {
		final ImageView imageView = mViewReference.get();
		final BitmapWorkerTask bitmapWorkerTask = ImageLoader
				.getBitmapWorkerTask(imageView);
		if (this == bitmapWorkerTask) {
			return imageView;
		}
		return null;
	}
}