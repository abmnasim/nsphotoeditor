package com.example.nsphotoeditor;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

public class MainActivity extends AppCompatActivity {

    ActivityResultLauncher<Intent> pickImageLauncher;

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

        setupPickImageCallback();

//        findViewById(R.id.open_sheet_btn).setOnClickListener(view -> openBottomSheet());
//        openBottomSheet();
    }

    @Override
    protected void onResume() {
        super.onResume();

        openBottomSheet();
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

    private void openBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_bottom_sheet, null);
        Button openGalleryBtn = view.findViewById(R.id.gallery_btn);
        openGalleryBtn.setOnClickListener(v -> {
//            dialog.dismiss();
            pickImageFromGallery();
        });
        dialog.setContentView(view);
        dialog.show();
    }

    private void pickImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }
}