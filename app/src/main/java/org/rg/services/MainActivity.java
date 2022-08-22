package org.rg.services;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.StrictMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.rg.services.ui.main.MainFragment;
import org.rg.services.ui.main.SettingsFragment;
import org.rg.util.LoggerChain;
import org.rg.util.RestTemplateSupplier;

import java.text.DateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

public class MainActivity extends AppCompatActivity {
    private LocalDateTime lastUpdateTime;
    private DateTimeFormatter dateFormatter;
    private ExecutorService executorService;

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
        //executorService = ForkJoinPool.commonPool();
        executorService = Executors.newFixedThreadPool(12);
        LoggerChain.getInstance().appendExceptionLogger(message -> {
            runOnUiThread(()-> {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            });
        });
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
            .replace(R.id.container, new SettingsFragment())
            .commit();
        setTitle(getResources().getString(R.string.settingsLabelText));
    }

    public void goToMainView() {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.container, new MainFragment())
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

    public ExecutorService getExecutorService(){
        return this.executorService;
    }
}