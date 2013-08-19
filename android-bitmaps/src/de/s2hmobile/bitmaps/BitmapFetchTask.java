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

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import de.s2hmobile.bitmaps.framework.AsyncTask;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.http.AndroidHttpClient;
import android.os.Build;
import android.widget.ImageView;

/**
 * Decodes a bitmap from an image url. The parameters are an integer array
 * composed of the height and with of the target bitmap.
 * 
 * @author Stephan Hoehne
 * 
 */
public final class BitmapFetchTask extends BitmapBaseTask {

	/** Timeout in milliseconds for each Http request. **/
	private static final int HTTP_REQUEST_TIMEOUT_MS = 2 * 60 * 1000;

	/** User agent string, to be displayed in the server logs. **/
	private static final String USER_AGENT = "Android " + Build.VERSION.RELEASE;

	private final String mUrl;

	private BitmapFetchTask(final String url,
			final OnBitmapRenderedListener listener, final ImageView imageView) {
		super(listener, imageView);
		mUrl = url;
	}

	@Override
	protected String createKey(final Bitmap bitmap) {

		final int width = bitmap.getWidth();
		final int height = bitmap.getHeight();

		final String source = new StringBuilder().append(mUrl).append("_")
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

	/**
	 * The parameters are an integer array consisting of the width and height of
	 * the required image, in this order.
	 */
	@Override
	protected Bitmap doInBackground(final Integer... params) {

		// evaluate the parameters
		final int targetWidth = params[0];
		final int targetHeight = params[1];

		// get the image source as byte array
		final byte[] content = getImageByteArray(mUrl);

		// decode the bitmap from the byte array
		return decodeBitmapFromByteArray(content, targetWidth, targetHeight);
	}

	/**
	 * Executes a {@link BitmapFetchTask} to render a bitmap from the given
	 * image url.
	 * 
	 * @param url
	 *            - the url of the image file, for example a jpeg
	 * @param listener
	 *            - for the callback
	 * @param imageView
	 *            - the {@link ImageView} displaying the target bitmap
	 * @param targetWidth
	 *            - the width of the target bitmap
	 * @param targetHeight
	 *            - the height of the target bitmap
	 */
	public static void renderBitmapFromUrl(final String url,
			final OnBitmapRenderedListener listener, final ImageView imageView,
			final int targetWidth, final int targetHeight) {

		// instantiate the task
		final BitmapFetchTask task = new BitmapFetchTask(url, listener,
				imageView);

		// start the task with parameter array
		final Integer[] params = { targetWidth, targetHeight };
		task.executeOnExecutor(AsyncTask.DUAL_THREAD_EXECUTOR, params);
	}

	private static Bitmap decodeBitmapFromByteArray(final byte[] data,
			final int targetWidth, final int targetHeight) {
		final BitmapFactory.Options options = new BitmapFactory.Options();

		/*
		 * Read the dimensions of the source image prior to construction (and
		 * memory allocation) of the target bitmap.
		 */
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(data, 0, data.length, options);

		// raw height and width of image
		final int imageWidth = options.outWidth;
		final int imageHeight = options.outHeight;

		// decode the source image into a bitmap
		options.inJustDecodeBounds = false;
		options.inSampleSize = calculateInSampleSize(imageHeight, imageWidth,
				targetHeight, targetWidth);
		options.inPurgeable = true;
		return BitmapFactory.decodeByteArray(data, 0, data.length, options);
	}

	/**
	 * Set up a custom {@link AndroidHttpClient} with custom timeout.
	 * 
	 * @return The {@link AndroidHttpClient} instance.
	 */
	private static final AndroidHttpClient getAndroidHttpClient() {
		final AndroidHttpClient client = AndroidHttpClient
				.newInstance(USER_AGENT);

		// set the timeout parameters
		final HttpParams params = client.getParams();
		HttpConnectionParams.setConnectionTimeout(params,
				HTTP_REQUEST_TIMEOUT_MS);
		HttpConnectionParams.setSoTimeout(params, HTTP_REQUEST_TIMEOUT_MS);
		ConnManagerParams.setTimeout(params, HTTP_REQUEST_TIMEOUT_MS);
		return client;
	}

	private static final byte[] getImageByteArray(final String spec) {
		final AndroidHttpClient client = getAndroidHttpClient();
		final InputStream in = null;
		try {
			final URI uri = new URI(spec);
			final HttpGet get = new HttpGet(uri);
			final String mimeType = "image/jpeg";
			get.setHeader("Content-Type", mimeType);
			final HttpResponse response = client.execute(get);
			final int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode == HttpStatus.SC_OK) {

				return IOUtils.toByteArray(response.getEntity().getContent());
			} else {

				// TODO remove log statement in production
				// final String message = response.getStatusLine()
				// .getReasonPhrase();
				// android.util.Log.w("BitmapFetchTask",
				// "Unable to connect to server. " + message);
				return null;
			}
		} catch (final URISyntaxException e) {
			return null;
		} catch (final ClientProtocolException e) {
			return null;
		} catch (final IOException e) {
			return null;
		} finally {
			IOUtils.closeQuietly(in);
			if (client != null) {
				client.close();
			}
		}
	}
}