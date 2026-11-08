package com.tordenbro.carasengif;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Caras en GIF: muestra el editor (assets/www/index.html) en un WebView,
 * recibe GIF/vídeos desde "Compartir" y guarda o comparte el resultado.
 */
public class MainActivity extends Activity {

    private static final int FILE_REQ = 1;
    private static final String HOST = "appassets.androidplatform.net";
    private static final String BASE = "https://" + HOST + "/assets/www/index.html";

    private WebView web;
    private WebViewAssetLoader loader;
    private ValueCallback<Uri[]> fileCb;

    // Archivo recibido desde "Compartir"; el editor lo lee en /shared/file
    private volatile Uri sharedUri;
    private volatile String sharedType = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/shared/", path -> {
                    Uri u = sharedUri;
                    if (u == null) return null;
                    try {
                        InputStream in = getContentResolver().openInputStream(u);
                        if (in == null) return null;
                        String t = sharedType.isEmpty() ? "application/octet-stream" : sharedType;
                        return new WebResourceResponse(t, null, in);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                List<String> mimes = new ArrayList<>();
                String[] accept = params.getAcceptTypes();
                if (accept != null) {
                    for (String a : accept) {
                        if (a == null) continue;
                        for (String x : a.split(",")) {
                            x = x.trim();
                            if (x.contains("/")) mimes.add(x);
                        }
                    }
                }
                if (!mimes.isEmpty()) i.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toArray(new String[0]));
                try {
                    startActivityForResult(Intent.createChooser(i, "Elegir archivo"), FILE_REQ);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "Android");

        if (!handleShare(getIntent())) web.loadUrl(BASE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShare(intent);
    }

    /** Si la app se abrió desde "Compartir", carga el editor con el archivo recibido. */
    @SuppressWarnings("deprecation")
    private boolean handleShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return false;
        Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (u == null) return false;
        String type = getContentResolver().getType(u);
        if (type == null) type = intent.getType();
        sharedType = type == null ? "" : type;
        sharedUri = u;
        String name = queryName(u);
        web.loadUrl(BASE + "?shared=1&name=" + Uri.encode(name) + "&type=" + Uri.encode(sharedType)
                + "&t=" + System.currentTimeMillis());
        return true;
    }

    private String queryName(Uri u) {
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null) return n;
            }
        } catch (Exception ignored) {
        }
        return "compartido";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_REQ) {
            if (fileCb != null) {
                fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCb = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    /** Guarda el GIF en Imágenes/Caras en GIF y devuelve su Uri (o null si falla). */
    private Uri writeGif(String base64) {
        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            ContentResolver cr = getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, "cara-en-gif-" + System.currentTimeMillis() + ".gif");
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/gif");
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Caras en GIF");
            cv.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri u = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (u == null) return null;
            try (OutputStream os = cr.openOutputStream(u)) {
                if (os == null) return null;
                os.write(data);
            }
            cv.clear();
            cv.put(MediaStore.Images.Media.IS_PENDING, 0);
            cr.update(u, cv, null, null);
            return u;
        } catch (Exception e) {
            return null;
        }
    }

    /** Funciones que el editor (JavaScript) puede llamar como window.Android.xxx */
    private class Bridge {
        @JavascriptInterface
        public String saveGif(String base64) {
            return writeGif(base64) != null ? "ok" : "error";
        }

        @JavascriptInterface
        public String shareGif(String base64) {
            Uri u = writeGif(base64);
            if (u == null) return "error";
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/gif");
            send.putExtra(Intent.EXTRA_STREAM, u);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            runOnUiThread(() -> startActivity(Intent.createChooser(send, "Compartir GIF")));
            return "ok";
        }
    }
}
