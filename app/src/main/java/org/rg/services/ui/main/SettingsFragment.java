package org.rg.services.ui.main;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

import org.rg.services.R;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static SettingsFragment INSTANCE;

    public static SettingsFragment getInstance() {
        if (INSTANCE == null) {
            synchronized (SettingsFragment.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SettingsFragment();
                }
            }
        }
        return INSTANCE;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
        setPreferenceType("binanceApiKey", InputType.TYPE_CLASS_TEXT);
        setPreferenceType("binanceApiSecret", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        setPreferenceType("cryptoComApiKey", InputType.TYPE_CLASS_TEXT);
        setPreferenceType("cryptoComApiSecret", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        setPreferenceType("gitHubAuthorizationToken", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        setPreferenceType("intervalBetweenRequestGroups", InputType.TYPE_CLASS_NUMBER);
    }

    private void setPreferenceType(String id, int type) {
        final EditTextPreference preference = findPreference(id);
        if (preference != null) {
            preference.setOnBindEditTextListener(
                new EditTextPreference.OnBindEditTextListener() {
                    @Override
                    public void onBindEditText(@NonNull EditText editText) {
                        editText.setInputType(type);
                    }
                }
            );
        }
    }
}