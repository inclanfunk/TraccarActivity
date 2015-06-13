/*
 * Copyright 2013 - 2014 Anton Tananaev (anton.tananaev@gmail.com)
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

import java.util.Date;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class PositionProvider implements SensorEventListener {

    public static final String TAG = "PositionProvider";

    public static final String ALTITUDE = "altitude";
    public static final String LATITUDE = "latitude";
    public static final String LONGITUDE = "longitude";
    public static final String ORIENTATION = "orientation";

    public static final String PROVIDER_MIXED = "mixed";
    public static final long PERIOD_DELTA = 10 * 1000;
    public static final long RETRY_PERIOD = 60 * 1000;

    public interface PositionListener {
        public void onPositionUpdate(Location location);
    }
    
    private final Handler handler;
    private final LocationManager locationManager;
    private final long period;
    private final PositionListener listener;
    private final Context context;
    private SharedPreferences sharedPreferences;

    private boolean useFine;
    private boolean useCoarse;

    private SensorManager mSensorManager;

    private float[] mRotationMatrix;
    private float[] mAccelerometerData;
    private float[] mMagneticFieldData;
    private float[] mOrientationData;

    public PositionProvider(Context context, String type, long period, PositionListener listener) {
        handler = new Handler(context.getMainLooper());
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.period = period;
        this.listener = listener;
        this.context = context;

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.edit().putString("test", "HELLO").commit();
        makeFirstMeasurement();

        // Determine providers
        if (type.equals(PROVIDER_MIXED)) {
            useFine = true;
            useCoarse = true;
        } else if (type.equals(LocationManager.GPS_PROVIDER)) {
            useFine = true;
        } else if (type.equals(LocationManager.NETWORK_PROVIDER)) {
            useCoarse = true;
        }

        prepareSensorManager();
    }

    private void prepareSensorManager() {
        mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);

        mRotationMatrix = new float[16];
        mAccelerometerData = new float[3];
        mMagneticFieldData = new float[3];
        mOrientationData = new float[3];
    }

    private void makeFirstMeasurement() {
        String provider = useFine ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
        Location location = locationManager.getLastKnownLocation(provider);
        if (location == null) {
            if (useFine) {
                provider = LocationManager.NETWORK_PROVIDER;
                location = locationManager.getLastKnownLocation(provider);
            }
        }
        if (location != null) {
            storeLocationInPreferences(location);
        } else {
            Log.w(TAG, "Unable to get device location");
        }
    }

    public void startUpdates() {
        if (useFine) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, period, 0, fineLocationListener);
            } catch (Exception ex) {
                
            }
        }
        if (useCoarse) {
            try {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, period, 0, coarseLocationListener);
            } catch (Exception ex) {

            }
        }
        handler.postDelayed(updateTask, period);

        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_UI);
    }

    public void stopUpdates() {
        handler.removeCallbacks(updateTask);
        locationManager.removeUpdates(fineLocationListener);
        locationManager.removeUpdates(coarseLocationListener);

        mSensorManager.unregisterListener(this);
    }

    private final Runnable updateTask = new Runnable() {

        private boolean tryProvider(String provider) {
            Location location = locationManager.getLastKnownLocation(provider);

            /*if (location != null) {
                Toast.makeText(context, "phone: " + new Date() + "\ngps: " + new Date(location.getTime()), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, "no location", Toast.LENGTH_LONG).show();
            }*/
            
            if (location != null && new Date().getTime() - location.getTime() <= period + PERIOD_DELTA) {
                listener.onPositionUpdate(location);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void run() {
            if (useFine && tryProvider(LocationManager.GPS_PROVIDER)) {
            } else if (useCoarse && tryProvider(LocationManager.NETWORK_PROVIDER)) {
            } else {
                listener.onPositionUpdate(null);
            }
            handler.postDelayed(this, period);
        }

    };

    private final InternalLocationListener fineLocationListener = new InternalLocationListener();
    private final InternalLocationListener coarseLocationListener = new InternalLocationListener();

    private class InternalLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location location) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onStatusChanged(final String provider, int status, Bundle extras) {
            if (status == LocationProvider.TEMPORARILY_UNAVAILABLE || status == LocationProvider.OUT_OF_SERVICE) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        locationManager.removeUpdates(InternalLocationListener.this);
                        locationManager.requestLocationUpdates(provider, period, 0, InternalLocationListener.this);
                    }
                }, RETRY_PERIOD);
            }
        }

    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        loadSensorData(sensorEvent);
        SensorManager.getRotationMatrix(mRotationMatrix, null, mAccelerometerData,
                mMagneticFieldData);
        SensorManager.getOrientation(mRotationMatrix, mOrientationData);
        storeAzimuthInPreferences(mOrientationData[0]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        Log.i(TAG, "Accuracy of a sensor has changed");
    }

    private void loadSensorData(SensorEvent sensorEvent) {
        final int type = sensorEvent.sensor.getType();
        if (type == Sensor.TYPE_ACCELEROMETER) {
            mAccelerometerData = sensorEvent.values.clone();
        }

        if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            mMagneticFieldData = sensorEvent.values.clone();
        }
    }

    private void storeLocationInPreferences(Location location) {
        SharedPreferences.Editor preferencesEditor = sharedPreferences.edit();
        preferencesEditor.putString(ALTITUDE, String.valueOf(location.getAltitude()));
        preferencesEditor.putString(LATITUDE, String.valueOf(location.getLatitude()));
        preferencesEditor.putString(LONGITUDE, String.valueOf(location.getLongitude()));
        preferencesEditor.commit();
    }

    private void storeAzimuthInPreferences(float azimuth) {
        SharedPreferences.Editor preferencesEditor = sharedPreferences.edit();
        preferencesEditor.putString(ORIENTATION, String.valueOf(azimuth));
        preferencesEditor.commit();
    }

}
