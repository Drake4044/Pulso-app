package com.pulso.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.util.ArrayList;

/**
 * Pantalla única de la app.
 *
 * Pulso es una app "híbrida": toda la interfaz está hecha con HTML/CSS/JavaScript (assets/index.html)
 * y se muestra en un WebView. Java se usa solo para lo que el navegador no puede hacer bien solo:
 * permisos de cámara y micrófono, notificaciones programadas, el selector de fotos, la voz del sistema
 * y el inicio de sesión con Google. Esas funciones se exponen a JavaScript con el puente {@link Bridge}.
 *
 * ¿Por qué híbrida y no nativa (Kotlin/Compose) o React Native? Porque así un solo código de interfaz
 * sirve para el celular y para la versión web, se puede iterar muy rápido y el APK queda liviano.
 */
public class MainActivity extends Activity {
    WebView w;
    private PermissionRequest pending;
    private ValueCallback<Uri[]> fileCb;
    private static final int REQ_FILE = 9;
    static final int REQ_PHOTO = 11;
    static volatile String pendingPhoto;
    private static final int REQ_MEDIA = 7;

    // Se crea el canal de notificaciones (obligatorio desde Android 8), se pide permiso para notificar
    // (Android 13+) y se configura el WebView que carga la interfaz.
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("pulso_rutina", "Recordatorios de rutina", NotificationManager.IMPORTANCE_HIGH));
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        w = new WebView(this);
        w.setBackgroundColor(0xFF12101B);
        // sin barras de desplazamiento nativas (se veía una línea blanca a la derecha al arrancar la intro)
        w.setVerticalScrollBarEnabled(false);
        w.setHorizontalScrollBarEnabled(false);
        w.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        // Desde JavaScript se llama como window.PulsoNative.metodo(...). Solo los métodos marcados con
        // @JavascriptInterface en Bridge quedan visibles.
        w.addJavascriptInterface(new Bridge(this), "PulsoNative");
        w.setWebChromeClient(new WebChromeClient() {
            // La página pidió cámara o micrófono (getUserMedia): se traduce a un permiso de Android.
            @Override public void onPermissionRequest(final PermissionRequest r) { runOnUiThread(() -> handlePerm(r)); }
            // sin esto, Android muestra un ícono gris de "play" antes de que arranque el video de la intro
            @Override public android.graphics.Bitmap getDefaultVideoPoster() { return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888); }
            // <input type="file"> en el WebView: se abre el selector de fotos del sistema.
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams fp) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent pick;
                if (Build.VERSION.SDK_INT >= 33) pick = new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);
                else { pick = new Intent(Intent.ACTION_PICK); pick.setType("image/*"); }
                try { startActivityForResult(pick, REQ_FILE); }
                catch (Exception e) {
                    Intent alt = new Intent(Intent.ACTION_GET_CONTENT); alt.addCategory(Intent.CATEGORY_OPENABLE); alt.setType("image/*");
                    try { startActivityForResult(alt, REQ_FILE); } catch (Exception e2) { fileCb = null; cb.onReceiveValue(null); return false; }
                }
                return true;
            }
        });
        Bridge.setLink(dataOf(getIntent()));
        w.loadUrl("file:///android_asset/index.html");
        setContentView(w);
    }

    private static String dataOf(Intent i) { Uri u = i == null ? null : i.getData(); return u == null ? null : u.toString(); }

    // Si falta algún permiso de Android, se pide y se guarda la solicitud del WebView para responderla después.
    private void handlePerm(PermissionRequest r) {
        ArrayList<String> need = new ArrayList<>();
        for (String res : r.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res) && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.CAMERA);
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.RECORD_AUDIO);
        }
        if (need.isEmpty()) { r.grant(r.getResources()); return; }
        if (pending != null) pending.deny();
        pending = r;
        requestPermissions(need.toArray(new String[0]), REQ_MEDIA);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code != REQ_MEDIA || pending == null) return;
        PermissionRequest r = pending; pending = null;
        ArrayList<String> ok = new ArrayList<>();
        for (String x : r.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(x) && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) ok.add(x);
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(x) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) ok.add(x);
        }
        if (ok.isEmpty()) r.deny(); else r.grant(ok.toArray(new String[0]));
    }

    // Vuelve el selector de fotos. La foto se procesa en un hilo aparte (achicar + enderezar) y después
    // se avisa a JavaScript con PulsoPhotoReady(); si Android cerró la app mientras tanto, la foto queda
    // guardada en pendingPhoto y JavaScript la pide al volver.
    @SuppressWarnings("deprecation")
    @Override protected void onActivityResult(int code, int res, Intent data) {
        super.onActivityResult(code, res, data);
        if (code == REQ_PHOTO) {
            final Uri u = (res == RESULT_OK && data != null) ? data.getData() : null;
            if (u == null) { js("window.PulsoPhotoReady&&PulsoPhotoReady()"); return; }
            new Thread(() -> {
                pendingPhoto = Bridge.loadPhoto(this, u);
                js("window.PulsoPhotoReady&&PulsoPhotoReady()");
            }).start();
            return;
        }
        if (code != REQ_FILE || fileCb == null) return;
        Uri u = (res == RESULT_OK && data != null) ? data.getData() : null;
        fileCb.onReceiveValue(u == null ? null : new Uri[]{u});
        fileCb = null;
    }

    // Enlaces pulso://auth/... (vuelta del inicio de sesión web) mientras la app ya estaba abierta.
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); Bridge.setLink(dataOf(i)); }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { if (w != null && w.canGoBack()) w.goBack(); else super.onBackPressed(); }

    // Ejecuta JavaScript en la página desde Java (siempre en el hilo de la interfaz).
    void js(String code) { runOnUiThread(() -> { if (w != null) w.evaluateJavascript(code, null); }); }
}
