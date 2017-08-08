# android-bitmaps

Legacy Android library for rendering bitmaps. You can load a bitmap from a resource or a file.

## Setup
- Import the project folder as a library into the Eclipse ADT workspace.
- Add the library to an existing Android application: 

1. Right-click on your application in Eclipse -> _Properties_
2. Select _Android_ -> _Library_
3. Click on _Add ..._
4. Select the library and click _Ok_

## Usage
- Create an instance of ``ImageLoader`` in your activity.
- Use one of the methods ``loadImageFromResource()`` or ``loadBitmapFromFile()`` to load an image into your ``ImageView``:


```java
package com.example.bitmaps;

import android.os.Bundle;
import android.widget.ImageView;

import de.s2hmobile.bitmaps.ImageLoader;


public class MainActivity extends Activity {
	
	ImageLoader mImageLoader;
	
	ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView = (ImageView) findViewById(R.id.image_main);

        mImageLoader = new ImageLoader(getResources());

		// load drawable into image view, at size 200 x 200
        mImageLoader.loadImageFromResource(mImageView, R.drawable.ic_launcher, 200, 200);
    }
}

```

