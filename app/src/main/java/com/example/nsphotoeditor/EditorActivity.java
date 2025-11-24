package com.example.nsphotoeditor;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;

public class EditorActivity extends AppCompatActivity {

    private ImageView imageView;
    ImageButton btnFlip, btnUndo, btnRedo, btnSave, btnBack, btnReset, btnCancel, btnApply;
    private RecyclerView rvFeatures;
    private View controllerContainer;
    private TextView controllerTitle;
    private SeekBar seekBar;
    private Bitmap baseBitmap, currentBitmap;

    private final int SEEKBAR_MAX = 200;
    private final HashMap<String, FeatureState> featureStates = new HashMap<>();
    private final HashMap<String, FeatureConfig> featureConfigs = new HashMap<>();
    private final Stack<HashMap<String, FeatureState>> undoStack = new Stack<>();
    private final Stack<HashMap<String, FeatureState>> redoStack = new Stack<>();

    private String selectedFeatureKey = null;
    private long lastRebuildMs = 0;
    private static final long REBUILD_DEBOUNCE_MS = 60;

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
        btnUndo = findViewById(R.id.undo_btn);
        btnRedo = findViewById(R.id.redo_btn);
        btnSave = findViewById(R.id.save_btn);
        btnFlip = findViewById(R.id.flip_preview_btn);

        btnReset = findViewById(R.id.btn_reset);
        btnCancel = findViewById(R.id.btn_cancel);
        btnApply = findViewById(R.id.btn_apply);

        rvFeatures = findViewById(R.id.rv_features);
        controllerContainer = findViewById(R.id.controller_container);
        controllerTitle = findViewById(R.id.controller_title);

        seekBar = findViewById(R.id.seekbar);

        initFeatureConfigs();
        initFeatureStates();

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
            e.printStackTrace();
            finish();
            return;
        }

        currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);

        imageView.setImageBitmap(currentBitmap);

        setupFeatureList();
        setupTopButtons();
        setupCompareButton();
        setupControllerActions();
    }

    private void setupTopButtons() {
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(EditorActivity.this, MainActivity.class));
                finish();
            }
        });

        btnUndo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!undoStack.isEmpty()) {
                    // push current to redo
                    pushRedoSnapshot();
                    HashMap<String, FeatureState> prev = undoStack.pop();
                    // replace featureStates with prev
                    featureStates.clear();

                    featureStates.putAll(deepCopyFeatureStates(prev));
                    rebuildPipelineAsync();
                }else{
                    btnUndo.setEnabled(false);
                }
            }
        });

        btnRedo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!redoStack.isEmpty()) {
                    // push current to undo
                    pushUndoSnapshot();
                    HashMap<String, FeatureState> next = redoStack.pop();
                    // replace featureStates with next
                    featureStates.clear();
                    featureStates.putAll(next);
                    rebuildPipelineAsync();
                }else{
                    btnRedo.setEnabled(false);
                }
            }
        });
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
        featureConfigs.put("contrast", new FeatureConfig(0f, 2f, 1f, true));
        featureConfigs.put("saturation", new FeatureConfig(0f, 2f, 1f, true));
        featureConfigs.put("brightness", new FeatureConfig(-100f, 100f, 0f, true));
        featureConfigs.put("warmth", new FeatureConfig(-1f, 1f, 0f, true));
        featureConfigs.put("exposure", new FeatureConfig(-2f, 2f, 0f, true));
        featureConfigs.put("vignette", new FeatureConfig(0f, 1f, 0f, false));
        featureConfigs.put("pixelate", new FeatureConfig(0f, 60f, 0f, false));
        featureConfigs.put("rotate", new FeatureConfig(0f, 360f, 0f, false));
        featureConfigs.put("flipH", new FeatureConfig(0f, 1f, 0f, false));
        featureConfigs.put("flipV", new FeatureConfig(0f, 1f, 0f, false));
        featureConfigs.put("roundMask", new FeatureConfig(0f, 1f, 0f, false));
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
        items.add(new FeatureItem("Contrast", android.R.drawable.ic_menu_manage, FeatureType.ADJUSTABLE));
        items.add(new FeatureItem("Saturation", android.R.drawable.ic_menu_gallery, FeatureType.ADJUSTABLE));
        items.add(new FeatureItem("Brightness", android.R.drawable.ic_menu_day, FeatureType.ADJUSTABLE));
        items.add(new FeatureItem("Exposure", android.R.drawable.ic_menu_camera, FeatureType.ADJUSTABLE));
        items.add(new FeatureItem("Warmth", android.R.drawable.ic_menu_compass, FeatureType.ADJUSTABLE));
        items.add(new FeatureItem("Vignette", android.R.drawable.ic_menu_crop, FeatureType.ADJUSTABLE));
        items.add(new FeatureItem("Pixelate", android.R.drawable.ic_menu_crop, FeatureType.ADJUSTABLE));
        items.add(new FeatureItem("Flip H", R.drawable.swap_horiz_icon, FeatureType.INSTANT));
        items.add(new FeatureItem("Flip V", R.drawable.swap_vert_icon, FeatureType.INSTANT));
        items.add(new FeatureItem("Rotate", android.R.drawable.ic_menu_rotate, FeatureType.ADJUSTABLE));
        items.add(new FeatureItem("Round Mask", android.R.drawable.ic_menu_rotate, FeatureType.INSTANT));

        FeatureAdapter adapter = new FeatureAdapter(items, this::onFeatureClicked);
        rvFeatures.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvFeatures.setAdapter(adapter);
    }

    private void onFeatureClicked(FeatureItem item) {
        final String name = item.name;
        if (item.type == FeatureType.ADJUSTABLE) {
            String key = featureKeyFromDisplay(name);
            if (key == null) return;
            openControllerForFeature(key, name);
        }else{
            performInstantOperationByName(name);
        }
    }

    private String featureKeyFromDisplay(String display) {
        switch (display) {
            case "Contrast": return "contrast";
            case "Saturation": return "saturation";
            case "Brightness": return "brightness";
            case "Exposure": return "exposure";
            case "Warmth": return "warmth";
            case "Vignette": return "vignette";
            case "Pixelate": return "pixelate";
            case "Rotate": return "rotate";
            default: return null;
        }
    }

    private void performInstantOperationByName(String name) {
        // save current state snapshot for undo
        pushUndoSnapshot();

        FeatureState fs;
        switch (name) {
            case "Flip H":
                fs = featureStates.get("flipH");
                fs.value = 1f;
                fs.applied = true;
                break;
            case "Flip V":
                fs = featureStates.get("flipV");
                fs.value = 0f;
                fs.applied = true;
                break;
//            case "Rotate 180":
//                fs = featureStates.get("rotate");
//                float newDeg180 = (fs.value + 180f) % 360f;
//                fs.value = 1f - newDeg180;
//                fs.applied = fs.value != 0f;
//                break;
//            case "Rotate 270":
//                fs = featureStates.get("rotate");
//                float newDeg270 = (fs.value + 270f) % 360f;
//                fs.value = 1f - newDeg270;
//                fs.applied = fs.value != 0f;
//                break;
            case "Round Mask":
                fs = featureStates.get("roundmask");
                fs.value = fs.value == 0f ? 1f : 0f;
                fs.applied = fs.value != 0f;
                break;
            default:
                return;
        }

        // apply pipeline after state change
        rebuildPipelineAsync();

        // clear redo
        redoStack.clear();
    }


    // ------------- Controller open/close and actions --------------
    private void openControllerForFeature(String key, String displayName) {
        selectedFeatureKey = key;

        controllerTitle.setText(displayName);
        rvFeatures.setVisibility(GONE);
        controllerContainer.setVisibility(VISIBLE);

        // configure seek bar
        FeatureConfig cfg = featureConfigs.get(key);
        if (cfg == null) return;
        seekBar.setMax(SEEKBAR_MAX);

        FeatureState fs = featureStates.get(key);
        if (fs == null) {
            fs = new FeatureState(paramToSeek(cfg.defaultValue, cfg), cfg.defaultValue, false);
            featureStates.put(key, fs);
        }

        // set seek to stored value for the feature
        int storedSeek = fs.seekValue;
        seekBar.setProgress(storedSeek);

        // apply preview with stored value (do not commit)
        // we'll apply live when user changes seekbar; but show current pipeline with this stored value as preview
        rebuildPipelineAsyncWithTemporary(key, storedSeek);
    }

    private void closeControllerAndResultUI() {
        selectedFeatureKey = null;
        controllerContainer.setVisibility(GONE);
        rvFeatures.setVisibility(VISIBLE);
    }

    private void setupControllerActions() {
        // Seekbar change -> update featureStates live and preview

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                if (!fromUser) return;
                if (selectedFeatureKey == null) return;

                FeatureConfig cfg = featureConfigs.get(selectedFeatureKey);
                if (cfg == null) return;

                // store progress temporarily to featureStates (but not mark applied untile Apply)
                FeatureState fs = featureStates.get(selectedFeatureKey);
                if (fs == null) {
                    fs = new FeatureState(progress, seekToParam(progress, cfg), false);
                    featureStates.put(selectedFeatureKey, fs);
                }else{
//                    fs.seekValue = progress;
//                    fs.value = seekToParam(progress, cfg);
                    rebuildPipelineAsyncWithTemporary(selectedFeatureKey, progress); // 24/11/25-11:35 PM
                }

                // debounce quick updates
                long now = SystemClock.uptimeMillis();
                if (now - lastRebuildMs < REBUILD_DEBOUNCE_MS) {
                    // stile update lastRebuildMs so we get spaced updates
                    lastRebuildMs = now;
                }

                rebuildPipelineAsyncWithTemporary(selectedFeatureKey, progress);
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
                if (selectedFeatureKey == null) return;
                FeatureConfig cfg = featureConfigs.get(selectedFeatureKey);
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
            }
        });

        btnApply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedFeatureKey == null) return;

                // push snapshot for undo
                pushUndoSnapshot();

                FeatureConfig cfg = featureConfigs.get(selectedFeatureKey);
                int progress = seekBar.getProgress();

                // commit the feature state (mark applied true)
                FeatureState fs = featureStates.get(selectedFeatureKey);
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

                // 8. flip horizontal
                FlipOperation fo = new FlipOperation();
                tmp = fo.apply(tmp, getEffectiveParam("flipH", temporaryFeatureKey, tempSeek));

                // 9. flip vertical
                tmp = fo.apply(tmp, getEffectiveParam("flipV", temporaryFeatureKey, tempSeek));


                final Bitmap finalBmp = tmp;
                runOnUiThread(() -> {
                    if (currentBitmap != null && currentBitmap != baseBitmap && !currentBitmap.isRecycled()) {
                        currentBitmap.recycle();
                    }
                    currentBitmap = finalBmp;
                    imageView.setImageBitmap(currentBitmap);
                });

            } catch (Throwable t) {
                t.printStackTrace();
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