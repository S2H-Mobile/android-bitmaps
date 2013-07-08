package de.s2hmobile.bitmaps;

import android.graphics.Bitmap;
import android.widget.ImageView;

/**
 * Functional interface. Activities should implement this to provide a callback
 * listens to bitmap rendered events.
 * 
 * @author Stephan Hoehne
 * 
 */
public interface OnBitmapRenderedListener {

	/**
	 * Triggered when bitmap is rendered.
	 * 
	 * @param view
	 *            - the {@link ImageView} holding the bitmap
	 * @param bitmap
	 *            - the rendered bitmap
	 */
	public void onBitmapRendered(ImageView view, Bitmap bitmap);
}
