package tbt.lab7.myapplication;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final String TAG = "MainActivity";
    private static final String SERVER_URL = "http://192.168.1.7:5000/upload_and_predict";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageView catImageView = findViewById(R.id.catImageView);
        ImageView sticker1 = findViewById(R.id.sticker1ImageView);

        // Click listener for placeholder_image
        catImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGallery();
            }
        });

        // Click listener for sticker1
        sticker1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendImageToServer();
            }
        });
    }

    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, PICK_IMAGE_REQUEST);
    }

    private void sendImageToServer() {
        ImageView catImageView = findViewById(R.id.catImageView);

        // Convert ImageView to Bitmap
        Bitmap bitmap = ((BitmapDrawable) catImageView.getDrawable()).getBitmap();

        // Convert Bitmap to ByteArrayOutputStream
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream); // Compress Bitmap
        byte[] byteArray = stream.toByteArray();

        // Create RequestBody using byte array
        RequestBody requestBody = RequestBody.create(MediaType.parse("image/jpeg"), byteArray);

        // Create MultipartBody.Part using file name, MediaType and RequestBody
        MultipartBody.Part body = MultipartBody.Part.createFormData("image", "image.jpg", requestBody);

        // Prepare the Request
        Request request = new Request.Builder()
                .url("http://192.168.1.7:5000/upload_and_predict")
                .header("Connection", "close")
                .post(new MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addPart(body)
                        .build())
                .build();
        Toast.makeText(this, "Sending image to server", Toast.LENGTH_SHORT).show();
        // Asynchronously send the request
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS) // Increase connection timeout
                .writeTimeout(30, TimeUnit.SECONDS)   // Increase write timeout for sending data
                .readTimeout(30, TimeUnit.SECONDS).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                // Handle failure
                e.printStackTrace();
            }

            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                // Check if the response is successful and contains data
                Log.d("Response Testing", "onResponse: " + response);
                if (response.isSuccessful() && response.body() != null) {
                    // Convert the InputStream to a byte array
                    byte[] imageData = response.body().bytes();

                    // Convert byte array to Bitmap (make sure to do this on the UI thread)
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);

                    // Update the ImageView on the UI thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Image received from server", Toast.LENGTH_SHORT).show();
                            ImageView imageView = findViewById(R.id.catImageView);
                            imageView.setImageBitmap(bitmap);
                        }
                    });
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            // Get the selected image URI
            Uri selectedImageUri = data.getData();
            // Set the selected image to the catImageView
            ImageView catImageView = findViewById(R.id.catImageView);
            catImageView.setImageURI(selectedImageUri);
        }
    }
}