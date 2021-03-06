package de.s2hmobile.bitmaps;

import java.io.IOException;
import java.lang.ref.WeakReference;

import android.annotation.TargetApi;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.widget.ImageView;
import de.s2hmobile.bitmaps.framework.AsyncTask;

/**
 * The actual AsyncTask that will asynchronously process the image.
 */
abstract class BitmapWorkerTask extends
		AsyncTask<Integer, Void, BitmapDrawable> {
	protected final ImageCache mImageCache;
	protected final Resources mResources;
	private final String mKey;

	private final WeakReference<ImageView> mViewReference;

	protected BitmapWorkerTask(final ImageView imageView, final String key,
			final Resources res, final ImageCache cache) {
		mViewReference = new WeakReference<ImageView>(imageView);
		mKey = key;
		mResources = res;
		mImageCache = cache;
	}

	protected abstract Bitmap decodeBitmap(final int targetWidth,
			final int targetHeight);

	@Override
	protected BitmapDrawable doInBackground(final Integer... params) {
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
			} catch (final IOException e) {
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

			// evaluate the parameters
			final int targetWidth = params[0];
			final int targetHeight = params[1];

			bitmap = decodeBitmap(targetWidth, targetHeight);
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
		addToCache(drawable);

		return drawable;
	}

	private void addToCache(final BitmapDrawable drawable) {
		if (mImageCache == null) {
			return;
		}

		try {
			mImageCache.addToCache(mKey, drawable);
		} catch (final IOException e) {
		}
	}

	// @Override
	// protected void onCancelled(final BitmapDrawable value) {
	// super.onCancelled(value);
	// synchronized (mPauseWorkLock) {
	// mPauseWorkLock.notifyAll();
	// }
	// }

	protected String getKey() {
		return mKey;
	}

	/**
	 * Once the image is processed, set it as the image view drawable. If cancel
	 * was called on this task or the "exit early" flag is set then we're done.
	 * 
	 * <p>
	 * <code>if (isCancelled() || mExitTasksEarly) </code>
	 */
	@Override
	protected void onPostExecute(final BitmapDrawable result) {
		if (isCancelled()) {
			return;
		}

		if (result == null) {
			return;
		}

		final ImageView imageView = getAttachedImageView();
		if (imageView == null) {
			return;
		}

		imageView.setImageDrawable(result);
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

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	protected static void addInBitmapOptions(
			final BitmapFactory.Options options, final ImageCache cache) {

		/*
		 * This works with mutable bitmaps so force the decoder to return
		 * mutable bitmaps.
		 */
		options.inMutable = true;

		if (cache == null) {
			return;
		}

		// Try and find a bitmap to use for inBitmap
		final Bitmap bitmap = cache.getBitmapFromReusableSet(options);
		if (bitmap == null) {
			return;
		}

		options.inBitmap = bitmap;
	}

	/**
	 * Determines the factor the source image is scaled down by. The resulting
	 * sample size is to be used in a {@link BitmapFactory.Options} object when
	 * decoding bitmaps with {@link BitmapFactory}.
	 * 
	 * <p>
	 * Compares the dimensions of source and target image. Calculates the
	 * smallest sample size that will result in the final decoded bitmap having
	 * a width and height equal to or larger than the requested width and
	 * height. Determines the sample size by calculating the power of two that
	 * is closest to the ratio.
	 * 
	 * @param imageHeight
	 *            - height of original image
	 * @param imageWidth
	 *            - width of original image
	 * @param reqHeight
	 *            - requested height of target image
	 * @param reqWidth
	 *            - requested width of target image
	 * 
	 * @return The scale factor as a power of two.
	 */
	protected static int calculateInSampleSize(final int imageHeight,
			final int imageWidth, final int reqHeight, final int reqWidth) {

		// initialize the size ratio between source and target
		int ratio = 1;

		/*
		 * Check if the requested size of the target bitmap is positive to avoid
		 * dividing by zero. Check if the original image is actually larger than
		 * the target image.
		 */
		if (reqWidth > 0 && reqHeight > 0
				&& (imageHeight > reqHeight || imageWidth > reqWidth)) {

			// calculate height and width ratios
			final int heightRatio = Math.round((float) imageHeight
					/ (float) reqHeight);
			final int widthRatio = Math.round((float) imageWidth
					/ (float) reqWidth);

			/*
			 * Don't scale down too much, so choose the smallest ratio. This
			 * will guarantee a final image with both dimensions larger than or
			 * equal to the requested ones.
			 */
			ratio = Math.min(heightRatio, widthRatio);
		}

		/*
		 * Determine the power of two that is closest to and smaller than the
		 * scale factor.
		 */
		int inSampleSize = 2;
		while (inSampleSize <= ratio) {
			inSampleSize *= 2;
		}
		return inSampleSize;
	}
}