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

import android.graphics.Bitmap;
import android.widget.ImageView;

/**
 * Base class for bitmap tasks. Holds the callback to the listener and a weak
 * reference to the image view.
 * 
 * @author Stephan Hoehne
 * 
 */
abstract class BitmapBaseTask extends AsyncTask<Integer, Void, Bitmap> {

	protected final WeakReference<ImageView> mViewReference;

	private final OnBitmapRenderedListener mCallback;

	protected BitmapBaseTask(final OnBitmapRenderedListener listener,
			final ImageView imageView) {
		mCallback = listener;
		mViewReference = new WeakReference<ImageView>(imageView);
	}

	/**
	 * Returns the scaled bitmap to the caller. May return null, in order for
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
				mCallback.onBitmapRendered(imageView, bitmap);
			}
		}
	}

	/**
	 * Determines the factor the source image is scaled down by. Compares the
	 * dimensions of source and target image and calculates the smallest ratio.
	 * Determines the scale factor by calculating the power of two that is
	 * closest to this ratio.
	 * 
	 * @param imageWidth
	 *            - width of original image
	 * @param imageHeight
	 *            - height of original image
	 * @param reqWidth
	 *            - requested width of target image
	 * @param reqHeight
	 *            - requested height of target image
	 * @return The scale factor, a power of two.
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
		if (reqWidth != 0 && reqHeight != 0
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
		int temp = 2;
		while (temp <= ratio) {
			temp *= 2;
		}
		final int scaleFactor = temp / 2;

		// TODO remove log statement in production
		android.util.Log.i("BitmapBaseTask", "The scale factor is "
				+ scaleFactor + ".");

		return scaleFactor;
	}
}