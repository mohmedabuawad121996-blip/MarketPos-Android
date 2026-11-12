package com.marketpos.app;

import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import java.io.IOException;
import java.util.Collections;

/** Google sign-in and uploads to Google Drive (drive.file scope: only files this app creates). */
final class DriveHelper {

    private DriveHelper() {
    }

    static GoogleSignInClient signInClient(AppCompatActivity activity) {
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                .build();
        return GoogleSignIn.getClient(activity, options);
    }

    /** The signed-in account, or null when nobody is signed in yet. */
    static GoogleSignInAccount account(AppCompatActivity activity) {
        return GoogleSignIn.getLastSignedInAccount(activity);
    }

    /** Uploads a local file to the user's Drive on a background thread and reports the result in a toast. */
    static void upload(final AppCompatActivity activity, final GoogleSignInAccount account,
                       final java.io.File local, final String mimeType) {
        Toast.makeText(activity, "جاري الرفع إلى Google Drive...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            String message;
            try {
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        activity, Collections.singleton(DriveScopes.DRIVE_FILE));
                credential.setSelectedAccount(account.getAccount());

                Drive drive = new Drive.Builder(
                        AndroidHttp.newCompatibleTransport(),
                        GsonFactory.getDefaultInstance(),
                        credential)
                        .setApplicationName("MarketPos")
                        .build();

                File meta = new File().setName(local.getName());
                File created = drive.files()
                        .create(meta, new FileContent(mimeType, local))
                        .setFields("id, name")
                        .execute();
                message = "تم الرفع: " + created.getName();
            } catch (IOException | RuntimeException e) {
                message = "فشل الرفع: " + e.getMessage();
            }
            final String result = message;
            activity.runOnUiThread(() -> Toast.makeText(activity, result, Toast.LENGTH_LONG).show());
        }).start();
    }
}
