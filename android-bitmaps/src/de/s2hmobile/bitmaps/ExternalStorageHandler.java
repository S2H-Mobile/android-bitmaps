/*
 * Copyright (C) 2012 - 2014, S2H Mobile
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
import java.io.IOException;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Environment;

public final class ExternalStorageHandler {

	/** Caches the handler for the image file. */
	private static File sImageFile = null;

	private ExternalStorageHandler() {
	}

	/**
	 * Delete image file.
	 * 
	 * @since v1.1.1
	 * 
	 * @param fileName
	 * @return True if file was deleted successfully.
	 */
	public static boolean deleteImageFile(final String fileName) {
		try {
			final File image = ExternalStorageHandler.getImageFile(fileName);
			return image != null ? image.delete() : false;
		} catch (final IOException e) {
			return false;
		}
	}

	public static File getImageFile(final String fileName) throws IOException {
		if (sImageFile == null) {
			sImageFile = createImageFile(fileName);
		}

		return sImageFile;
	}

	/**
	 * Get a usable cache directory (external if available, internal otherwise).
	 * Returns {@code null} if an I/O exception occurs.
	 * 
	 * @param context
	 *            - the context to use
	 * @param uniqueName
	 *            - a unique directory name to append to the cache dir
	 * @return The cache directory.
	 */
	static File getDiskCacheDir(final Context context, final String uniqueName)
			throws IOException {

		/*
		 * Check if media is mounted or storage is built-in. If so, try and use
		 * external cache directory. Otherwise use internal cache directory.
		 */
		final File cacheDir = isExternalStorageWritable() ? context
				.getExternalCacheDir() : context.getCacheDir();
		if (cacheDir == null) {
			return null;
		}

		final String path = cacheDir.getPath() + File.separator + uniqueName;
		return new File(path);
	}

	private static File createImageFile(final String fileName)
			throws IOException {
		if (!isExternalStorageWritable()) {
			return null;
		}

		final File path = Environment
				.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		path.mkdirs();
		return new File(path, fileName);
	}

	/**
	 * Check if external storage is built-in or removable.
	 * 
	 * @return True if external storage is removable (like an SD card), false
	 *         otherwise.
	 */
	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	private static boolean isExternalStorageRemovable() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD ? Environment
				.isExternalStorageRemovable() : true;
	}

	private static boolean isExternalStorageWritable() {
		final String state = Environment.getExternalStorageState();
		return Environment.MEDIA_MOUNTED.equals(state);
	}
}