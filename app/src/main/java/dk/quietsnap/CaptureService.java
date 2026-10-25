package dk.quietsnap;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
public class CaptureService extends Service {
    private static final String CHANNEL = "quietsnap_capture";
    private static final String TAG = "QuietSnap";
    private HandlerThread thread;
    private Handler handler;
    private Camera camera;
    private SurfaceTexture dummyTexture;
    private PowerManager.WakeLock wakeLock;
    private boolean finished = false;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("QuietSnap aktiv")
                .setContentText("Tager planlagt billede")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true).build();
        startForeground(7, n);
        thread = new HandlerThread("QuietSnapCamera1");
        thread.start();
        handler = new Handler(thread.getLooper());
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "QuietSnap:Capture");
        wakeLock.acquire(45000L);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        handler.post(this::openAndCapture);
        return START_NOT_STICKY;
    }

    private void openAndCapture() {
        try {
            int wantedFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
            if (BuildConfig.CAMERA_SELECTABLE && getSharedPreferences("settings", MODE_PRIVATE).getInt("camera_facing", 0) == 1) {
                wantedFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
            }
            int chosen = -1;
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == wantedFacing) { chosen = i; break; }
            }
            if (chosen < 0) { Log.e(TAG, "No matching camera found"); finishCapture(); return; }
            camera = Camera.open(chosen);
            Camera.Parameters params = camera.getParameters();
            List<Camera.Size> sizes = params.getSupportedPictureSizes();
            if (sizes != null && !sizes.isEmpty()) {
                Camera.Size best = sizes.get(0);
                long bestPixels = 0;
                for (Camera.Size s : sizes) {
                    long px = (long)s.width * s.height;
                    if (px <= 12000000L && px > bestPixels) { best = s; bestPixels = px; }
                }
                params.setPictureSize(best.width, best.height);
            }
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes != null) {
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }
            camera.setParameters(params);
            // Camera1 needs a preview target even when no visible preview is desired.
            dummyTexture = new SurfaceTexture(10);
            camera.setPreviewTexture(dummyTexture);
            camera.startPreview();
            handler.postDelayed(() -> {
                if (camera == null || finished) return;
                try {
                    camera.takePicture(null, null, (data, cam) -> {
                        try { saveJpeg(data); }
                        catch (Exception e) { Log.e(TAG, "Failed saving JPEG", e); }
                        finally { finishCapture(); }
                    });
                } catch (Exception e) { Log.e(TAG, "takePicture failed", e); finishCapture(); }
            }, 2500L);
        } catch (Exception e) {
            Log.e(TAG, "Camera1 open/capture failed", e);
            finishCapture();
        }
    }

    private void saveJpeg(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length < 1000) throw new Exception("Camera returned empty/too-small JPEG");
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "QuietSnap");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Could not create DCIM/QuietSnap");
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        File out = new File(dir, "QuietSnap_" + stamp + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(bytes); fos.flush(); }
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, android.net.Uri.fromFile(out)));
        Log.i(TAG, "Saved JPEG: " + out.getAbsolutePath() + " bytes=" + bytes.length);
    }

    private void finishCapture() {
        if (finished) return;
        finished = true;
        handler.post(() -> {
            try { if (camera != null) { try { camera.stopPreview(); } catch (Exception ignored) {} camera.release(); camera = null; } } catch (Exception ignored) {}
            try { if (dummyTexture != null) { dummyTexture.release(); dummyTexture = null; } } catch (Exception ignored) {}
            try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
            stopForeground(true);
            stopSelf();
        });
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "QuietSnap kamera", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        try { if (camera != null) { camera.release(); camera = null; } } catch (Exception ignored) {}
        try { if (dummyTexture != null) dummyTexture.release(); } catch (Exception ignored) {}
        try { if (thread != null) thread.quitSafely(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
