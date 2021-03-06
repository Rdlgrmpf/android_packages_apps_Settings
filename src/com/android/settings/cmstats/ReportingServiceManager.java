/*
 * Copyright (C) 2012 The CyanogenMod Project
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

package com.android.settings.cmstats;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class ReportingServiceManager extends BroadcastReceiver {

    public static final long dMill = 24L * 60L * 60L * 1000L;
    public static final long tFrame = 1L * dMill;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            setAlarm(ctx);
        } else {
            launchService(ctx);
        }
    }

    protected static void setAlarm (Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("CMStats", 0);
        boolean optedIn = prefs.getBoolean(AnonymousStats.ANONYMOUS_OPT_IN, true);
        if (!optedIn) {
            return;
        }
        long lastSynced = prefs.getLong(AnonymousStats.ANONYMOUS_LAST_CHECKED, 0);
        if (lastSynced == 0) {
            // never synced, so let's fake out that the last sync was just now.
            // this will allow the user tFrame time to opt out before it will start
            // sending up anonymous stats.
            lastSynced = System.currentTimeMillis();
            prefs.edit().putLong(AnonymousStats.ANONYMOUS_LAST_CHECKED, lastSynced).apply();
            Log.d(ReportingService.TAG, "Set alarm for first sync.");
        }
        long timeLeft = (lastSynced + tFrame) - System.currentTimeMillis();
        Intent sIntent = new Intent(ConnectivityManager.CONNECTIVITY_ACTION);
        sIntent.setComponent(new ComponentName(ctx.getPackageName(), ReportingServiceManager.class.getName()));
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + timeLeft, PendingIntent.getBroadcast(ctx, 0, sIntent, 0));
        Log.d(ReportingService.TAG, "Next sync attempt in : " + timeLeft * 24 / dMill + " hours");
    }

    public static void launchService (Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnected()) {
            return;
        }
        SharedPreferences prefs = ctx.getSharedPreferences("CMStats", 0);
        boolean optedIn = prefs.getBoolean(AnonymousStats.ANONYMOUS_OPT_IN, true);
        if (!optedIn) {
            return;
        }
        long lastSynced = prefs.getLong(AnonymousStats.ANONYMOUS_LAST_CHECKED, 0);
        if (lastSynced == 0) {
            setAlarm(ctx);
            return;
        }
        long timeLeft = System.currentTimeMillis() - lastSynced;
        if (timeLeft < tFrame) {
            Log.d(ReportingService.TAG, "Waiting for next sync : " + timeLeft * 24 / dMill + " hours");
            return;
        }
        Intent sIntent = new Intent();
        sIntent.setComponent(new ComponentName(ctx.getPackageName(), ReportingService.class.getName()));
        ctx.startService(sIntent);
    }
}
