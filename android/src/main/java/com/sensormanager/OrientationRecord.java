package com.sensormanager;

import android.os.Bundle;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.support.annotation.Nullable;

import java.io.*;
import java.util.Date;
import java.util.Timer;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.ReactApplicationContext;

public class OrientationRecord implements SensorEventListener {
    private final int ORIENTATION_PORTRAIT = 6;
    private final int ORIENTATION_LANDSCAPE_REVERSE = 3;
    private final int ORIENTATION_LANDSCAPE = 1;
    private final int ORIENTATION_PORTRAIT_REVERSE = 8;
    int smoothness = 1;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mMagnetometer;
    private long lastUpdate = 0;
    private int i = 0, n = 0;
    private int delay;
    private int isRegistered = 0;

    private ReactContext mReactContext;
    private Arguments mArguments;
    private int orientation = ORIENTATION_PORTRAIT;

    public OrientationRecord(ReactApplicationContext reactContext) {
        mSensorManager = (SensorManager)reactContext.getSystemService(reactContext.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mReactContext = reactContext;
    }

    public int start(int delay) {
        this.delay = delay;
        if (mAccelerometer != null && isRegistered == 0) {
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
            mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_UI);
            isRegistered = 1;
            return (1);
        }
        return (0);
    }

    public void stop() {
        if (isRegistered == 1) {
            mSensorManager.unregisterListener(this);
        isRegistered = 0;
      }
    }

    private void sendEvent(String eventName, @Nullable WritableMap params)
    {
        try {
            mReactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
        } catch (RuntimeException e) {
            Log.e("ERROR", "java.lang.RuntimeException: Trying to invoke JS before CatalystInstance has been set!");
        }
    }

    float[] mGravity;
    float[] mGeomagnetic;

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
      Sensor mySensor = sensorEvent.sensor;
      WritableMap map = mArguments.createMap();

      if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER)
        mGravity = sensorEvent.values;
      if (mySensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
        mGeomagnetic = sensorEvent.values;
      if (mGravity != null && mGeomagnetic != null) {
        float R[] = new float[9];
        float I[] = new float[9];
        boolean success = mSensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
        if (success) {
          long curTime = System.currentTimeMillis();
          float orientationData[] = new float[3];
          mSensorManager.getOrientation(R, orientationData);

          float heading = (float)((Math.toDegrees(orientationData[0])) % 360.0f);
          float pitch = (float)((Math.toDegrees(orientationData[1])) % 360.0f);
          float roll = (float)((Math.toDegrees(orientationData[2])) % 360.0f);

          String returned  = calculateOrientation(pitch, roll);

          i++;
          if ((curTime - lastUpdate) > delay) {
            i = 0;
            map.putString("orientation", returned);
            sendEvent("Orientation", map);
            lastUpdate = curTime;
          }
        }
      }
    }

     private String calculateOrientation(float averagePitch, float averageRoll) {
        // finding local orientation dip
        if (((orientation == ORIENTATION_PORTRAIT || orientation == ORIENTATION_PORTRAIT_REVERSE)
                && (averageRoll > -30 && averageRoll < 30))) {
            if (averagePitch > 0)
                return "PORTRAIT_REVERSE";
            else
                return "PORTRAIT";
        } else {
            // divides between all orientations
            if (Math.abs(averagePitch) >= 30) {
                if (averagePitch > 0)
                    return "PORTRAIT_REVERSE";
                else
                    return "PORTRAIT";
            } else {
                if (averageRoll > 0) {
                  return "LANDSCAPE_RIGHT";
                } else {
                    return "LANDSCAPE";
                }
            }
        }
    }

    private float addValue(float value, float[] values) {
        value = (float) Math.round((Math.toDegrees(value)));
        float average = 0;
        for (int i = 1; i < smoothness; i++) {
            values[i - 1] = values[i];
            average += values[i];
        }
        values[smoothness - 1] = value;
        average = (average + value) / smoothness;
        return average;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
