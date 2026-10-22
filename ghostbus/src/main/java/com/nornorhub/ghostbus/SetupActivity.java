package com.nornorhub.ghostbus;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;

/** Asks for the NJ Transit developer login, then downloads every stop, route and trip. */
public class SetupActivity extends Activity {

    static final String EXTRA_REDOWNLOAD = "redownload";

    private EditText username;
    private EditText password;
    private Button signIn;
    private ProgressBar progress;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        signIn = findViewById(R.id.sign_in);
        progress = findViewById(R.id.progress);
        status = findViewById(R.id.status);

        Prefs prefs = new Prefs(this);
        username.setText(prefs.username());
        password.setText(prefs.password());
        signIn.setOnClickListener(v -> start());
        if (getIntent().getBooleanExtra(EXTRA_REDOWNLOAD, false) && prefs.hasLogin()) {
            start();
        }
    }

    private void start() {
        String user = username.getText().toString().trim();
        String pass = password.getText().toString();
        if (user.isEmpty() || pass.isEmpty()) {
            showStatus("Enter your NJ Transit developer username and password.", true);
            return;
        }
        Prefs prefs = new Prefs(this);
        prefs.setLogin(user, pass);
        setBusy(true);
        showStatus("Signing in to NJ Transit…", false);

        boolean redownload = getIntent().getBooleanExtra(EXTRA_REDOWNLOAD, false);
        Ui.IO.execute(() -> {
            try {
                NjtApi api = new NjtApi(prefs);
                api.authenticate();
                TransitDb db = TransitDb.get(this);
                if (redownload || !db.hasStops()) {
                    progress("Downloading every NJ Transit bus stop and route… (this can take a minute)");
                    File zip = new File(getCacheDir(), "gtfs.zip");
                    try {
                        api.downloadGtfs(zip);
                        GtfsImporter.importZip(zip, db, this::progress);
                    } finally {
                        zip.delete();
                    }
                    prefs.setGtfsImportedAt(System.currentTimeMillis());
                }
                runOnUiThread(() -> {
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showStatus(e.getMessage() == null ? e.toString() : e.getMessage(), true);
                });
            }
        });
    }

    private void progress(String message) {
        runOnUiThread(() -> showStatus(message, false));
    }

    private void setBusy(boolean busy) {
        signIn.setEnabled(!busy);
        username.setEnabled(!busy);
        password.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void showStatus(String message, boolean error) {
        status.setText(message);
        status.setTextColor(error ? 0xFFC62828 : Ui.MUTED);
    }
}
