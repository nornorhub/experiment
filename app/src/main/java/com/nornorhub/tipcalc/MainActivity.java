package com.nornorhub.tipcalc;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.NumberFormat;

public class MainActivity extends Activity {

    private EditText bill;
    private SeekBar tipSeek;
    private SeekBar peopleSeek;
    private TextView tipLabel;
    private TextView peopleLabel;
    private TextView tipAmount;
    private TextView totalAmount;
    private TextView perPerson;

    private final NumberFormat currency = NumberFormat.getCurrencyInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bill = findViewById(R.id.bill);
        tipSeek = findViewById(R.id.tip_seek);
        peopleSeek = findViewById(R.id.people_seek);
        tipLabel = findViewById(R.id.tip_label);
        peopleLabel = findViewById(R.id.people_label);
        tipAmount = findViewById(R.id.tip_amount);
        totalAmount = findViewById(R.id.total_amount);
        perPerson = findViewById(R.id.per_person);

        bill.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { update(); }
        });

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { update(); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        tipSeek.setOnSeekBarChangeListener(listener);
        peopleSeek.setOnSeekBarChangeListener(listener);

        update();
    }

    private void update() {
        double amount;
        try {
            amount = Double.parseDouble(bill.getText().toString());
        } catch (NumberFormatException e) {
            amount = 0;
        }
        int tipPercent = tipSeek.getProgress();
        int people = peopleSeek.getProgress() + 1;

        double tip = amount * tipPercent / 100.0;
        double total = amount + tip;

        tipLabel.setText(getString(R.string.tip_label, tipPercent));
        peopleLabel.setText(getString(R.string.people_label, people));
        tipAmount.setText(getString(R.string.tip_amount, currency.format(tip)));
        totalAmount.setText(getString(R.string.total_amount, currency.format(total)));
        perPerson.setText(getString(R.string.per_person, currency.format(total / people)));
    }
}
