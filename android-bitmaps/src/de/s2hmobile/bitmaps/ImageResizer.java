/*
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

import java.io.FileDescriptor;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

/**
 * A simple subclass of {@link ImageLoader} that resizes images from resources
 * given a target width and height. Useful for when the input images might be
 * too large to simply load directly into memory.
 */
public class ImageResizer extends ImageLoader {

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private static void addInBitmapOptions(final BitmapFactory.Options options,
			final ImageCache cache) {

		// inBitmap only works with mutable bitmaps so force the decoder to
		// return mutable bitmaps.
		options.inMutable = true;

		if (cache != null) {
			// Try and find a bitmap to use for inBitmap
			final Bitmap inBitmap = cache.getBitmapFromReusableSet(options);

			if (inBitmap != null) {
				if (BuildConfig.DEBUG) {
					Log.i("ImageResizer", "Found bitmap to use for inBitmap");
				}
				options.inBitmap = inBitmap;
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
	 * @return The sample size as a power of two.
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

	/**
	 * Decode and sample down a bitmap from a file input stream to the requested
	 * width and height.
	 * 
	 * @param fileDescriptor
	 *            The file descriptor to read from
	 * @param reqWidth
	 *            The requested width of the resulting bitmap
	 * @param reqHeight
	 *            The requested height of the resulting bitmap
	 * @param cache
	 *            The ImageCache used to find candidate bitmaps for use with
	 *            inBitmap
	 * @return A bitmap sampled down from the original with the same aspect
	 *         ratio and dimensions that are equal to or greater than the
	 *         requested width and height
	 */
	public static Bitmap decodeSampledBitmapFromDescriptor(
			final FileDescriptor fileDescriptor, final int reqHeight,
			final int reqWidth, final ImageCache cache) {

		// First decode with inJustDecodeBounds=true to check dimensions
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);

		// Calculate inSampleSize
		options.inSampleSize = calculateInSampleSize(options.outHeight,
				options.outWidth, reqWidth, reqHeight);

		// Decode bitmap with inSampleSize set
		options.inJustDecodeBounds = false;

		// If we're running on Honeycomb or newer, try to use inBitmap
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			addInBitmapOptions(options, cache);
		}

		return BitmapFactory
				.decodeFileDescriptor(fileDescriptor, null, options);
	}

	/**
	 * Decode and sample down a bitmap from a file to the requested width and
	 * height.
	 * 
	 * @param path
	 *            The full path of the file to decode
	 * @param reqWidth
	 *            The requested width of the resulting bitmap
	 * @param reqHeight
	 *            The requested height of the resulting bitmap
	 * @param cache
	 *            The ImageCache used to find candidate bitmaps for use with
	 *            inBitmap
	 * @return A bitmap sampled down from the original with the same aspect
	 *         ratio and dimensions that are equal to or greater than the
	 *         requested width and height
	 */
	public static Bitmap decodeSampledBitmapFromFile(final String path,
			final int reqWidth, final int reqHeight, final ImageCache cache) {

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

		options.inSampleSize = calculateInSampleSize(imageHeight, imageWidth,
				reqHeight, reqWidth);

		// If we're running on Honeycomb or newer, try to use inBitmap
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			addInBitmapOptions(options, cache);
		}

		// decode the image file into a bitmap
		options.inJustDecodeBounds = false;
		options.inPurgeable = true;
		return BitmapFactory.decodeFile(path, options);
	}

	/**
	 * Decode and sample down a bitmap from resources to the requested width and
	 * height.
	 * 
	 * @param res
	 *            The resources object containing the image data
	 * @param resId
	 *            The resource id of the image data
	 * @param reqWidth
	 *            The requested width of the resulting bitmap
	 * @param reqHeight
	 *            The requested height of the resulting bitmap
	 * @param cache
	 *            The ImageCache used to find candidate bitmaps for use with
	 *            inBitmap
	 * @return A bitmap sampled down from the original with the same aspect
	 *         ratio and dimensions that are equal to or greater than the
	 *         requested width and height
	 */
	public static Bitmap decodeSampledBitmapFromResource(final Resources res,
			final int resId, final int reqWidth, final int reqHeight,
			final ImageCache cache) {

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

		options.inSampleSize = calculateInSampleSize(imageHeight, imageWidth,
				reqHeight, reqWidth);

		// If we're running on Honeycomb or newer, try to use inBitmap
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			addInBitmapOptions(options, cache);
		}

		// decode the image file into a bitmap
		options.inJustDecodeBounds = false;
		options.inPurgeable = true;
		return BitmapFactory.decodeResource(res, resId, options);
	}

	// TODO maybe let dimensions be reset
	protected final int mImageHeight;

	protected final int mImageWidth;

	public ImageResizer(final Context context, final int imageWidth,
			final int imageHeight) {
		super(context);
		mImageWidth = imageWidth;
		mImageHeight = imageHeight;
	}

	/**
	 * The main processing method. This happens in a background task. In this
	 * case we are just sampling down the bitmap and returning it from a
	 * resource.
	 * 
	 * @param resId
	 * @return
	 */
	@Override
	protected Bitmap processBitmap(final int resId) {
		return decodeSampledBitmapFromResource(mResources, resId, mImageWidth,
				mImageHeight, getImageCache());
	}
}
