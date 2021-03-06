package org.wordpress.android.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.helpshift.support.Support;

import org.wordpress.android.util.LocaleManager;

public class HelpshiftDeepLinkReceiver extends AppCompatActivity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String action = getIntent().getAction();
        Uri data = getIntent().getData();

        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            String faqid = data.getQueryParameter("faqid");
            String sectionid = data.getQueryParameter("sectionid");
            if (faqid != null) {
                Support.showSingleFAQ(this, faqid);
            } else if (sectionid != null) {
                Support.showFAQSection(this, sectionid);
            }
        }
        finish();
    }
}
