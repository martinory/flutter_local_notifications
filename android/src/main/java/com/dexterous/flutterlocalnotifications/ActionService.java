// // Copyright 2017 The Chromium Authors. All rights reserved.
// // Use of this source code is governed by a BSD-style license that can be
// // found in the LICENSE file.

// package com.dexterous.flutterlocalnotifications;

// import android.app.AlarmManager;
// import android.app.PendingIntent;
// import android.content.Context;
// import android.content.Intent;
// import android.content.SharedPreferences;
// import android.os.Handler;
// import android.util.Log;
// import androidx.core.app.AlarmManagerCompat;
// import androidx.core.app.JobIntentService;
// import io.flutter.plugin.common.MethodChannel;
// import io.flutter.plugin.common.PluginRegistry.PluginRegistrantCallback;
// import io.flutter.view.FlutterCallbackInformation;
// import io.flutter.view.FlutterMain;
// import io.flutter.view.FlutterNativeView;
// import io.flutter.view.FlutterRunArguments;
// import java.util.Collections;
// import java.util.HashMap;
// import java.util.HashSet;
// import java.util.Iterator;
// import java.util.LinkedList;
// import java.util.List;
// import java.util.Set;
// import java.util.concurrent.CountDownLatch;
// import java.util.concurrent.atomic.AtomicBoolean;
// import org.json.JSONException;
// import org.json.JSONObject;

// public class ActionService extends JobIntentService {
//   // TODO(mattcarroll): tags should be private. Make private if no public usage.
//   public static final String TAG = "ActionService";
//   private static final String CALLBACK_HANDLE_KEY = "action_callback_handle";
//   private static final String PERSISTENT_ACTIONS_SET_KEY = "persistent_actions_ids";
//   private static final String SHARED_PREFERENCES_KEY = "com.dexterous.flutterlocalnotifications";
//   private static final int JOB_ID = 999; // Random job ID.
//   private static final Object sPersistentAlarmsLock = new Object();

//   // TODO(mattcarroll): make sIsIsolateRunning per-instance, not static.
//   private static AtomicBoolean sIsIsolateRunning = new AtomicBoolean(false);

//   // TODO(mattcarroll): make sAlarmQueue per-instance, not static.
//   private static List<Intent> sAlarmQueue = Collections.synchronizedList(new LinkedList<Intent>());

//   /** Background Dart execution context. */
//   private static FlutterNativeView sBackgroundFlutterView;

//   /**
//    * The {@link MethodChannel} that connects the Android side of this plugin with the background
//    * Dart isolate that was created by this plugin.
//    */
//   private static MethodChannel sBackgroundChannel;

//   private static PluginRegistrantCallback sPluginRegistrantCallback;

//   // Schedule the alarm to be handled by the AlarmService.
//   public static void enqueueActionProcessing(Context context, Intent alarmContext) {
//     enqueueWork(context, ActionService.class, JOB_ID, alarmContext);
//   }

//   /**
//    * Starts running a background Dart isolate within a new {@link FlutterNativeView}.
//    *
//    * <p>The isolate is configured as follows:
//    *
//    * <ul>
//    *   <li>Bundle Path: {@code FlutterMain.findAppBundlePath(context)}.
//    *   <li>Entrypoint: The Dart method represented by {@code callbackHandle}.
//    *   <li>Run args: none.
//    * </ul>
//    *
//    * <p>Preconditions:
//    *
//    * <ul>
//    *   <li>The given {@code callbackHandle} must correspond to a registered Dart callback. If the
//    *       handle does not resolve to a Dart callback then this method does nothing.
//    *   <li>A static {@link #sPluginRegistrantCallback} must exist, otherwise a {@link
//    *       PluginRegistrantException} will be thrown.
//    * </ul>
//    */
//   public static void startBackgroundIsolate(Context context, long callbackHandle) {
//     // TODO(mattcarroll): re-arrange order of operations. The order is strange - there are 3
//     // conditions that must be met for this method to do anything but they're split up for no
//     // apparent reason. Do the qualification checks first, then execute the method's logic.
//     FlutterMain.ensureInitializationComplete(context, null);
//     String mAppBundlePath = FlutterMain.findAppBundlePath(context);
//     FlutterCallbackInformation flutterCallback =
//         FlutterCallbackInformation.lookupCallbackInformation(callbackHandle);
//     if (flutterCallback == null) {
//       Log.e(TAG, "Fatal: failed to find callback");
//       return;
//     }

//     // Note that we're passing `true` as the second argument to our
//     // FlutterNativeView constructor. This specifies the FlutterNativeView
//     // as a background view and does not create a drawing surface.
//     sBackgroundFlutterView = new FlutterNativeView(context, true);
//     if (mAppBundlePath != null && !sIsIsolateRunning.get()) {
//       if (sPluginRegistrantCallback == null) {
//         throw new PluginRegistrantException();
//       }
//       Log.i(TAG, "Starting AlarmService...");
//       FlutterRunArguments args = new FlutterRunArguments();
//       args.bundlePath = mAppBundlePath;
//       args.entrypoint = flutterCallback.callbackName;
//       args.libraryPath = flutterCallback.callbackLibraryPath;
//       sBackgroundFlutterView.runFromBundle(args);
//       sPluginRegistrantCallback.registerWith(sBackgroundFlutterView.getPluginRegistry());
//     }
//   }

//   /**
//    * Called once the Dart isolate ({@code sBackgroundFlutterView}) has finished initializing.
//    *
//    * <p>Invoked by {@link AndroidAlarmManagerPlugin} when it receives the {@code
//    * AlarmService.initialized} message. Processes all alarm events that came in while the isolate
//    * was starting.
//    */
//   // TODO(mattcarroll): consider making this method package private
//   public static void onInitialized() {
//     Log.i(TAG, "ActionService started!");
//     sIsIsolateRunning.set(true);
//     synchronized (sAlarmQueue) {
//       // Handle all the alarm events received before the Dart isolate was
//       // initialized, then clear the queue.
//       Iterator<Intent> i = sAlarmQueue.iterator();
//       while (i.hasNext()) {
//         executeDartCallbackInBackgroundIsolate(i.next(), null);
//       }
//       sAlarmQueue.clear();
//     }
//   }

//   /**
//    * Sets the {@link MethodChannel} that is used to communicate with Dart callbacks that are invoked
//    * in the background by the android_alarm_manager plugin.
//    */
//   public static void setBackgroundChannel(MethodChannel channel) {
//     sBackgroundChannel = channel;
//   }

//   /**
//    * Sets the Dart callback handle for the Dart method that is responsible for initializing the
//    * background Dart isolate, preparing it to receive Dart callback tasks requests.
//    */
//   public static void setCallbackDispatcher(Context context, long callbackHandle) {
//     SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_KEY, 0);
//     prefs.edit().putLong(CALLBACK_HANDLE_KEY, callbackHandle).apply();
//   }

//   public static boolean setBackgroundFlutterView(FlutterNativeView view) {
//     if (sBackgroundFlutterView != null && sBackgroundFlutterView != view) {
//       Log.i(TAG, "setBackgroundFlutterView tried to overwrite an existing FlutterNativeView");
//       return false;
//     }
//     sBackgroundFlutterView = view;
//     return true;
//   }

//   public static void setPluginRegistrant(PluginRegistrantCallback callback) {
//     sPluginRegistrantCallback = callback;
//   }

//   /**
//    * Executes the desired Dart callback in a background Dart isolate.
//    *
//    * <p>The given {@code intent} should contain a {@code long} extra called "callbackHandle", which
//    * corresponds to a callback registered with the Dart VM.
//    */
//   private static void executeDartCallbackInBackgroundIsolate(
//       Intent intent, final CountDownLatch latch) {
//     // Grab the handle for the callback associated with this alarm. Pay close
//     // attention to the type of the callback handle as storing this value in a
//     // variable of the wrong size will cause the callback lookup to fail.
//     long callbackHandle = intent.getLongExtra("callbackHandle", 0);
//     if (sBackgroundChannel == null) {
//       Log.e(
//           TAG,
//           "setBackgroundChannel was not called before alarms were scheduled." + " Bailing out.");
//       return;
//     }

//     // If another thread is waiting, then wake that thread when the callback returns a result.
//     MethodChannel.Result result = null;
//     if (latch != null) {
//       result =
//           new MethodChannel.Result() {
//             @Override
//             public void success(Object result) {
//               latch.countDown();
//             }

//             @Override
//             public void error(String errorCode, String errorMessage, Object errorDetails) {
//               latch.countDown();
//             }

//             @Override
//             public void notImplemented() {
//               latch.countDown();
//             }
//           };
//     }

//     // Handle the alarm event in Dart. Note that for this plugin, we don't
//     // care about the method name as we simply lookup and invoke the callback
//     // provided.
//     // TODO(mattcarroll): consider giving a method name anyway for the purpose of developer discoverability
//     //                    when reading the source code. Especially on the Dart side.
//     sBackgroundChannel.invokeMethod("", new Object[] {callbackHandle}, result);
//   }

//   @Override
//   public void onCreate() {
//     super.onCreate();

//     Context context = getApplicationContext();
//     FlutterMain.ensureInitializationComplete(context, null);

//     if (!sIsIsolateRunning.get()) {
//       SharedPreferences p = context.getSharedPreferences(SHARED_PREFERENCES_KEY, 0);
//       long callbackHandle = p.getLong(CALLBACK_HANDLE_KEY, 0);
//       startBackgroundIsolate(context, callbackHandle);
//     }
//   }

//   /**
//    * Executes a Dart callback, as specified within the incoming {@code intent}.
//    *
//    * <p>Invoked by our {@link JobIntentService} superclass after a call to {@link
//    * JobIntentService#enqueueWork(Context, Class, int, Intent);}.
//    *
//    * <p>If there are no pre-existing callback execution requests, other than the incoming {@code
//    * intent}, then the desired Dart callback is invoked immediately.
//    *
//    * <p>If there are any pre-existing callback requests that have yet to be executed, the incoming
//    * {@code intent} is added to the {@link #sAlarmQueue} to invoked later, after all pre-existing
//    * callbacks have been executed.
//    */
//   @Override
//   protected void onHandleWork(final Intent intent) {
//     // If we're in the middle of processing queued alarms, add the incoming
//     // intent to the queue and return.
//     synchronized (sAlarmQueue) {
//       if (!sIsIsolateRunning.get()) {
//         Log.i(TAG, "AlarmService has not yet started.");
//         sAlarmQueue.add(intent);
//         return;
//       }
//     }

//     // There were no pre-existing callback requests. Execute the callback
//     // specified by the incoming intent.
//     final CountDownLatch latch = new CountDownLatch(1);
//     new Handler(getMainLooper())
//         .post(
//             new Runnable() {
//               @Override
//               public void run() {
//                 executeDartCallbackInBackgroundIsolate(intent, latch);
//               }
//             });

//     try {
//       latch.await();
//     } catch (InterruptedException ex) {
//       Log.i(TAG, "Exception waiting to execute Dart callback", ex);
//     }
//   }
// }
