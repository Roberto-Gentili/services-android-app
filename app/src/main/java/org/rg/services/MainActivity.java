package org.rg.services;

import android.os.Bundle;
import android.os.StrictMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.rg.services.ui.main.MainFragment;
import org.rg.services.ui.main.SettingsFragment;
import org.rg.util.LoggerChain;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity {
    private LocalDateTime lastUpdateTime;
    private DateTimeFormatter dateFormatter;

    public MainActivity() {
        int SDK_INT = android.os.Build.VERSION.SDK_INT;
        if (SDK_INT > 8)  {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        dateFormatter = DateTimeFormatter.ofPattern("HH:mm:ss - dd/MM/yyyy");
        //RestTemplateSupplier.getSharedInstance().enableRequestLogger();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Consumer<String> logger = message -> {
            if (!message.toLowerCase().contains("invalid symbol")) {
                runOnUiThread(() -> {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            }
        };
        LoggerChain.getInstance().appendExceptionLogger(logger);
        LoggerChain.getInstance().appendInfoLogger(logger);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            goToMainView();
        }
    }

    protected MainFragment getMainFragment(){
        return (MainFragment)getSupportFragmentManager().findFragmentById(R.id.container);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settingsMenuItem:
                goToSettingsView();
                break;
            case R.id.cryptoInfoMenuItem:
                goToMainView();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void goToSettingsView() {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.container, SettingsFragment.getInstance())
            .commit();
        setTitle(getResources().getString(R.string.settingsLabelText));
    }

    public void goToMainView() {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.container, MainFragment.getInstance())
            .commitNow();
        setTitle(getResources().getString(R.string.cryptoInfoLabelText));
    }

    public void setLastUpdateTime() {
        lastUpdateTime = LocalDateTime.now(ZoneId.systemDefault());
    }

    public String getLastUpdateTimeAsString() {
        if (lastUpdateTime != null) {
            return dateFormatter.format(lastUpdateTime);
        }
        return null;
    }
}