package dk.quietsnap;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Size;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Surface;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class CaptureService extends Service {
    private static final String CHANNEL = "quietsnap_capture";
    private HandlerThread thread;
    private Handler handler;
    private CameraDevice camera;
    private ImageReader reader;
    private CameraCaptureSession session;
    private PowerManager.WakeLock wakeLock;
    private boolean saved = false;

    @Override public void onCreate() {
        super.onCreate(); createChannel();
        Notification n = new Notification.Builder(this, CHANNEL).setContentTitle("QuietSnap aktiv").setContentText("Tager planlagt billede").setSmallIcon(android.R.drawable.ic_menu_camera).setOngoing(true).build();
        startForeground(7, n);
        thread = new HandlerThread("QuietSnapCamera"); thread.start(); handler = new Handler(thread.getLooper());
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE); wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "QuietSnap:Capture"); wakeLock.acquire(45000L);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { openCamera(); return START_NOT_STICKY; }
    private void openCamera() {
        try {
            CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE); String chosen = null;
            int wantedFacing = android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK;
            if (BuildConfig.CAMERA_SELECTABLE && getSharedPreferences("settings", MODE_PRIVATE).getInt("camera_facing", 0) == 1) {
                wantedFacing = android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT;
            }
            for (String id : manager.getCameraIdList()) { Integer facing = manager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING); if (facing != null && facing == wantedFacing) { chosen = id; break; } }
            if (chosen == null) { finish(); return; }
            // Use a JPEG size actually supported by this camera. A hard-coded size can
            // produce a failed or unreliable camera session on some devices.
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(chosen);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size selectedSize = new Size(1920, 1080);
            if (map != null) {
                Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
                if (sizes != null && sizes.length > 0) {
                    // Prefer the largest supported size up to roughly 12 MP.
                    selectedSize = sizes[0];
                    long bestPixels = 0;
                    for (Size size : sizes) {
                        long pixels = (long) size.getWidth() * size.getHeight();
                        if (pixels <= 12_000_000L && pixels > bestPixels) {
                            selectedSize = size;
                            bestPixels = pixels;
                        }
                    }
                }
            }
            reader = ImageReader.newInstance(selectedSize.getWidth(), selectedSize.getHeight(), ImageFormat.JPEG, 3);
            reader.setOnImageAvailableListener(r -> saveImage(r), handler);
            manager.openCamera(chosen, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice c) { camera = c; createSession(); }
                @Override public void onDisconnected(CameraDevice c) { c.close(); camera = null; finish(); }
                @Override public void onError(CameraDevice c, int error) { c.close(); camera = null; finish(); }
            }, handler);
        } catch (Exception e) { finish(); }
    }
    private void createSession() {
        try {
            camera.createCaptureSession(Arrays.asList(reader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession s) {
                    session = s;
                    // Give the camera a moment to initialise exposure/white balance after
                    // opening, then issue a still request with explicit auto settings.
                    handler.postDelayed(() -> {
                        try {
                            if (camera == null || session == null || reader == null) { finish(); return; }
                            CaptureRequest.Builder b = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                            b.addTarget(reader.getSurface());
                            b.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                            b.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
                            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            b.set(CaptureRequest.JPEG_QUALITY, (byte)95);
                            session.capture(b.build(), new CameraCaptureSession.CaptureCallback() {
                                @Override public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, android.hardware.camera2.CaptureFailure failure) {
                                    finish();
                                }
                            }, handler);
                        } catch (Exception e) { finish(); }
                    }, 1200L);
                }
                @Override public void onConfigureFailed(CameraCaptureSession s) { finish(); }
            }, handler);
        } catch (Exception e) { finish(); }
    }
    private void saveImage(ImageReader r) {
        Image image = null;
        try {
            image = r.acquireNextImage(); if (image == null || saved) return; saved = true;
            ByteBuffer buffer = image.getPlanes()[0].getBuffer(); byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes);
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "QuietSnap");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("Kan ikke oprette mappe");
            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
            File out = new File(dir, "QuietSnap_" + stamp + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(bytes); fos.flush(); }
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, android.net.Uri.fromFile(out)));
        } catch (Exception e) {
            android.util.Log.e("QuietSnap", "Could not save captured image", e);
        }
        finally { if (image != null) image.close(); finish(); }
    }
    private void finish() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> { try { if (session != null) session.close(); } catch (Exception ignored) {} try { if (camera != null) camera.close(); } catch (Exception ignored) {} try { if (reader != null) reader.close(); } catch (Exception ignored) {} try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {} stopForeground(true); stopSelf(); }, 250);
    }
    private void createChannel() { if (Build.VERSION.SDK_INT >= 26) { NotificationChannel c = new NotificationChannel(CHANNEL, "QuietSnap kamera", NotificationManager.IMPORTANCE_LOW); ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c); } }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { try { if (thread != null) thread.quitSafely(); } catch (Exception ignored) {} super.onDestroy(); }
}
