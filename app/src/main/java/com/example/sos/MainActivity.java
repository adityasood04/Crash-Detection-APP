package com.example.sos;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.util.Log;
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

    private TextView angleXText, angleYText;
    private Handler handler = new Handler();
    private String ESP_URL = "http://192.168.254.147/";
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
                if (!isDialogVisible) { // Fetch only when no alert is shown
                    fetchData();
                }
//                fetchData();
                handler.postDelayed(this, 3000); // Fetch every 3 seconds
            }
        }, 1000);
    }

    private void fetchData() {
        int timeoutMs = 8000; // Increase timeout to 8 seconds
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

                        angleXText.setText("Angle X: " + angleX + "°");
                        angleYText.setText("Angle Y: " + angleY + "°");

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
                .setMessage("Sending SOS message in 6 seconds...\nPress 'Cancel' if this is a mistake.")
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
        sosHandler.postDelayed(sosRunnable, 6000);
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
