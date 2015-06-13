/*
 * Copyright 2012 - 2014 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.client;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.ActionBar;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.FragmentManager;
import android.app.TaskStackBuilder;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import org.traccar.client.util.Util;

/**
 * Main user interface
 */
@SuppressWarnings("deprecation")
public class TraccarActivity extends PreferenceActivity implements
		SensorEventListener {

    public static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
    public static final int IMAGE_ACTIVITY_REQUEST_CODE = 200;
    private String mCurrentPhotoPath;

	public static final String LOG_TAG = "traccar";
	public static final String KEY_ID = "id";
	public static final String KEY_ADDRESS = "address";
	public static final String KEY_PORT = "port";
	public static final String KEY_INTERVAL = "interval";
	public static final String KEY_PROVIDER = "provider";
	public static final String KEY_EXTENDED = "extended";
	public static final String KEY_STATUS = "status";
	public static final String FALL_DETECTION_STATUS = "fallDetection";
	SubmitFallFragment submitFallFragment = new SubmitFallFragment();
	public boolean warningDialogVisible = false;
	public  boolean FALL_REPORTING = false;
	private Timer fallAlarmTimer = new Timer();
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		initPreferences();
		SharedPreferences sharedPreferences = getPreferenceScreen()
				.getSharedPreferences();
		if (sharedPreferences.getBoolean(KEY_STATUS, false)) {
			startService(new Intent(TraccarActivity.this,
					TraccarService.class));
		}
		if (sharedPreferences.getBoolean(FALL_DETECTION_STATUS, false)) {
			registerAccelerometerListener();	
		}
		
		fallAlarmTimer.schedule(new TimerTask() {
			AtomicInteger i = new AtomicInteger() ;
			@Override
			public void run() {
				if(FALL_REPORTING){
					TraccarService.inDanger = true;
					TraccarActivity.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							ActionBar actionBar = getActionBar();
							if((i.intValue() % 2) == 0){
								actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#FF0000")));
							}else{
								actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#FFFFFF")));
							}
							i.incrementAndGet();
						}
					});
				}else{
					TraccarService.inDanger = false;
				}
			}
		}, 0, 1000);
	}

	@Override
	protected void onResume() {
		super.onResume();
		getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(
						preferenceChangeListener);
	}

	@Override
	protected void onPause() {
		getPreferenceScreen().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(
						preferenceChangeListener);
		super.onPause();
	}
	OnSharedPreferenceChangeListener preferenceChangeListener = new OnSharedPreferenceChangeListener() {
		@Override
		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			if (key.equals(KEY_STATUS)) {
				if (sharedPreferences.getBoolean(KEY_STATUS, false)) {
					startService(new Intent(TraccarActivity.this,
							TraccarService.class));
				} else {
					stopService(new Intent(TraccarActivity.this,
							TraccarService.class));
				}
			}
			if (key.equals(FALL_DETECTION_STATUS)) {
				if (sharedPreferences.getBoolean(FALL_DETECTION_STATUS, false)) {
					registerAccelerometerListener();
				} else {
					FALL_REPORTING = false;
					ActionBar actionBar = getActionBar();
					actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#FFFFFF")));
					unregisterAccelerometerLister();
				}
			} else if (key.equals(KEY_ID)) {
				findPreference(KEY_ID).setSummary(
						sharedPreferences.getString(KEY_ID, null));
			}
		}
	};
	private void registerAccelerometerListener(){
		SensorManager sensorManager = (SensorManager) TraccarActivity.this
				.getSystemService(Context.SENSOR_SERVICE);
		Sensor sensor = sensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		sensorManager.registerListener(TraccarActivity.this,
				sensor, SensorManager.SENSOR_DELAY_NORMAL);
	}
	private void unregisterAccelerometerLister(){
		SensorManager sensorManager = (SensorManager) TraccarActivity.this
				.getSystemService(Context.SENSOR_SERVICE);
		sensorManager.unregisterListener(TraccarActivity.this);
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.status) {
			startActivity(new Intent(this, StatusActivity.class));
			return true;
		} else if (item.getItemId() == R.id.about) {
			startActivity(new Intent(this, AboutActivity.class));
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private boolean isServiceRunning() {
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE)) {
			if (TraccarService.class.getName().equals(
					service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	private void initPreferences() {
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

		TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		String id = telephonyManager.getDeviceId();

		SharedPreferences sharedPreferences = getPreferenceScreen()
				.getSharedPreferences();

		if (!sharedPreferences.contains(KEY_ID)) {
			sharedPreferences.edit().putString(KEY_ID, id).commit();
		}
		findPreference(KEY_ID).setSummary(
                sharedPreferences.getString(KEY_ID, id));
        initTakePicturePreference();
    }

    private void initTakePicturePreference() {
        Preference preference = findPreference(getResources().getString(R.string.take_picture_key));
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startCamera();
                return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
                Intent imageViewIntent = new Intent(this, ImageActivity.class);
                imageViewIntent.putExtra(Intent.EXTRA_ORIGINATING_URI, mCurrentPhotoPath);
                mCurrentPhotoPath = null;
                startActivityForResult(imageViewIntent, IMAGE_ACTIVITY_REQUEST_CODE);
            } else if (requestCode == IMAGE_ACTIVITY_REQUEST_CODE) {
                startCamera();
            }
        }
    }

    private void startCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        Uri imageUri= Util.getOutputImageFileUri();
        mCurrentPhotoPath = imageUri.toString();
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(cameraIntent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
    }

    float[] gravity = new float[3];
	float[] valuesAccelerometer = new float[3];
	int mSensorTuningCounter;

	@Override
	public void onSensorChanged(SensorEvent event) {
		switch (event.sensor.getType()) {
		case Sensor.TYPE_ACCELEROMETER:
			for (int i = 0; i < 3; i++) {
				valuesAccelerometer[i] = event.values[i];
			}
			final float alpha = 0.8f;
			gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
			gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
			gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

			float Value = getGForce();
			Log.d(LOG_TAG, "Gforce Value =  " + Value);

			if (Value < 0.5) {
				Log.d(LOG_TAG, "Device Dropped " + Value);
				if (!warningDialogVisible) {
					warningDialogVisible = true;
					submitFallFragment = new SubmitFallFragment();
					FragmentManager fm = getFragmentManager();
					submitFallFragment.show(fm, "show_fall_fragment");
				}
			}

			break;
		}
		mSensorTuningCounter++;
	}

	public float getGForce() {

		double netForce = gravity[0] * gravity[0];
		netForce += gravity[1] * gravity[1];
		netForce += gravity[2] * gravity[2];

		netForce = Math.sqrt(netForce);
		float gForce = (float) netForce / SensorManager.GRAVITY_EARTH;
		return gForce;
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}

//    private Uri getOutputImageFileUri(){
//        return Uri.fromFile(getOutputImageFile());
//    }
//
//    /** Create a File for saving an image*/
//    private File getOutputImageFile(){
//        // To be safe, you should check that the SDCard is mounted
//        // using Environment.getExternalStorageState() before doing this.
//
//        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
//                Environment.DIRECTORY_PICTURES), "traccar_images");
//        // This location works best if you want the created images to be shared
//        // between applications and persist after your app has been uninstalled.
//
//        // Create the storage directory if it does not exist
//        if (! mediaStorageDir.exists()){
//            if (! mediaStorageDir.mkdirs()){
//                Log.d("MyCameraApp", "failed to create directory");
//                return null;
//            }
//        }
//
//        // Create a media file name
//        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
//        return new File(mediaStorageDir.getPath() + File.separator +
//                    "IMG_"+ timeStamp + ".jpg");
//    }
}
