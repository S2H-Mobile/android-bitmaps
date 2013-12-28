package de.s2hmobile.bitmaps;

import java.io.File;
import java.io.IOException;

import android.content.Context;
import android.support.v4.app.FragmentManager;

/**
 * Holds disk cache parameters.
 * 
 * @author s.hoehne
 * 
 */
public class DiskCacheParams {

	private final File mDiskCacheDir;

	/**
	 * The size of the disk cache in megabytes.
	 */
	private final int mDiskCacheSize;

	/**
	 * Create a set of image cache parameters that can be provided to
	 * {@link ImageCache#getInstance(FragmentManager, DiskCacheParams)} or
	 * {@link ImageLoader#addImageCache(FragmentManager, DiskCacheParams)}.
	 * 
	 * @param dirName
	 *            - unique subdirectory name that will be appended to the
	 *            application cache directory, usually "cache" or "images" is
	 *            sufficient
	 */
	public DiskCacheParams(final Context context, final String dirName,
			final int diskCacheSize) throws IOException {
		mDiskCacheDir = ExternalStorageHandler
				.getDiskCacheDir(context, dirName);
		mDiskCacheSize = diskCacheSize;
	}

	public File getDiskCacheDir() {
		return mDiskCacheDir;
	}

	public int getDiskCacheSize() {
		return mDiskCacheSize;
	}

}