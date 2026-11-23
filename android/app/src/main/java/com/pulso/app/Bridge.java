package com.pulso.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Build;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.speech.tts.TextToSpeech;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Puente entre JavaScript y Android.
 * Cada método con @JavascriptInterface se puede llamar desde la página como window.PulsoNative.metodo().
 * Los resultados asincrónicos (voz terminada, foto lista, token de Google) vuelven a la página
 * ejecutando una función global de JavaScript (PulsoSpoke, PulsoPhotoReady, PulsoGoogle).
 */
public class Bridge {
    private final MainActivity act;
    private static String link;
    private TextToSpeech tts;

    private String voiceName;

    // Al crear el puente se inicia el motor de voz del sistema (TextToSpeech) en español y se elige la
    // voz más natural instalada. Se usa como respaldo de las frases grabadas del coach.
    Bridge(MainActivity a) {
        act = a;
        tts = new TextToSpeech(a, st -> {
            if (st != TextToSpeech.SUCCESS) return;
            Locale[] ls = {new Locale("es", "US"), new Locale("es", "MX"), new Locale("es", "AR"), new Locale("es", "ES"), new Locale("es")};
            for (Locale l : ls) { int r = tts.isLanguageAvailable(l); if (r >= TextToSpeech.LANG_AVAILABLE) { tts.setLanguage(l); break; } }
            pickBest();
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { act.js("window.PulsoSpoke&&PulsoSpoke()"); }
                @Override public void onError(String id) {}
            });
        });
    }

    // Lista de voces en español instaladas, de la más natural a la menos natural
    private java.util.List<android.speech.tts.Voice> spanish() {
        java.util.List<android.speech.tts.Voice> out = new java.util.ArrayList<>();
        try { for (android.speech.tts.Voice v : tts.getVoices()) {
            if (v.getLocale() == null || !"es".equals(v.getLocale().getLanguage())) continue;
            if (v.getFeatures() != null && v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
            out.add(v); } } catch (Exception ignored) {}
        final String[] pref = {"US", "MX", "AR", "419", "CO", "ES"};
        out.sort((x, y) -> {
            if (x.getQuality() != y.getQuality()) return y.getQuality() - x.getQuality();
            if (x.isNetworkConnectionRequired() != y.isNetworkConnectionRequired()) return x.isNetworkConnectionRequired() ? -1 : 1;
            int px = 9, py = 9; for (int i = 0; i < pref.length; i++) { if (pref[i].equals(x.getLocale().getCountry())) px = i; if (pref[i].equals(y.getLocale().getCountry())) py = i; }
            if (px != py) return px - py;
            return x.getName().compareTo(y.getName());
        });
        return out;
    }

    private void pickBest() {
        if (voiceName != null) { applyVoice(voiceName); return; }
        java.util.List<android.speech.tts.Voice> l = spanish();
        for (android.speech.tts.Voice v : l) { if (!v.isNetworkConnectionRequired() || online()) { tts.setVoice(v); return; } }
    }

    private boolean online() {
        try { android.net.ConnectivityManager cm = (android.net.ConnectivityManager) act.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
              android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
              return nc != null && nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET); } catch (Exception e) { return false; }
    }

    private void applyVoice(String n) {
        for (android.speech.tts.Voice v : spanish()) if (v.getName().equals(n)) { if (!v.isNetworkConnectionRequired() || online()) tts.setVoice(v); return; }
    }

    // Lista de voces disponibles (para el selector de voz del coach).
    @JavascriptInterface public String voices() {
        try { org.json.JSONArray a = new org.json.JSONArray();
            for (android.speech.tts.Voice v : spanish()) { org.json.JSONObject o = new org.json.JSONObject();
                o.put("n", v.getName()); o.put("l", v.getLocale().toLanguageTag()); o.put("q", v.getQuality()); o.put("net", v.isNetworkConnectionRequired()); a.put(o); }
            return a.toString(); } catch (Exception e) { return "[]"; }
    }

    @JavascriptInterface public void setVoice(String n) { voiceName = n; if (tts != null) applyVoice(n); }

    @JavascriptInterface public String curVoice() { try { return tts.getVoice() == null ? "" : tts.getVoice().getName(); } catch (Exception e) { return ""; } }

    @JavascriptInterface public void ttsSettings() {
        act.runOnUiThread(() -> { try { Intent i = new Intent("com.android.settings.TTS_SETTINGS"); i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); act.startActivity(i); } catch (Exception e) {} });
    }

    static synchronized void setLink(String l) { if (l != null) link = l; }

    @JavascriptInterface public synchronized String takeLink() { String l = link; link = null; return l; }

    // Habla un texto. add=1 lo pone en cola; si no, interrumpe lo que se estaba diciendo.
    @JavascriptInterface public void say(String t, float pitch, float rate, int add) {
        if (tts == null || t == null) return;
        tts.setPitch(pitch); tts.setSpeechRate(rate);
        Bundle p = new Bundle(); p.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
        tts.speak(t, add == 1 ? TextToSpeech.QUEUE_ADD : TextToSpeech.QUEUE_FLUSH, p, "pulso" + System.nanoTime());
    }

    @JavascriptInterface public void shush() { if (tts != null) tts.stop(); }

    // Abre una página en una pestaña de Chrome dentro de la app (Custom Tabs), por ejemplo para pagos.
    @JavascriptInterface public void openTab(String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        Bundle b = new Bundle(); b.putBinder("android.support.customtabs.extra.SESSION", null); i.putExtras(b);
        i.putExtra("android.support.customtabs.extra.TOOLBAR_COLOR", 0xFF12101B);
        i.putExtra("android.support.customtabs.extra.TITLE_VISIBILITY", 1);
        act.startActivity(i);
    }

    private PendingIntent pi(int id, String t, String body) {
        Intent i = new Intent(act, Reminder.class);
        if (t != null) { i.putExtra("t", t); i.putExtra("b", body); i.putExtra("id", id); }
        return PendingIntent.getBroadcast(act, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // Programa un recordatorio exacto con AlarmManager: suena aunque la app esté cerrada o el
    // celular en reposo. Si el sistema no permite alarmas exactas, se usa una aproximada.
    @JavascriptInterface public void schedule(int id, long at, String t, String body) {
        AlarmManager am = (AlarmManager) act.getSystemService(android.content.Context.ALARM_SERVICE);
        try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(id, t, body)); }
        catch (SecurityException e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(id, t, body)); }
    }

    @JavascriptInterface public void scheduleInexact(int id, long at, String t, String body) {
        AlarmManager am = (AlarmManager) act.getSystemService(android.content.Context.ALARM_SERVICE);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(id, t, body));
    }

    @JavascriptInterface public void cancel(int id) {
        AlarmManager am = (AlarmManager) act.getSystemService(android.content.Context.ALARM_SERVICE);
        am.cancel(pi(id, null, null));
    }

    // Permite girar la pantalla (modo cámara) o la fija en vertical.
    @JavascriptInterface public void orient(int mode) {
        act.runOnUiThread(() -> act.setRequestedOrientation(mode == 1 ? ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
    }

    // Mantiene la pantalla encendida durante el entrenamiento.
    @JavascriptInterface public void keepOn(boolean on) {
        act.runOnUiThread(() -> { if (on) act.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); else act.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); });
    }

    @JavascriptInterface public boolean hasNativeGoogle() { return true; }

    // Selector de fotos de Android: un toque en la foto y vuelve directo a la app
    @JavascriptInterface public void pickPhoto() {
        act.runOnUiThread(() -> {
            Intent pick;
            if (Build.VERSION.SDK_INT >= 33) pick = new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);
            else { pick = new Intent(Intent.ACTION_PICK); pick.setType("image/*"); }
            try { act.startActivityForResult(pick, MainActivity.REQ_PHOTO); }
            catch (Exception e) {
                Intent alt = new Intent(Intent.ACTION_GET_CONTENT); alt.addCategory(Intent.CATEGORY_OPENABLE); alt.setType("image/*");
                try { act.startActivityForResult(alt, MainActivity.REQ_PHOTO); } catch (Exception e2) { act.js("window.PulsoPhotoReady&&PulsoPhotoReady()"); }
            }
        });
    }

    @JavascriptInterface public String takePhoto() { String p = MainActivity.pendingPhoto; MainActivity.pendingPhoto = null; return p; }

    // Lee la foto, la achica (máx. 1600 px) y la endereza según la orientación de la cámara
    static String loadPhoto(android.content.Context c, Uri u) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
            int s = 1; while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= 1600) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options(); o2.inSampleSize = s;
            Bitmap bm; try (InputStream in = c.getContentResolver().openInputStream(u)) { bm = BitmapFactory.decodeStream(in, null, o2); }
            if (bm == null) return null;
            int rot = 0;
            try (InputStream in = c.getContentResolver().openInputStream(u)) {
                int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                rot = ori == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : ori == ExifInterface.ORIENTATION_ROTATE_180 ? 180 : ori == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            } catch (Exception ignored) {}
            float k = Math.min(1f, 1600f / Math.max(bm.getWidth(), bm.getHeight()));
            Matrix m = new Matrix(); m.postScale(k, k); if (rot != 0) m.postRotate(rot);
            Bitmap out = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(); out.compress(Bitmap.CompressFormat.JPEG, 88, bo);
            return "data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable t) { return null; }
    }

    private void back(String tok, String err) {
        act.js("window.PulsoGoogle&&PulsoGoogle(" + (tok == null ? "null" : JSONObject.quote(tok)) + "," + (err == null ? "null" : JSONObject.quote(err)) + ")");
    }

    // Inicio de sesión con Google usando Credential Manager (la API actual de Android).
    // Devuelve un "ID token" que JavaScript le pasa a Firebase Authentication.
    // web_client_id (res/values/strings.xml) es el ID de cliente web del proyecto de Firebase.
    @JavascriptInterface public void googleSignIn() {
        act.runOnUiThread(() -> {
            try {
                GetSignInWithGoogleOption opt = new GetSignInWithGoogleOption.Builder(act.getString(R.string.web_client_id)).build();
                GetCredentialRequest req = new GetCredentialRequest.Builder().addCredentialOption(opt).build();
                CredentialManager.Companion.create(act).getCredentialAsync(act, req, new CancellationSignal(), act.getMainExecutor(),
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override public void onResult(GetCredentialResponse r) {
                            Credential c = r.getCredential();
                            if (c instanceof CustomCredential && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                                try { back(GoogleIdTokenCredential.createFrom(c.getData()).getIdToken(), null); }
                                catch (Exception e) { back(null, "parse"); }
                            } else back(null, "type");
                        }
                        @Override public void onError(GetCredentialException e) {
                            back(null, e instanceof GetCredentialCancellationException ? "cancel" : e instanceof NoCredentialException ? "nocred" : "err:" + e.getType());
                        }
                    });
            } catch (Throwable t) { back(null, "err:" + t.getClass().getSimpleName()); }
        });
    }
}
