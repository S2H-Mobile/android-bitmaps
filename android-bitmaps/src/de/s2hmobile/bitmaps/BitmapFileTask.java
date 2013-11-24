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
import android.widget.ImageView;

public final class BitmapFileTask extends BitmapWorkerTask {

	private final String mPath;

	protected BitmapFileTask(final ImageView imageView, final String key,
			final Resources res, final ImageCache cache, final String path) {
		super(imageView, key, res, cache);
		mPath = path;
	}

	@Override
	protected Bitmap decodeBitmap(final int targetWidth, final int targetHeight) {
		final BitmapFactory.Options options = new BitmapFactory.Options();

		/*
		 * Read the dimensions of the source image prior to construction (and
		 * memory allocation) of the target bitmap.
		 */
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(mPath, options);

		// raw height and width of image
		final int imageWidth = options.outWidth;
		final int imageHeight = options.outHeight;

		options.inSampleSize = calculateInSampleSize(imageHeight, imageWidth,
				targetHeight, targetWidth);

		// If we're running on Honeycomb or newer, try to use inBitmap
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			addInBitmapOptions(options, mImageCache);
		}

		// decode the image file into a bitmap
		options.inJustDecodeBounds = false;
		options.inPurgeable = true;
		return BitmapFactory.decodeFile(mPath, options);
	}
}