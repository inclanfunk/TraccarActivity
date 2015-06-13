package org.traccar.client;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.traccar.client.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ImageActivity extends Activity {

    public static final String TAG = "Image Activity";

    public static final String INVALID_LOCATION_DATA = "Unable to get current location. " +
            "Send data to server failed";

    private ImageView mImageView;
    private TextView mImageQualityButton;

    private ImageButton mCancelButton;
    private ImageButton mShootAgainButton;
    private ImageButton mSendButton;

    private Dialog mImageQualityDialog;
    private String mImageQuality = "50%";//default image quality should be represented in mImageQualityValues
    private List<String> mImageQualityValues;

    private File mPicture;

    private SharedPreferences mSharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_view);

        mImageView = (ImageView) findViewById(R.id.imageViewer);
        mCancelButton = (ImageButton) findViewById(R.id.cancelButton);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ImageActivity.this.finish();
            }
        });

        parseIntent();

        mShootAgainButton = (ImageButton) findViewById(R.id.shootAgainButton);
        mShootAgainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent data = new Intent();
                if (getParent() == null) {
                    setResult(RESULT_OK, data);
                } else {
                    getParent().setResult(RESULT_OK, data);
                }
                finish();
            }
        });

        mSendButton = (ImageButton) findViewById(R.id.sendButton);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    sendImage();
                } catch (IOException e) {
                    Log.e(TAG, e.getLocalizedMessage());
                    e.printStackTrace();
                }
            }
        });

        mImageQualityValues
                = Arrays.asList(getResources().getStringArray(R.array.image_quality_array));
        mImageQualityButton = (TextView) findViewById(R.id.imageQuality);
        mImageQualityButton.setText(getResources().getString(R.string.image_quality) + ": " +
                mImageQuality);
        mImageQualityDialog = configureImageQualityDialog();
        mImageQualityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mImageQualityDialog.show();
            }
        });

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

    }

    private Dialog configureImageQualityDialog() {
        Dialog imageQualityDialog = new Dialog(ImageActivity.this);
        imageQualityDialog.setContentView(R.layout.image_quality_dialog);
        imageQualityDialog.setTitle(getResources().getString(R.string.image_quality));

        final String previousImageQualityValue = mImageQuality;//hold value of image quality before invoking dialog.

        configureImageQualitySpinner(imageQualityDialog);
        configureImageQualityDialogButtons(imageQualityDialog, previousImageQualityValue);

        return imageQualityDialog;
    }

    private void configureImageQualitySpinner(Dialog imageQualityDialog) {
        Spinner imageQualitySpinner
                = (Spinner) imageQualityDialog.findViewById(R.id.imageQualitySpinner);
        ArrayAdapter<String> imageQualityAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, mImageQualityValues);
        imageQualitySpinner.setAdapter(imageQualityAdapter);
        if (mImageQualityValues.contains(mImageQuality))
            imageQualitySpinner.setSelection(mImageQualityValues.indexOf(mImageQuality));
        else {
            Log.w(TAG, getResources().getString(R.string.image_quality_values_error));
            mImageQuality = mImageQualityValues.get(0);
        }

        imageQualitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                mImageQuality = adapterView.getItemAtPosition(i).toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    private void configureImageQualityDialogButtons(final Dialog imageQualityDialog,
                                                    final String previousImageQualityValue) {

        Button okButton = (Button) imageQualityDialog.findViewById(R.id.okImageDialog);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                imageQualityDialog.hide();
                mImageQualityButton.setText(getResources().getString(R.string.image_quality) +
                        ": " + mImageQuality);
            }
        });

        Button cancelButton = (Button) imageQualityDialog.findViewById(R.id.cancelImageDialog);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mImageQuality = previousImageQualityValue;
                imageQualityDialog.hide();
            }
        });
    }

    private void parseIntent() {
        try {
            Intent intent = getIntent();
            if (intent == null) return;
            String imageUrl = intent.getStringExtra(Intent.EXTRA_ORIGINATING_URI);
            if (imageUrl == null || imageUrl.isEmpty()) return;
            Uri imageUri = Uri.parse(imageUrl);
            mPicture = new File(imageUri.getPath());
            Bitmap picture = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
            mImageView.setImageBitmap(picture);
        } catch (IOException e) {
            Log.e(TAG, e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    private void sendImage() throws IOException {
        if (mPicture == null) return;
        SendImageToServerTask sendImageTask = new SendImageToServerTask(mPicture);
        sendImageTask.execute();
        finish();
    }

    private class SendImageToServerTask extends AsyncTask<Void, Integer, String> {

        private File imageToSend;

        SendImageToServerTask(File imageToSend) {
            this.imageToSend = imageToSend;
        }

        @Override
        protected String doInBackground(Void ... params) {
            return uploadImage();
        }

        @SuppressWarnings("deprecation")
        private String uploadImage() {
            String responseString;

            String address = mSharedPreferences.getString(TraccarActivity.KEY_ADDRESS, "localhost");

            HttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(address + ":8082/process_image");

            try {
                MultipartEntity entity = new MultipartEntity();

                entity.addPart("image", new FileBody(imageToSend, "image/jpeg"));

                String deviceId = mSharedPreferences.getString(TraccarActivity.KEY_ID, null);
                String altitude = mSharedPreferences.getString(PositionProvider.ALTITUDE, null);
                String latitude = mSharedPreferences.getString(PositionProvider.LATITUDE, null);
                String longitude = mSharedPreferences.getString(PositionProvider.LONGITUDE, null);
                String orientation = mSharedPreferences.getString(PositionProvider.ORIENTATION,
                        null);

                if (!validatePositionData(altitude, latitude, longitude, orientation)) {
                    responseString = "Unable to get all position data.";
                    Log.e(TAG, responseString);
                    return responseString;
                }

                entity.addPart("deviceId", new StringBody(deviceId));
                entity.addPart("altitude", new StringBody(altitude));
                entity.addPart("latitude", new StringBody(latitude));
                entity.addPart("longitude", new StringBody(longitude));
                entity.addPart("time", new StringBody(String.valueOf(System.currentTimeMillis())));
                entity.addPart("orientation", new StringBody(orientation));

                httpPost.setEntity(entity);

                HttpResponse response = httpClient.execute(httpPost);
                HttpEntity responseEntity = response.getEntity();

                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    responseString = EntityUtils.toString(responseEntity);
                } else {
                    responseString = "Error occurred. Http Status Code: "
                            + statusCode;
                }

            } catch (ClientProtocolException e) {
                responseString = e.toString();
            } catch (IOException e) {
                responseString = e.toString();
            }

            return responseString;

        }

        @Override
        protected void onPostExecute(String responseString) {
            super.onPostExecute(responseString);
            Log.i(TAG, "Image sending result: " + responseString);
        }

        private boolean validatePositionData(String... locationData) {
            for (String location : locationData)
                if (location == null) return false;
            return true;
        }

    }

}
