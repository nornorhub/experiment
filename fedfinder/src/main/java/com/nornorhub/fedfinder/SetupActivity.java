package com.nornorhub.fedfinder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.nornorhub.fedfinder.core.SearchQuery;

/** Asks for the free USAJOBS API key and the email it was issued to, then checks they work. */
public class SetupActivity extends Activity {

    private EditText email;
    private EditText apiKey;
    private Button save;
    private ProgressBar progress;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        email = findViewById(R.id.email);
        apiKey = findViewById(R.id.api_key);
        save = findViewById(R.id.save);
        progress = findViewById(R.id.progress);
        status = findViewById(R.id.status);

        Prefs prefs = new Prefs(this);
        email.setText(prefs.email());
        apiKey.setText(prefs.apiKey());
        save.setOnClickListener(v -> check());
    }

    private void check() {
        String mail = email.getText().toString().trim();
        String key = apiKey.getText().toString().trim();
        if (mail.isEmpty() || key.isEmpty()) {
            showStatus("Enter the email you registered with and your API key.", true);
            return;
        }
        setBusy(true);
        showStatus("Checking your key with USAJOBS…", false);
        Ui.IO.execute(() -> {
            try {
                SearchQuery test = new SearchQuery();
                test.resultsPerPage = 1;
                new UsaJobsApi(mail, key).search(test);
                new Prefs(this).setKey(mail, key);
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

    private void setBusy(boolean busy) {
        save.setEnabled(!busy);
        email.setEnabled(!busy);
        apiKey.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void showStatus(String message, boolean error) {
        status.setText(message);
        status.setTextColor(error ? 0xFFC62828 : Ui.MUTED);
    }
}
