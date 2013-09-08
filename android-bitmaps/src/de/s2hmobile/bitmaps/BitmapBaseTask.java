/*
 * Copyright (C) 2013, S2H Mobile
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

import de.s2hmobile.bitmaps.framework.AsyncTask;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

/**
 * Base class for bitmap tasks. Holds the callback to the listener and a weak
 * reference to the image view.
 * 
 * <p>
 * Derived classes store the data needed to access the image resource, for
 * example a url, a file system path or a resource Id.
 * 
 * @author Stephan Hoehne
 * 
 */
abstract class BitmapBaseTask extends AsyncTask<Integer, Void, Bitmap> {

	private final OnBitmapRenderedListener mCallback;

	private final WeakReference<ImageView> mViewReference;

	/**
	 * Here we construct the weak references to the image view and the memory
	 * cache. We use them in {@link BitmapBaseTask#onPostExecute(Bitmap)}.
	 * 
	 * @param callback
	 * @param imageView
	 */
	protected BitmapBaseTask(final OnBitmapRenderedListener callback,
			final ImageView imageView) {
		mCallback = callback;
		mViewReference = new WeakReference<ImageView>(imageView);
	}

	protected abstract String createKey(final Bitmap bitmap);

	/**
	 * Returns the rescaled bitmap to the caller. The bitmap is tagged with a
	 * key string, as defined by derived classes. May return null, in order for
	 * listeners to update their UI accordingly, e.g. with error messages.
	 */
	@Override
	protected void onPostExecute(Bitmap bitmap) {
		if (super.isCancelled()) {
			bitmap = null;
		}

		if (mViewReference != null && mCallback != null) {
			final ImageView imageView = mViewReference.get();
			if (imageView != null) {
				// final String key = createKey(bitmap);
				// final TaggedBitmap result = new TaggedBitmap(bitmap, key);
				mCallback.onBitmapRendered(imageView, bitmap);
			}
		}
	}

	/**
	 * Determines the factor the source image is scaled down by. The resulting
	 * sample size is to be used in a {@link BitmapFactory.Options} object when
	 * decoding bitmaps with {@link BitmapFactory}.
	 * 
	 * Compares the dimensions of source and target image and calculates the
	 * smallest ratio.
	 * 
	 * Calculates the smallest sample size that will result in the final decoded
	 * bitmap having a width and height equal to or larger than the requested
	 * width and height.
	 * 
	 * Determines the scale factor by calculating the power of two that is
	 * closest to this ratio.
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

			// TODO remove log statement in production
			android.util.Log.i("BitmapBaseTask", "ratio = " + ratio);
		}

		// This offers some additional logic in case the image has a strange
		// aspect ratio. For example, a panorama may have a much larger
		// width than height. In these cases the total pixels might still
		// end up being too large to fit comfortably in memory, so we should
		// be more aggressive with sample down the image (=larger
		// inSampleSize).
		final float totalPixels = imageWidth * imageHeight;

		// Anything more than 2x the requested pixels we'll sample down
		// further
		final float totalRequestedPixelsCap = reqWidth * reqHeight * 2;

		while (totalPixels / (ratio * ratio) > totalRequestedPixelsCap) {
			ratio++;
		}

		// TODO remove log statement in production
		android.util.Log.i("BitmapBaseTask", "ratio = " + ratio);

		/*
		 * Determine the power of two that is closest to and smaller than the
		 * scale factor.
		 */
		int temp = 2;
		while (temp <= ratio) {
			temp *= 2;
		}
		final int inSampleSize = temp / 2;

		// TODO remove log statement in production
		android.util.Log.i("BitmapBaseTask", "The scale factor is "
				+ inSampleSize + ".");

		return inSampleSize;
	}
}