package com.intel.realsense.camera;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Lists the files of an app folder; tap returns the path, long-press deletes. */
public class FileBrowserActivity extends AppCompatActivity {
    static final String EXTRA_FOLDER = "folder";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_FILE_PATH = "filePath";

    private File mFolder;
    private ListView mListView;
    private View mEmptyView;

    static Intent newIntent(Context context, File folder, @StringRes int title) {
        return new Intent(context, FileBrowserActivity.class)
                .putExtra(EXTRA_FOLDER, folder.getAbsolutePath())
                .putExtra(EXTRA_TITLE, title);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        Ui.edgeToEdgeWithAppBar(this, findViewById(R.id.root));
        mFolder = new File(getIntent().getStringExtra(EXTRA_FOLDER));
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(getIntent().getIntExtra(EXTRA_TITLE, R.string.select_file));
        Ui.setupToolbar(this, toolbar);
        mListView = findViewById(R.id.list_view);
        mEmptyView = findViewById(R.id.empty_view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        File[] listed = mFolder.listFiles();
        if (listed == null) {
            Toast.makeText(this, R.string.file_read_error, Toast.LENGTH_LONG).show();
            listed = new File[0];
        }
        final List<File> files = new ArrayList<>(Arrays.asList(listed));
        Collections.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        mEmptyView.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
        mListView.setVisibility(files.isEmpty() ? View.GONE : View.VISIBLE);

        mListView.setAdapter(new ArrayAdapter<File>(this, R.layout.item_two_line, files) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = convertView != null ? convertView : LayoutInflater.from(getContext()).inflate(R.layout.item_two_line, parent, false);
                File file = files.get(position);
                ((TextView) view.findViewById(R.id.item_title)).setText(file.getName());
                ((TextView) view.findViewById(R.id.item_subtitle)).setText(FileUtilities.formatSize(file.length()) + "  •  " +
                        DateFormat.getMediumDateFormat(getContext()).format(file.lastModified()) + " " +
                        DateFormat.getTimeFormat(getContext()).format(file.lastModified()));
                return view;
            }
        });
        mListView.setOnItemClickListener((parent, view, position, id) -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_FILE_PATH, files.get(position).getAbsolutePath()));
            finish();
        });
        mListView.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmDelete(files.get(position));
            return true;
        });
    }

    private void confirmDelete(File file) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.delete_file_title, file.getName()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    if (file.delete())
                        Toast.makeText(this, getString(R.string.file_deleted, file.getName()), Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .show();
    }
}
