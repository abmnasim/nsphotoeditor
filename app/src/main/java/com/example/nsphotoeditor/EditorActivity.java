package com.example.nsphotoeditor;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nsphotoeditor.adapters.FeatureAdapter;
import com.example.nsphotoeditor.enums.FeatureType;
import com.example.nsphotoeditor.features.BrightnessOperation;
import com.example.nsphotoeditor.features.ContrastOperation;
import com.example.nsphotoeditor.features.ExposureOperation;
import com.example.nsphotoeditor.features.FlipOperation;
import com.example.nsphotoeditor.features.PixelateOperation;
import com.example.nsphotoeditor.features.RotationOperation;
import com.example.nsphotoeditor.features.SaturationOperation;
import com.example.nsphotoeditor.features.VignetteOperation;
import com.example.nsphotoeditor.features.WarmthOperation;
import com.example.nsphotoeditor.utils.BitmapUtils;
import com.example.nsphotoeditor.utils.FeatureConfig;
import com.example.nsphotoeditor.utils.FeatureItem;
import com.example.nsphotoeditor.utils.FeatureState;
import com.example.nsphotoeditor.utils.LoadingDialog;

import org.json.JSONArray;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class EditorActivity extends AppCompatActivity {
    private static final String TAG = "EDITOR";
    private ImageView imageView;
    ImageButton btnFlip, btnUndo, btnRedo, btnBack;
    Button btnSave, btnCancel, btnApply, btnReset;
    private RecyclerView rvFeatures, rvFrames;
    private View controllerContainer, frameControllerContainer;
    private TextView controllerTitle;
    private SeekBar seekBar;
    private Bitmap baseBitmap, currentBitmap;

    private final int SEEKBAR_MAX = 200;
    private final HashMap<String, FeatureState> featureStates = new HashMap<>();
    private final HashMap<String, FeatureConfig> featureConfigs = new HashMap<>();
    private final Stack<HashMap<String, FeatureState>> undoStack = new Stack<>();
    private final Stack<HashMap<String, FeatureState>> redoStack = new Stack<>();

    private FeatureItem selectedFeature = null;
    private long lastRebuildMs = 0;
    private static final long REBUILD_DEBOUNCE_MS = 60;

//    private Bitmap[] frameBitmaps = new Bitmap[11];

    // Variables for Save
    LoadingDialog loadingDialog = new LoadingDialog(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_editor);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        imageView = findViewById(R.id.image_view);
        btnBack = findViewById(R.id.back_btn);
//        btnUndo = findViewById(R.id.undo_btn);
//        btnRedo = findViewById(R.id.redo_btn);
        btnSave = findViewById(R.id.btn_save);
        btnFlip = findViewById(R.id.flip_preview_btn);

        btnReset = findViewById(R.id.btn_reset);
        btnCancel = findViewById(R.id.btn_cancel);
        btnApply = findViewById(R.id.btn_apply);

        rvFeatures = findViewById(R.id.rv_features);
//        rvFrames = findViewById(R.id.rv_frames);
        controllerContainer = findViewById(R.id.controller_container);
//        frameControllerContainer = findViewById(R.id.frame_controller_container);
        controllerTitle = findViewById(R.id.operation_name);

        seekBar = findViewById(R.id.seekbar);

        initFeatureConfigs();
        initFeatureStates();
//        loadFrames();

        // Load image from intent
        Uri uri = null;
        String u = getIntent().getStringExtra("image_uri");
        if (u != null) uri = Uri.parse(u);
        if (uri == null && getIntent().getData() != null) uri = getIntent().getData();
        if (uri == null) {
            finish();
        }

        try {
            baseBitmap = BitmapUtils.loadSampledBitmap(this, uri, 1080, 1920);
        } catch (IOException e) {
            Log.e(TAG, "onCreate: ", e.fillInStackTrace());
            finish();
            return;
        }

        currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);

        imageView.setImageBitmap(currentBitmap);

        setupFeatureList();
        setupTopButtons();
        setupCompareButton();
        setupControllerActions();

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveOutput();
            }
        });
    }

    private void saveOutput() {
        Bitmap bitmapToSave = (currentBitmap != null) ? currentBitmap : baseBitmap;

        loadingDialog.show();

        new Thread(() -> {
            Uri savedUri = saveImagePNG(bitmapToSave);

            runOnUiThread(() -> {
                loadingDialog.dismiss();

                if (savedUri != null) {
                    Toast.makeText(this, "Saved Successfully!", Toast.LENGTH_SHORT).show();

                    saveToRecentList(savedUri.toString());
                    shareImage(savedUri);
                }else{
                    Toast.makeText(this, "Failed to Save!", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private Uri saveImagePNG(Bitmap bitmap) {
        String filename = "NSPhoto_" + System.currentTimeMillis() + ".png";
        OutputStream fos;
        Uri imageUri = null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");;
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HSPhotoEditor");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);

                imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                fos = getContentResolver().openOutputStream(imageUri);

                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();

                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(imageUri, values, null, null);
            }else{
                File dir = new File(Environment.getExternalStorageDirectory() + "/Pictures/NSPhotoEditor");
                if (!dir.exists()) dir.mkdir();

                File file = new File(dir, filename);
                fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();

                imageUri = Uri.fromFile(file);

                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, imageUri));
            }

            return imageUri;

        } catch (IOException e) {
            Log.e(TAG, "saveImagePNG: ", e.fillInStackTrace());
//            throw new RuntimeException(e);
            return null;

        }
    }

    private void shareImage(Uri uri) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/png");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(shareIntent, "Share Image"));
    }

    private void saveToRecentList(String uri) {
        SharedPreferences prefs = getSharedPreferences("recent_images", MODE_PRIVATE);
        String json = prefs.getString("images_json", "[]");

        try {
            JSONArray array = new JSONArray(json);

            // remove duplicate if exists
            for (int i = 0; i < array.length(); i++) {
                if (array.getString(i).equals(uri)) {
                    array.remove(i);
                    break;
                }
            }

            // add new image at end (or beginning if you want reverse)
            array.put(uri);

            prefs.edit().putString("images_json", array.toString()).apply();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    private void loadFrames() {
//        frameBitmaps[1] = BitmapFactory.decodeResource(getResources(), R.drawable.frame_1);
//        frameBitmaps[2] = BitmapFactory.decodeResource(getResources(), R.drawable.frame_2);
//        frameBitmaps[3] = BitmapFactory.decodeResource(getResources(), R.drawable.frame_3);
//        frameBitmaps[4] = BitmapFactory.decodeResource(getResources(), R.drawable.frame_4);
//        frameBitmaps[5] = BitmapFactory.decodeResource(getResources(), R.drawable.frame_5);
//        frameBitmaps[6] = BitmapFactory.decodeResource(getResources(), R.drawable.frame_6);
//        frameBitmaps[7] = BitmapFactory.decodeResource(getResources(), R.drawable.frame_7);
//        frameBitmaps[8] = BitmapFactory.decodeResource(getResources(), R.drawable.frame_8);
//        frameBitmaps[9] = BitmapFactory.decodeResource(getResources(), R.drawable.frame_9);
//        frameBitmaps[10] = BitmapFactory.decodeResource(getResources(), R.drawable.frame_10);
//    }

    private void setupTopButtons() {
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(EditorActivity.this, MainActivity.class));
                finish();
            }
        });

//        btnUndo.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (!undoStack.isEmpty()) {
//                    // push current to redo
//                    pushRedoSnapshot();
//                    HashMap<String, FeatureState> prev = undoStack.pop();
//                    // replace featureStates with prev
//                    featureStates.clear();
//
//                    featureStates.putAll(deepCopyFeatureStates(prev));
//                    rebuildPipelineAsync();
//                }else{
//                    btnUndo.setEnabled(false);
//                }
//            }
//        });

//        btnRedo.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (!redoStack.isEmpty()) {
//                    // push current to undo
//                    pushUndoSnapshot();
//                    HashMap<String, FeatureState> next = redoStack.pop();
//                    // replace featureStates with next
//                    featureStates.clear();
//                    featureStates.putAll(next);
//                    rebuildPipelineAsync();
//                }else{
//                    btnRedo.setEnabled(false);
//                }
//            }
//        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupCompareButton() {
        btnFlip.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    // show original
                    imageView.setImageBitmap(baseBitmap);
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    // restore current
                    imageView.setImageBitmap(currentBitmap);
                }
                return true;
            }
        });
    }

    private void initFeatureConfigs() {
//        featureConfigs.put("frame", new FeatureConfig(0f, 10f, 0f, false));
        featureConfigs.put("contrast", new FeatureConfig(0f, 2f, 1f, true));
        featureConfigs.put("saturation", new FeatureConfig(0f, 2f, 1f, true));
        featureConfigs.put("brightness", new FeatureConfig(-100f, 100f, 0f, true));
        featureConfigs.put("warmth", new FeatureConfig(-1f, 1f, 0f, true));
        featureConfigs.put("exposure", new FeatureConfig(-2f, 2f, 0f, true));
        featureConfigs.put("vignette", new FeatureConfig(0f, 1f, 0f, false));
        featureConfigs.put("pixelate", new FeatureConfig(0f, 60f, 0f, false));
        featureConfigs.put("rotate", new FeatureConfig(0f, 360f, 0f, false));
        featureConfigs.put("flip", new FeatureConfig(0f, 1f, 0f, false));
//        featureConfigs.put("roundMask", new FeatureConfig(0f, 1f, 0f, false));
    }

    private void initFeatureStates() {
        for (Map.Entry<String, FeatureConfig> e : featureConfigs.entrySet()) {
            String key =  e.getKey();
            FeatureConfig cfg = e.getValue();

            int seek = paramToSeek(cfg.defaultValue, cfg);
            featureStates.put(key, new FeatureState(seek, cfg.defaultValue, cfg.defaultValue != 0f));
        }
    }

    // ------------------- Feature list (RecyclerView) ---------------------
    private void setupFeatureList() {
        List<FeatureItem> items = new ArrayList<>();
//        items.add(new FeatureItem("Frame", android.R.drawable.ic_menu_gallery, FeatureType.FRAME_SELECTOR));
        items.add(new FeatureItem("contrast", "Contrast", android.R.drawable.ic_menu_manage, FeatureType.ADJUSTABLE));
        items.add(new FeatureItem("saturation", "Saturation", android.R.drawable.ic_menu_gallery, FeatureType.ADJUSTABLE));
        items.add(new FeatureItem("brightness", "Brightness", android.R.drawable.ic_menu_day, FeatureType.ADJUSTABLE));
        items.add(new FeatureItem("exposure", "Exposure", android.R.drawable.ic_menu_camera, FeatureType.ADJUSTABLE));
        items.add(new FeatureItem("warmth", "Warmth", android.R.drawable.ic_menu_compass, FeatureType.ADJUSTABLE));
        items.add(new FeatureItem("vignette", "Vignette", android.R.drawable.ic_menu_crop, FeatureType.ADJUSTABLE));
        items.add(new FeatureItem("pixelate", "Pixelate", android.R.drawable.ic_menu_crop, FeatureType.ADJUSTABLE));
        items.add(new FeatureItem("flip", "Flip", R.drawable.swap_horiz_icon, FeatureType.INSTANT));
        items.add(new FeatureItem("rotate", "Rotate", android.R.drawable.ic_menu_rotate, FeatureType.ADJUSTABLE));
//        items.add(new FeatureItem("Round Mask", android.R.drawable.ic_menu_rotate, FeatureType.INSTANT));

        FeatureAdapter adapter = new FeatureAdapter(items, this::onFeatureClicked);
        rvFeatures.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvFeatures.setAdapter(adapter);
    }

    private void onFeatureClicked(FeatureItem item) {
        if (item.type == FeatureType.ADJUSTABLE) {
//            String key = featureKeyFromDisplay(name);
//            if (key == null) return;
            openControllerForFeature(item);
        }
//        else if (item.type == FeatureType.FRAME_SELECTOR) {
//            String key = featureKeyFromDisplay(name);
//            if (key == null) return;
//            openFrameSelector(key, name);
//        }
        else{
            performInstantOperationByName(item);
        }
    }

    private void performInstantOperationByName(FeatureItem item) {
        // save current state snapshot for undo
        pushUndoSnapshot();

        FeatureState fs;
        switch (item.id) {
            case "flip":
                fs = featureStates.get(item.id);
                fs.value = 1f - fs.value;
                fs.applied = true;
                break;
//            case "Round Mask":
//                fs = featureStates.get("roundmask");
//                fs.value = fs.value == 0f ? 1f : 0f;
//                fs.applied = fs.value != 0f;
//                break;
            default:
                return;
        }

        // apply pipeline after state change
        rebuildPipelineAsync();

        // clear redo
        redoStack.clear();
    }

    // ----------- Frame Controller open/close and actions ----------
    private void openFrameSelector(String key, String name) {
//        selectedFeatureKey = key;
//        btnSave.setVisibility(GONE);
//        btnReset.setVisibility(VISIBLE);
//        controllerTitle.setText(name);
//        rvFeatures.setVisibility(GONE);
//
//        frameControllerContainer.setVisibility(VISIBLE);
//        List<Integer> frameIcons = Arrays.asList(
//            R.drawable.frame_1,
//            R.drawable.frame_2,
//            R.drawable.frame_3,
//            R.drawable.frame_4,
//            R.drawable.frame_5,
//            R.drawable.frame_6,
//            R.drawable.frame_7,
//            R.drawable.frame_8,
//            R.drawable.frame_9,
//            R.drawable.frame_10
//        );
//
//        FrameAdapter adapter = new FrameAdapter(frameIcons, this);
//        rvFrames.bringToFront();
//        rvFrames.invalidate();
//        rvFrames.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
//        rvFrames.setAdapter(adapter);
    }

//    @Override
//    public void onFrameClicked(int index) {
//        Log.d(TAG, "onFrameClicked: "+ index);
//        if (selectedFeatureKey == null) return;
//
//        FeatureConfig cfg = featureConfigs.get(selectedFeatureKey);
//        if (cfg == null) return;
//
//        FeatureState fs = featureStates.get(selectedFeatureKey);
//        if (fs == null) {
//            fs = new FeatureState(index, seekToParam(index, cfg), false);
//            featureStates.put(selectedFeatureKey, fs);
//        }else{
//            rebuildPipelineAsyncWithTemporary(selectedFeatureKey, index); // 24/11/25-11:35 PM
//        }
//
//        // debounce quick updates
//        long now = SystemClock.uptimeMillis();
//        if (now - lastRebuildMs < REBUILD_DEBOUNCE_MS) {
//            // stile update lastRebuildMs so we get spaced updates
//            lastRebuildMs = now;
//        }
//
//        rebuildPipelineAsyncWithTemporary(selectedFeatureKey, index);
//
//    }

    // ------------- Controller open/close and actions --------------
    private void openControllerForFeature(FeatureItem feature) {
        selectedFeature = feature;
        btnSave.setVisibility(GONE);
        btnReset.setVisibility(VISIBLE);
        controllerTitle.setText(feature.name);
        rvFeatures.setVisibility(GONE);
        controllerContainer.setVisibility(VISIBLE);

        // configure seek bar
        FeatureConfig cfg = featureConfigs.get(selectedFeature.id);
        if (cfg == null) return;
        seekBar.setMax(SEEKBAR_MAX);

        FeatureState fs = featureStates.get(selectedFeature.id);
        if (fs == null) {
            fs = new FeatureState(paramToSeek(cfg.defaultValue, cfg), cfg.defaultValue, false);
            featureStates.put(selectedFeature.id, fs);
        }

        // set seek to stored value for the feature
        int storedSeek = fs.seekValue;
        seekBar.setProgress(storedSeek);

        // apply preview with stored value (do not commit)
        // we'll apply live when user changes seekbar; but show current pipeline with this stored value as preview
        rebuildPipelineAsyncWithTemporary(selectedFeature.id, storedSeek);
    }

    private void closeControllerAndResultUI() {
        selectedFeature = null;
        controllerContainer.setVisibility(GONE);
//        frameControllerContainer.setVisibility(GONE);
        rvFeatures.setVisibility(VISIBLE);
        btnSave.setVisibility(VISIBLE);
        btnReset.setVisibility(GONE);
    }

    private void setupControllerActions() {
        // Seekbar change -> update featureStates live and preview

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                if (!fromUser) return;
                if (selectedFeature == null) return;

                FeatureConfig cfg = featureConfigs.get(selectedFeature.id);
                if (cfg == null) return;

                // store progress temporarily to featureStates (but not mark applied untile Apply)
                FeatureState fs = featureStates.get(selectedFeature.id);
                if (fs == null) {
                    fs = new FeatureState(progress, seekToParam(progress, cfg), false);
                    featureStates.put(selectedFeature.id, fs);
                }
//                else{
//                    fs.seekValue = progress;
//                    fs.value = seekToParam(progress, cfg);
//                    rebuildPipelineAsyncWithTemporary(selectedFeature.id, progress); // 24/11/25-11:35 PM
//                }

                // debounce quick updates
                long now = SystemClock.uptimeMillis();
                if (now - lastRebuildMs < REBUILD_DEBOUNCE_MS) {
                    // stile update lastRebuildMs so we get spaced updates
                    lastRebuildMs = now;
                }

                rebuildPipelineAsyncWithTemporary(selectedFeature.id, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedFeature == null) return;
                FeatureConfig cfg = featureConfigs.get(selectedFeature.id);
                if (cfg == null) return;
                int defSeek = paramToSeek(cfg.defaultValue, cfg);
                seekBar.setProgress(defSeek);
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // discard temporary changes: restore UI from Last committed state
                closeControllerAndResultUI();

                rebuildPipelineAsync(); // 24/11/25 - 11:37
                controllerTitle.setText("Editor");
                btnSave.setVisibility(VISIBLE);
                btnReset.setVisibility(GONE);
            }
        });

        btnApply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedFeature == null) return;

                // push snapshot for undo
                pushUndoSnapshot();

                FeatureConfig cfg = featureConfigs.get(selectedFeature.id);

                int progress = seekBar.getProgress();

                // commit the feature state (mark applied true)
                FeatureState fs = featureStates.get(selectedFeature.id);

                if (fs != null) {
                    fs.seekValue = progress; // 24/11/25 - 11:36
                    fs.value = seekToParam(progress, cfg); // 24/11/25 - 11:36
                    fs.applied = true;
                }


                // hide controller and rebuild pipeline with committed values
                closeControllerAndResultUI();

                // clear redo stack (new branch)
                redoStack.clear();
            }
        });
    }

    // ---------- Undo / Redo (snapshot copy of featureStates) ----------
    private void pushUndoSnapshot() {
        HashMap<String, FeatureState> snapshot = deepCopyFeatureStates(featureStates);
        undoStack.push(snapshot);
        // cap undo history
        if (undoStack.size() > 30) {
            undoStack.remove(0);
        }
    }

    private void pushRedoSnapshot() {
        HashMap<String, FeatureState> snapshot = deepCopyFeatureStates(featureStates);
        redoStack.push(snapshot);
        // cap redo history
        if (redoStack.size() > 30) {
            redoStack.remove(0);
        }
    }

    private HashMap<String, FeatureState> deepCopyFeatureStates(HashMap<String, FeatureState> src) {
        HashMap<String, FeatureState> m = new HashMap<>();
        for (Map.Entry<String, FeatureState> e : src.entrySet()) {
            m.put(e.getKey(), new FeatureState(e.getValue()));
        }
        return m;
    }


    // ---------- Pipeline rebuild helpers ------------
    private void rebuildPipelineAsync() {
        rebuildPipelineAsync(null, -1);
    }

    // If temporaryFeatureKey != null and tempSeek >= 0, treat that feature as using tempSeek for preview (not committed)
    private void rebuildPipelineAsync(@Nullable String temporaryFeatureKey, int tempSeek) {
        // background thread
        new Thread(() -> {
            try {
                Bitmap tmp = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);

                // apply in fixed order
                // 1. contrast
                ContrastOperation co = new ContrastOperation();
                tmp = co.apply(tmp, getEffectiveParam("contrast", temporaryFeatureKey, tempSeek));

                // 2. saturation
                SaturationOperation so = new SaturationOperation();
                tmp = so.apply(tmp, getEffectiveParam("saturation", temporaryFeatureKey, tempSeek));

                // 3. brightness
                BrightnessOperation bo = new BrightnessOperation();
                tmp = bo.apply(tmp, getEffectiveParam("brightness", temporaryFeatureKey, tempSeek));

                // 4. exposure
                ExposureOperation eo = new ExposureOperation();
                tmp = eo.apply(tmp, getEffectiveParam("exposure", temporaryFeatureKey, tempSeek));

                // 5. warmth
                WarmthOperation wo = new WarmthOperation();
                tmp = wo.apply(tmp, getEffectiveParam("warmth", temporaryFeatureKey, tempSeek));

                // 6. vignette
                VignetteOperation vo = new VignetteOperation();
                tmp = vo.apply(tmp, getEffectiveParam("vignette", temporaryFeatureKey, tempSeek));

                // 7. pixelate
                PixelateOperation po = new PixelateOperation();
                tmp = po.apply(tmp, getEffectiveParam("pixelate", temporaryFeatureKey, tempSeek));

                // 8. rotate
                RotationOperation ro = new RotationOperation();
                tmp = ro.apply(tmp, getEffectiveParam("rotate", temporaryFeatureKey, tempSeek));

                // 9. flip horizontal
                FlipOperation fo = new FlipOperation();
                tmp = fo.apply(tmp, getEffectiveParam("flip", temporaryFeatureKey, tempSeek));

                // 10. flip horizontal
//                FrameOperation fro = new FrameOperation();
//                tmp = fro.apply(tmp, tempSeek, frameBitmaps);


                final Bitmap finalBmp = tmp;
                runOnUiThread(() -> {
                    if (currentBitmap != null && currentBitmap != baseBitmap && !currentBitmap.isRecycled()) {
                        currentBitmap.recycle();
                    }
                    currentBitmap = finalBmp;
                    imageView.setImageBitmap(currentBitmap);
                });

            } catch (Throwable t) {
                Log.e(TAG, "rebuildPipelineAsync: ", t);
            }
        }).start();
    }

    // Helper: rebuild with temporary when controller opened/seek changed
    private void rebuildPipelineAsyncWithTemporary(String temporaryFeatureKey, int tempSeek) {
        // throttle to avoid too many background tasks
        long now = SystemClock.uptimeMillis();
        if (now -lastRebuildMs < REBUILD_DEBOUNCE_MS) {
            lastRebuildMs = now;

            // schedule slight delay to still update
            new Thread(() -> {
                SystemClock.sleep(REBUILD_DEBOUNCE_MS);

                rebuildPipelineAsync(temporaryFeatureKey, tempSeek);
            }).start();
        }else{
            lastRebuildMs = now;
            rebuildPipelineAsync(temporaryFeatureKey, tempSeek);
        }
    }

    // Get effective param value for feature: check temporary override if editing
    private float getEffectiveParam(String key, @Nullable String temporaryFeatureKey, int tempSeek) {
        FeatureConfig cfg = featureConfigs.get(key);
        FeatureState fs = featureStates.get(key);
        float param = cfg != null ? cfg.defaultValue : 0f;
        if (fs != null && fs.applied) {
            param = fs.value;
        }

        if (temporaryFeatureKey != null && temporaryFeatureKey.equals(key) && tempSeek >= 0) {
            param = seekToParam(tempSeek, cfg);
        }
        return param;
    }

    // --------- Parameter mapping helpers (seek <-> param) -----------
    private float seekToParam(int seek, FeatureConfig cfg) {
        if (cfg == null) return 0f;
        float t = seek / (float) SEEKBAR_MAX;
        return cfg.min + t * (cfg.max - cfg.min);
    }
    private int paramToSeek(float param, FeatureConfig cfg) {
        if (cfg == null) return 0;
        float t = (param - cfg.min) / (cfg.max - cfg.min);
        return Math.round(t * SEEKBAR_MAX);
    }


}