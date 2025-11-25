package com.example.nsphotoeditor;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nsphotoeditor.adapters.FeatureAdapter;
import com.example.nsphotoeditor.adapters.RecentAdapter;
import com.example.nsphotoeditor.callbacks.PreviewImageListener;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class MainActivity extends AppCompatActivity implements PreviewImageListener {

    ActivityResultLauncher<Intent> pickImageLauncher;

    Button openCameraBtn, openGalleryBtn;

    List<String> recentImages = new ArrayList<>();
    RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        recyclerView = findViewById(R.id.rv_recent_images);
        openCameraBtn = findViewById(R.id.camera_btn);
        openGalleryBtn = findViewById(R.id.gallery_btn);

        setupPickImageCallback();

        recentImages = loadRecentImages();
        // Pass list to RecyclerView adapter
        RecentAdapter adapter = new RecentAdapter(recentImages, this);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerView.setAdapter(adapter);

        openGalleryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImageFromGallery();
            }
        });
    }

    private void setupPickImageCallback() {
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri selected = result.getData().getData();
                assert selected != null;
                openEditor(selected);
            }
        });
    }

    private void openEditor(Uri selected) {
        Intent intent = new Intent(MainActivity.this, EditorActivity.class);
        intent.putExtra("image_uri", selected.toString());
        startActivity(intent);
    }

//    private void openBottomSheet() {
//        BottomSheetDialog dialog = new BottomSheetDialog(this);
//        View view = getLayoutInflater().inflate(R.layout.dialog_bottom_sheet, null);
//        Button openGalleryBtn = view.findViewById(R.id.gallery_btn);
//        openGalleryBtn.setOnClickListener(v -> {
////            dialog.dismiss();
//            pickImageFromGallery();
//        });
//        dialog.setContentView(view);
//        dialog.setCancelable(false);
//        Window window = dialog.getWindow();
//        if (window != null) {
//            WindowManager.LayoutParams lp = window.getAttributes();
//            lp.dimAmount = 0.0f;
//            window.setAttributes(lp);
//        }
//        dialog.show();
//    }

    private void pickImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    private List<String> loadRecentImages() {
        SharedPreferences prefs = getSharedPreferences("recent_images", MODE_PRIVATE);
        String json = prefs.getString("images_json", "[]");

        List<String> list = new ArrayList<>();

        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                list.add(array.getString(i));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    @Override
    public void onClickPreviewImage(Uri uri) {
        Intent intent = new Intent(this, FullImageActivity.class);
        intent.putExtra("image", uri.toString());
        startActivity(intent);

        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}