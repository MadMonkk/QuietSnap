package dk.quietsnap;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_PERMS = 41;
    private SharedPreferences prefs;
    private TextView time1Text, time2Text;
    private CheckBox secondEnabled;
    private RadioGroup cameraChoice;
    private int t1 = 8 * 60, t2 = 20 * 60;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        t1 = prefs.getInt("t1", 8 * 60); t2 = prefs.getInt("t2", 20 * 60);
        buildUi();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERMS);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28, 32, 28, 24); root.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = new TextView(this); title.setText("QuietSnap"); title.setTextSize(28); title.setPadding(0, 0, 0, 10); root.addView(title);
        TextView desc = new TextView(this); desc.setText("Tager automatisk billeder med bagkameraet, også når skærmen er slukket."); desc.setTextSize(16); desc.setPadding(0, 0, 0, 24); root.addView(desc);
        if (BuildConfig.CAMERA_SELECTABLE) {
            TextView cameraLabel = new TextView(this); cameraLabel.setText("Kamera"); cameraLabel.setTextSize(18); root.addView(cameraLabel);
            cameraChoice = new RadioGroup(this); cameraChoice.setOrientation(RadioGroup.VERTICAL);
            RadioButton back = new RadioButton(this); back.setText("Bagkamera"); back.setId(1); cameraChoice.addView(back);
            RadioButton front = new RadioButton(this); front.setText("Frontkamera"); front.setId(2); cameraChoice.addView(front);
            int facing = prefs.getInt("camera_facing", 0);
            cameraChoice.check(facing == 1 ? 2 : 1);
            root.addView(cameraChoice);
        }
        time1Text = new TextView(this); time1Text.setTextSize(18); root.addView(time1Text);
        Button choose1 = new Button(this); choose1.setText("Vælg tidspunkt 1"); choose1.setOnClickListener(v -> pickTime(1)); root.addView(choose1);
        secondEnabled = new CheckBox(this); secondEnabled.setText("Tag også et billede mere hver dag"); secondEnabled.setChecked(prefs.getBoolean("second", true)); root.addView(secondEnabled);
        time2Text = new TextView(this); time2Text.setTextSize(18); root.addView(time2Text);
        Button choose2 = new Button(this); choose2.setText("Vælg tidspunkt 2"); choose2.setOnClickListener(v -> pickTime(2)); root.addView(choose2);
        Button save = new Button(this); save.setText("Gem og aktivér"); save.setOnClickListener(v -> saveSettings()); root.addView(save);
        TextView note = new TextView(this); note.setText("Billeder gemmes i DCIM/QuietSnap. Hold appen undtaget fra batterioptimering for bedst mulig drift."); note.setTextSize(14); note.setPadding(0, 24, 0, 0); root.addView(note);
        setContentView(root); refreshTimes();
    }
    private void refreshTimes() { time1Text.setText("Billede 1: " + fmt(t1)); time2Text.setText("Billede 2: " + fmt(t2)); }
    private String fmt(int m) { return String.format(Locale.getDefault(), "%02d:%02d", m / 60, m % 60); }
    private void pickTime(int which) {
        int val = which == 1 ? t1 : t2;
        new TimePickerDialog(this, (view, h, m) -> { if (which == 1) t1 = h * 60 + m; else t2 = h * 60 + m; refreshTimes(); }, val / 60, val % 60, true).show();
    }
    private void saveSettings() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Giv kamera- og lageradgang først.", Toast.LENGTH_LONG).show(); return;
        }
        SharedPreferences.Editor editor = prefs.edit().putInt("t1", t1).putInt("t2", t2).putBoolean("second", secondEnabled.isChecked());
        if (BuildConfig.CAMERA_SELECTABLE && cameraChoice != null) editor.putInt("camera_facing", cameraChoice.getCheckedRadioButtonId() == 2 ? 1 : 0);
        editor.apply();
        AlarmScheduler.schedule(this); Toast.makeText(this, "Planen er gemt og aktiveret.", Toast.LENGTH_LONG).show();
    }
    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) { super.onRequestPermissionsResult(r,p,g); if (r == REQ_PERMS && (g.length == 0 || g[0] != PackageManager.PERMISSION_GRANTED)) Toast.makeText(this, "Kameratilladelse er nødvendig.", Toast.LENGTH_LONG).show(); }
}
