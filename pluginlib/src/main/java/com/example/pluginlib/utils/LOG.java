package com.example.pluginlib.utils;

import android.util.Log;

import com.example.pluginlib.BuildConfig;

public class LOG {

    private static final String TAG = "LoadPlugin";

    public static void e(String log) {
        e(TAG, log);
    }

    public static void e(String tag, String log) {
        Log.e(tag, log);
    }

    public static void d(String log) {
        d(TAG, log);
    }

    public static void d(String tag, String log) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, log);
        }
    }

    public static void w(String log) {
        w(TAG, log);
    }

    public static void w(String tag, String log) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, log);
        }
    }

    public static void i(String log) {
        i(TAG, log);
    }

    public static void i(String tag, String log) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, log);
        }
    }
}
