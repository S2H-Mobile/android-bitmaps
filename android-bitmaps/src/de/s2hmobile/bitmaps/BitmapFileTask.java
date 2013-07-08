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

import java.io.File;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

public final class BitmapFileTask extends BitmapBaseTask {

	private final String mPath;

	private BitmapFileTask(String path, OnBitmapRenderedListener listener,
			ImageView imageView) {
		super(listener, imageView);
		mPath = path;
	}

	@Override
	protected Bitmap doInBackground(Integer... params) {

		// evaluate the parameters
		final int targetWidth = params[0];
		final int targetHeight = params[1];

		// decode the bitmap from the file
		return decodeBitmapFromFile(mPath, targetWidth, targetHeight);
	}

	/**
	 * Executes a {@link BitmapFileTask} to render a bitmap from the given file.
	 * 
	 * @param fileName
	 *            - the image file, for example a jpeg
	 * @param imageView
	 *            - the {@link ImageView} displaying the target bitmap
	 * @param targetWidth
	 *            - the width of the target bitmap
	 * @param targetHeight
	 *            - the height of the target bitmap
	 */
	public static void renderBitmapFromFile(File file,
			OnBitmapRenderedListener listener, ImageView imageView,
			final int targetWidth, final int targetHeight) {

		// check for file
		if (file != null && file.exists()) {

			// instantiate the task
			final BitmapFileTask task = new BitmapFileTask(
					file.getAbsolutePath(), listener, imageView);

			// start the task with parameter array
			final Integer[] params = { targetWidth, targetHeight };
			task.executeOnExecutor(AsyncTask.DUAL_THREAD_EXECUTOR, params);
		}
	}

	private static Bitmap decodeBitmapFromFile(final String path,
			final int targetWidth, final int targetHeight) {
		final BitmapFactory.Options options = new BitmapFactory.Options();

		/*
		 * Read the dimensions of the source image prior to construction (and
		 * memory allocation) of the target bitmap.
		 */
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(path, options);

		// raw height and width of image
		final int imageWidth = options.outWidth;
		final int imageHeight = options.outHeight;

		// decode the image file into a bitmap
		options.inJustDecodeBounds = false;
		options.inSampleSize = calculateInSampleSize(imageHeight, imageWidth,
				targetHeight, targetWidth);
		options.inPurgeable = true;
		return BitmapFactory.decodeFile(path, options);
	}
}