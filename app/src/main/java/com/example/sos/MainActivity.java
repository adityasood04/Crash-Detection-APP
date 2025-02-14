package com.example.sos;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.RetryPolicy;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

public class MainActivity extends AppCompatActivity {

    private TextView angleXText, angleYText, tvMain;
    private LinearLayout llOk;
    private Handler handler = new Handler();
    private String ESP_URL = "http://192.168.254.147/";
    private String ESP_IP = "192.168.254.147";
    private RequestQueue requestQueue;
    private Handler sosHandler = new Handler();
    private Runnable sosRunnable;
    private boolean isDialogVisible = false;
    private boolean isSOSCancelled = false;
    private String SOS_NUMBER;
    private String SOS_MESSAGE;
    private String LOCATION;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        angleXText = findViewById(R.id.angleXText);
        angleYText = findViewById(R.id.angleYText);
        tvMain = findViewById(R.id.tvConnection);
        llOk = findViewById(R.id.llFine);
        requestQueue = Volley.newRequestQueue(this);
        SOS_NUMBER = "+91" + getIntent().getStringExtra("PHONE");
        SOS_MESSAGE = "Emergency. Vehicle number " + getIntent().getStringExtra("VEHICLE") + " has crashed.";
        LOCATION = getIntent().getStringExtra("LOCATION");

        startFetchingData();
    }

    private void startFetchingData() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isDialogVisible) {
                    fetchData();
                }
                handler.postDelayed(this, 2000);
            }
        }, 1000);
    }

    private void fetchData() {
        int timeoutMs = 8000;
        RetryPolicy retryPolicy = new DefaultRetryPolicy(
                timeoutMs,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        );
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, ESP_URL, null,
                response -> {
                    try {
                        double angleX = response.getDouble("angleX");
                        double angleY = response.getDouble("angleY");
                        tvMain.setText("Connected to device\nIP : "+ESP_IP);
                        angleXText.setText("Tilt X: " + angleX + "°");
                        angleYText.setText("Tilt Y: " + angleY + "°");
                        llOk.setVisibility(View.VISIBLE);
                        if ((Math.abs(angleX) > 60 || Math.abs(angleY) > 60) && !isDialogVisible) {
                            showAlertDialog();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, Throwable::printStackTrace);

        request.setRetryPolicy(retryPolicy);
        requestQueue.add(request);
    }

    private void showAlertDialog() {
        isDialogVisible = true;
        isSOSCancelled = false;

        new AlertDialog.Builder(this)
                .setTitle("🚨 Vehicle Crashed! 🚨")
                .setMessage("Sending SOS message in 5 seconds...\nPress 'Cancel' if this is a mistake.")
                .setIcon(R.drawable.baseline_warning_24)
                .setCancelable(false)
                .setPositiveButton("Cancel SOS", (dialog, which) -> {
                    sosHandler.removeCallbacks(sosRunnable);
                    isSOSCancelled = true;
                    isDialogVisible = false;
                    Toast.makeText(MainActivity.this, "SOS Canceled!", Toast.LENGTH_SHORT).show();
                })
                .show();

        sosRunnable = () -> {
            if (isDialogVisible && !isSOSCancelled) {
                sendSOSMessage();
            }
        };
        sosHandler.postDelayed(sosRunnable, 5000);
    }

    private void sendSOSMessage() {
        isDialogVisible = false;
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(SOS_NUMBER, null, SOS_MESSAGE + ". Last location was " + LOCATION, null, null);
            Log.i("esp", "Message sent successfully");
            Toast.makeText(this, "🚨 SOS Message Sent! 🚨", Toast.LENGTH_LONG).show();
            finish();
        } catch (Exception e) {
            Log.i("esp sms", "sendSOSMessage: error " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sosHandler.removeCallbacks(sosRunnable);
    }
}
