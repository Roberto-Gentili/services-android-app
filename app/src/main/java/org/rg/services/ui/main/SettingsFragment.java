package org.rg.services.ui.main;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.rg.services.R;
import org.rg.util.LoggerChain;

import java.util.Optional;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static SettingsFragment INSTANCE;

    public SettingsFragment() {}

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
        setEditTextPreferenceType("binanceApiKey", InputType.TYPE_CLASS_TEXT);
        setEditTextPreferenceType("binanceApiSecret", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        setEditTextPreferenceType("cryptoComApiKey", InputType.TYPE_CLASS_TEXT);
        setEditTextPreferenceType("cryptoComApiSecret", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        setEditTextPreferenceType("cryptoComTimeOffset", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        setEditTextPreferenceType("gitHubAuthorizationToken", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        setEditTextPreferenceType("coinsToBeAlwaysDisplayed", InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        setEditTextPreferenceType("intervalBetweenRequestGroups", InputType.TYPE_CLASS_NUMBER);
        setEditTextPreferenceType("totalInvestment", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Optional.ofNullable(setEditTextPreferenceType("threadPoolSize", InputType.TYPE_CLASS_NUMBER)).ifPresent(pref -> setMinMaxFilter(pref, 6, 48));
    }

    private void setMinMaxFilter(EditTextPreference pref, int min, int max) {
        pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValueAsString) {
                Long newValue = Long.valueOf((String)newValueAsString);
                if (newValue >= min && newValue <= max) {
                    return true;
                }
                getActivity().runOnUiThread(() -> {
                    LoggerChain.getInstance().logError("The value must be between " + min + " and " + max);
                });
                return false;
            }
        });
    }

    private EditTextPreference setEditTextPreferenceType(String id, int type) {
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
        return preference;
    }
}