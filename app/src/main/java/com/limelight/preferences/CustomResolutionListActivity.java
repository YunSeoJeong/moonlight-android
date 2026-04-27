package com.limelight.preferences;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.limelight.R;
import com.limelight.utils.UiHelper;

import java.util.List;

public class CustomResolutionListActivity extends AppCompatActivity {

    private ResolutionAdapter adapter;
    private List<String> resolutionList;
    private View emptyState;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_resolution_list);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        resolutionList = PreferenceConfiguration.getCustomResolutionList(prefs);

        recyclerView = findViewById(R.id.resolutionRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ResolutionAdapter();
        recyclerView.setAdapter(adapter);

        emptyState = findViewById(R.id.emptyState);

        FloatingActionButton fab = findViewById(R.id.addResolutionFab);
        fab.setOnClickListener(v -> showResolutionDialog(null, -1));

        updateEmptyState();
        UiHelper.notifyNewRootView(this);
    }

    private void updateEmptyState() {
        if (resolutionList.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }
    }

    private void showResolutionDialog(String currentValue, int editPosition) {
        EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(11)});
        editText.setHint(getString(R.string.hint_resolution_input));
        if (currentValue != null) {
            editText.setText(currentValue);
            editText.setSelection(currentValue.length());
        }

        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        editText.setPadding(padding, padding, padding, padding);

        String title = editPosition >= 0
                ? getString(R.string.edit_custom_resolution)
                : getString(R.string.add_custom_resolution);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(editText)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String value = editText.getText().toString().trim();
                    if (TextUtils.isEmpty(value)) {
                        Toast.makeText(this, getString(R.string.pref_enter_value_0_9999), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String[] parts = value.split("x");
                    if (parts.length != 2) {
                        Toast.makeText(this, getString(R.string.pref_error_occurred), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        int w = Integer.parseInt(parts[0]);
                        int h = Integer.parseInt(parts[1]);
                        if (w <= 0 || h <= 0) {
                            Toast.makeText(this, getString(R.string.pref_error_occurred), Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, getString(R.string.pref_error_occurred), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (editPosition >= 0) {
                        resolutionList.set(editPosition, value);
                    } else {
                        resolutionList.add(value);
                    }
                    saveList();
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void saveList() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        PreferenceConfiguration.saveCustomResolutionList(prefs, resolutionList);
    }

    private class ResolutionAdapter extends RecyclerView.Adapter<ResolutionAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_custom_resolution, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String resolution = resolutionList.get(position);
            holder.resolutionText.setText(resolution);

            holder.editButton.setOnClickListener(v ->
                    showResolutionDialog(resolution, holder.getAdapterPosition()));

            holder.deleteButton.setOnClickListener(v ->
                    new AlertDialog.Builder(CustomResolutionListActivity.this)
                            .setTitle(R.string.delete_custom_resolution)
                            .setMessage(R.string.confirm_delete_resolution)
                            .setPositiveButton(R.string.delete_custom_resolution, (dialog, which) -> {
                                resolutionList.remove(holder.getAdapterPosition());
                                saveList();
                                notifyDataSetChanged();
                                updateEmptyState();
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show());
        }

        @Override
        public int getItemCount() {
            return resolutionList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView resolutionText;
            ImageButton editButton;
            ImageButton deleteButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                resolutionText = itemView.findViewById(R.id.resolutionText);
                editButton = itemView.findViewById(R.id.editResolution);
                deleteButton = itemView.findViewById(R.id.deleteResolution);
            }
        }
    }
}
