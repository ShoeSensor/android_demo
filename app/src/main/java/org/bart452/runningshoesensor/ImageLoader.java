package org.bart452.runningshoesensor;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.view.View;

/**
 * Created by bart452 on 13-4-16.
 */
public class ImageLoader extends AsyncTask<Integer, Void, Bitmap> {


    public ImageLoader(View V, Resources res) {

    }

    @Override
    protected Bitmap doInBackground(Integer... params) {
        return null;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        super.onPostExecute(bitmap);
    }
}
