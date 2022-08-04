package org.rg.services;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.rg.services.ui.main.MainFragment;
import org.rg.services.ui.main.SettingsFragment;
import org.rg.util.LoggerChain;
import org.rg.util.RestTemplateSupplier;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LoggerChain.getInstance().appendExceptionLogger(message -> {
            runOnUiThread(()-> {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            });
        });
        //RestTemplateSupplier.getSharedInstance().enableRequestLogger();
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
}