package com.limelight.preferences;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.limelight.R;
import com.limelight.binding.input.virtual_controller.WebGamepadLayoutLoader;
import com.limelight.utils.FileUriUtils;
import com.limelight.utils.UiHelper;

import java.util.ArrayList;
import java.util.List;

public class VirtualGamepadLayoutListActivity extends AppCompatActivity {
    private static final int READ_REQUEST_GAMEPAD_LAYOUT_CODE = 2001;

    private final List<WebGamepadLayoutLoader.ImportedLayout> layouts = new ArrayList<>();
    private LayoutAdapter adapter;
    private RecyclerView recyclerView;
    private View emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_virtual_gamepad_layout_list);

        recyclerView = findViewById(R.id.gamepadLayoutRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LayoutAdapter();
        recyclerView.setAdapter(adapter);

        emptyState = findViewById(R.id.emptyState);

        FloatingActionButton fab = findViewById(R.id.addGamepadLayoutFab);
        fab.setOnClickListener(v -> openImportPicker());

        reloadLayouts();
        UiHelper.notifyNewRootView(this);
    }

    private void openImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, READ_REQUEST_GAMEPAD_LAYOUT_CODE);
    }

    private void reloadLayouts() {
        layouts.clear();
        layouts.addAll(WebGamepadLayoutLoader.listImportedLayouts(this));
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean empty = layouts.isEmpty();
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != READ_REQUEST_GAMEPAD_LAYOUT_CODE ||
                resultCode != Activity.RESULT_OK ||
                data == null ||
                data.getData() == null) {
            return;
        }

        try {
            Uri uri = data.getData();
            String json = FileUriUtils.openUriForRead(this, uri);
            if (TextUtils.isEmpty(json)) {
                Toast.makeText(this, getString(R.string.pref_empty_file), Toast.LENGTH_SHORT).show();
                return;
            }

            WebGamepadLayoutLoader.saveImportedLayout(this, json, getDisplayName(uri));
            Toast.makeText(this, getString(R.string.pref_import_success), Toast.LENGTH_SHORT).show();
            reloadLayouts();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.pref_error_occurred) + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private class LayoutAdapter extends RecyclerView.Adapter<LayoutAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_virtual_gamepad_layout, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WebGamepadLayoutLoader.ImportedLayout layout = layouts.get(position);
            holder.nameText.setText(layout.name);
            holder.statusText.setText(layout.active
                    ? getString(R.string.virtual_gamepad_layout_active)
                    : getString(R.string.virtual_gamepad_layout_inactive));
            holder.activateButton.setEnabled(!layout.active);
            holder.activateButton.setAlpha(layout.active ? 0.35f : 1.0f);

            holder.activateButton.setOnClickListener(v -> {
                WebGamepadLayoutLoader.setActiveLayout(VirtualGamepadLayoutListActivity.this, layout.id);
                Toast.makeText(VirtualGamepadLayoutListActivity.this,
                        getString(R.string.virtual_gamepad_layout_activated, layout.name),
                        Toast.LENGTH_SHORT).show();
                reloadLayouts();
            });

            holder.deleteButton.setOnClickListener(v ->
                    new AlertDialog.Builder(VirtualGamepadLayoutListActivity.this)
                            .setTitle(R.string.virtual_gamepad_layout_delete)
                            .setMessage(getString(R.string.virtual_gamepad_layout_delete_confirm, layout.name))
                            .setPositiveButton(R.string.virtual_gamepad_layout_delete, (dialog, which) -> {
                                if (WebGamepadLayoutLoader.deleteImportedLayout(VirtualGamepadLayoutListActivity.this, layout.id)) {
                                    Toast.makeText(VirtualGamepadLayoutListActivity.this,
                                            getString(R.string.virtual_gamepad_layout_deleted, layout.name),
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(VirtualGamepadLayoutListActivity.this,
                                            getString(R.string.pref_error_occurred),
                                            Toast.LENGTH_SHORT).show();
                                }
                                reloadLayouts();
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show());
        }

        @Override
        public int getItemCount() {
            return layouts.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText;
            TextView statusText;
            ImageButton activateButton;
            ImageButton deleteButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.gamepadLayoutName);
                statusText = itemView.findViewById(R.id.gamepadLayoutStatus);
                activateButton = itemView.findViewById(R.id.activateGamepadLayout);
                deleteButton = itemView.findViewById(R.id.deleteGamepadLayout);
            }
        }
    }
}
