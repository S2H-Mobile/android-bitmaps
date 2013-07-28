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

import java.lang.ref.WeakReference;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.util.LruCache;
import android.widget.ImageView;

public class BitmapResourceTask extends BitmapBaseTask {

	private final WeakReference<LruCache<String, Bitmap>> mCacheReference;
	private final Resources mResources;

	public BitmapResourceTask(final Resources resources,
			final ImageView imageView, final LruCache<String, Bitmap> cache) {
		super(null, imageView);
		mResources = resources;
		mCacheReference = new WeakReference<LruCache<String, Bitmap>>(cache);
	}

	@Override
	protected Bitmap doInBackground(final Integer... params) {

		// evaluate the parameters
		final int resId = params[0];
		final int targetWidth = params[1];
		final int targetHeight = params[2];

		final Bitmap bitmap = decodeBitmapFromResource(mResources, resId,
				targetWidth, targetHeight);

		if (mCacheReference != null) {

			// we use the image resource id as the key for the cache entry
			BitmapResourceTask.addBitmapToCache(mCacheReference.get(),
					String.valueOf(resId), bitmap);
		}
		return bitmap;
	}

	@Override
	protected void onPostExecute(final Bitmap bitmap) {
		if (mViewReference != null && bitmap != null) {
			final ImageView imageView = mViewReference.get();
			if (imageView != null) {
				imageView.setImageBitmap(bitmap);
			}
		}
	}

	public static void renderBitmapFromResource(final int resId,
			final Resources resources, final ImageView imageView,
			final LruCache<String, Bitmap> cache, final int targetWidth,
			final int targetHeight) {

		// create the task
		final BitmapResourceTask task = new BitmapResourceTask(resources,
				imageView, cache);

		// start the task with parameters
		final Integer[] params = { resId, targetWidth, targetHeight };
		task.executeOnExecutor(AsyncTask.DUAL_THREAD_EXECUTOR, params);
	}

	/**
	 * Puts the bitmap in the cache.
	 * 
	 * @param cache
	 * @param key
	 * @param bitmap
	 */
	private static void addBitmapToCache(final LruCache<String, Bitmap> cache,
			final String key, final Bitmap bitmap) {
		if (cache != null) {
			if (cache.get(key) == null) {
				cache.put(key, bitmap);
			}
		}
	}

	/**
	 * Decodes a bitmap from a resource image file.
	 * 
	 * @param res
	 *            the package resources
	 * @param resId
	 *            the resource Id of the image file
	 * @param targetWidth
	 *            the width of the target bitmap
	 * @param targetHeight
	 *            the height of the target bitmap
	 * @return the decoded bitmap
	 */
	private static Bitmap decodeBitmapFromResource(final Resources res,
			final int resId, final int targetWidth, final int targetHeight) {
		final BitmapFactory.Options options = new BitmapFactory.Options();

		/*
		 * Read the dimensions of the source image prior to construction (and
		 * memory allocation) of the target bitmap.
		 */
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeResource(res, resId, options);

		// raw height and width of image
		final int imageWidth = options.outWidth;
		final int imageHeight = options.outHeight;

		// decode the image file into a bitmap
		options.inJustDecodeBounds = false;
		options.inSampleSize = BitmapBaseTask.calculateInSampleSize(
				imageHeight, imageWidth, targetHeight, targetWidth);
		options.inPurgeable = true;
		return BitmapFactory.decodeResource(res, resId, options);
	}
}