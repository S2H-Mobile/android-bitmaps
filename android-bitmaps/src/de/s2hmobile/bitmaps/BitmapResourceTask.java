/*
 * Copyright (C) 2012 - 2013, S2H Mobile
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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

public class BitmapResourceTask extends BitmapBaseTask {

	private final int mResId;
	private final Resources mResources;

	private BitmapResourceTask(final OnBitmapRenderedListener callback,
			final Resources resources, final int resId, final ImageCache cache) {
		super(callback, cache);
		mResources = resources;
		mResId = resId;
	}

	@Override
	protected String createKey() {
		return String.valueOf(mResId);
		// final int width = bitmap.getWidth();
		// final int height = bitmap.getHeight();
		//
		// final String result = new StringBuilder().append(mResId).append("_")
		// .append(width).append("_").append(height).toString();
		//
		// return result;
	}

	@Override
	protected Bitmap doInBackground(final Integer... params) {

		// evaluate the parameters
		final int targetWidth = params[0];
		final int targetHeight = params[1];

		// TODO hier zum Cache hinzufuegen

		return decodeBitmapFromResource(targetWidth, targetHeight);
	}

	// TODO das ist der oeffentliche Zugang
	// public static void renderBitmapFromResource(final Resources resources,
	// final int resId, final OnBitmapRenderedListener callback,
	// final int targetWidth, final int targetHeight, final ImageCache cache) {
	//
	// // create the task
	// final BitmapResourceTask task = new BitmapResourceTask(callback,
	// resources, resId, cache);
	//
	// // start the task with parameters
	// final Integer[] params = { targetWidth, targetHeight };
	// task.executeOnExecutor(AsyncTask.DUAL_THREAD_EXECUTOR, params);
	// }

	/**
	 * Decodes a bitmap from a resource image file.
	 * 
	 * @param res
	 *            - the package resources
	 * @param mKey
	 *            - the resource Id of the image file
	 * @param targetWidth
	 *            - the width of the target bitmap
	 * @param targetHeight
	 *            - the height of the target bitmap
	 * @return The decoded bitmap, scaled to the target dimensions.
	 */
	private Bitmap decodeBitmapFromResource(final int targetWidth,
			final int targetHeight) {
		final BitmapFactory.Options options = new BitmapFactory.Options();

		/*
		 * Read the dimensions of the source image prior to construction (and
		 * memory allocation) of the target bitmap.
		 */
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeResource(mResources, mResId, options);

		// raw height and width of image
		final int imageWidth = options.outWidth;
		final int imageHeight = options.outHeight;

		options.inSampleSize = BitmapBaseTask.calculateInSampleSize(
				imageHeight, imageWidth, targetHeight, targetWidth);

		// If we're running on Honeycomb or newer, try to use inBitmap
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			addInBitmapOptions(options, mCache);
		}

		// decode the image file into a bitmap
		options.inJustDecodeBounds = false;
		options.inPurgeable = true;
		return BitmapFactory.decodeResource(mResources, mResId, options);
	}
}