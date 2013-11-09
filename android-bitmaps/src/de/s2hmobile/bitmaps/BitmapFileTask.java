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
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import de.s2hmobile.bitmaps.framework.AsyncTask;

public final class BitmapFileTask extends BitmapBaseTask {

	private final String mPath;

	private BitmapFileTask(final String path,
			final OnBitmapRenderedListener listener) {
		super(listener);
		mPath = path;
	}

	@Override
	protected String createKey(final Bitmap bitmap) {
		final int width = bitmap.getWidth();
		final int height = bitmap.getHeight();

		final String source = new StringBuilder().append(mPath).append("_")
				.append(width).append("_").append(height).toString();

		// TODO remove log statement
		android.util.Log.i("BitmapFetchTask", "source for MD5 is " + source);

		try {
			final MessageDigest digester = MessageDigest.getInstance("MD5");
			digester.update(source.getBytes(), 0, source.length());
			final byte[] magnitude = digester.digest();
			final BigInteger bigInt = new BigInteger(1, magnitude);
			final String result = bigInt.toString(16);

			// TODO remove log statement
			android.util.Log.i("BitmapFetchTask", "resulting MD5 key is "
					+ result);

			return result;
		} catch (final NoSuchAlgorithmException e) {
			return null;
		}

	}

	@Override
	protected Bitmap doInBackground(final Integer... params) {

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
	public static void renderBitmapFromFile(final File file,
			final OnBitmapRenderedListener listener, final int targetWidth,
			final int targetHeight) {

		// check for file
		if (file != null && file.exists()) {

			// instantiate the task
			final BitmapFileTask task = new BitmapFileTask(
					file.getAbsolutePath(), listener);

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
		options.inSampleSize = BitmapBaseTask.calculateInSampleSize(
				imageHeight, imageWidth, targetHeight, targetWidth);
		options.inPurgeable = true;
		return BitmapFactory.decodeFile(path, options);
	}
}