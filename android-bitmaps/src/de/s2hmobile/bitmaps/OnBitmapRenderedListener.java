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

import android.graphics.Bitmap;
import android.widget.ImageView;

/**
 * Functional interface. Activities should implement this callback to listen to
 * bitmap rendered events.
 * 
 * @author Stephan Hoehne
 * 
 */
public interface OnBitmapRenderedListener {

	/**
	 * Triggered when bitmap is rendered.
	 * 
	 * @param view
	 *            - the {@link ImageView} that holds the bitmap
	 * @param taggedBitmap
	 *            - the rescaled bitmap and its the key
	 */
	public void onBitmapRendered(final ImageView view, final Bitmap bitmap);
}
