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
import java.io.IOException;

import android.os.Environment;

public final class ExternalStorageHandler {

	/** Caches the file handler. */
	private static File sFile = null;

	private ExternalStorageHandler() {
	}

	public static boolean deleteImageFile(final String fileName) {
		try {
			return ExternalStorageHandler.getImageFile(fileName).delete();
		} catch (final IOException e) {
			return false;
		}
	}

	public static File getImageFile(final String fileName) throws IOException {
		if (sFile == null) {
			if (isExternalStorageWritable()) {
				final File path = Environment
						.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
				path.mkdirs();
				sFile = new File(path, fileName);
			} else {
				throw new IOException(
						"Can't create path to external storage directory.");
			}
		}
		return sFile;
	}

	private static boolean isExternalStorageWritable() {
		final String currentState = Environment.getExternalStorageState();
		return Environment.MEDIA_MOUNTED.equals(currentState);
	}
}