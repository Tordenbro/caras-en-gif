package com.tordenbro.carasengif;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Caras en GIF: muestra el editor (assets/www/index.html) en un WebView,
 * recibe GIF/vídeos desde "Compartir" y guarda o comparte el resultado.
 */
public class MainActivity extends Activity {

    private static final int FILE_REQ = 1;
    private static final int TREE_REQ = 2;
    private static final String WA_MEDIA = "primary:Android/media/com.whatsapp/WhatsApp/Media/";
    private static final String HOST = "appassets.androidplatform.net";
    private static final String BASE = "https://" + HOST + "/assets/www/index.html";

    private WebView web;
    private WebViewAssetLoader loader;
    private ValueCallback<Uri[]> fileCb;

    // Archivos recibidos desde "Compartir"; el editor los lee en /shared/0, /shared/1…
    private volatile List<Uri> sharedUris = new ArrayList<>();
    private volatile String pendingTreeKind = "gifs";

    // GIFs encontrados en la carpeta de WhatsApp (el editor los pide por /wa/thumb/N y /wa/file/N)
    private static class WaItem {
        Uri uri; String name; String mime; long date; long size;
    }
    private volatile List<WaItem> waItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/shared/", path -> {
                    List<Uri> list = sharedUris;
                    String digits = path.replaceAll("[^0-9]", "");
                    int idx = digits.isEmpty() ? 0 : Integer.parseInt(digits);
                    if (idx < 0 || idx >= list.size()) return null;
                    Uri u = list.get(idx);
                    try {
                        InputStream in = getContentResolver().openInputStream(u);
                        if (in == null) return null;
                        String t = getContentResolver().getType(u);
                        return new WebResourceResponse(t == null ? "application/octet-stream" : t, null, in);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .addPathHandler("/wa/", this::serveWa)
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

    /** Si la app se abrió desde "Compartir" (uno o varios archivos), carga el editor con ellos. */
    @SuppressWarnings("deprecation")
    private boolean handleShare(Intent intent) {
        if (intent == null) return false;
        List<Uri> list = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) list.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> many = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (many != null) for (Uri u : many) if (u != null && list.size() < 50) list.add(u);
        }
        if (list.isEmpty()) return false;
        sharedUris = list;
        Uri first = list.get(0);
        String type = getContentResolver().getType(first);
        if (type == null) type = intent.getType();
        web.loadUrl(BASE + "?shared=1&n=" + list.size() + "&name=" + Uri.encode(queryName(first))
                + "&type=" + Uri.encode(type == null ? "" : type) + "&t=" + System.currentTimeMillis());
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
        if (requestCode == TREE_REQ) {
            boolean ok = false;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri tree = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    prefs().edit().putString("waTree_" + pendingTreeKind, tree.toString()).apply();
                    ok = true;
                } catch (Exception ignored) {
                }
            }
            js("window.onWaConnected&&window.onWaConnected(" + ok + ",'" + pendingTreeKind + "')");
            return;
        }
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

    private SharedPreferences prefs() {
        return getSharedPreferences("caras", MODE_PRIVATE);
    }

    private void js(String code) {
        runOnUiThread(() -> web.evaluateJavascript(code, null));
    }

    /** Carpeta de WhatsApp (gifs o stickers) a la que el usuario dio acceso, o null si aún no. */
    private Uri waTree(String kind) {
        String t = prefs().getString("waTree_" + kind, null);
        if (t == null && "gifs".equals(kind)) t = prefs().getString("waTree", null);
        if (t == null) return null;
        Uri tree = Uri.parse(t);
        for (UriPermission p : getContentResolver().getPersistedUriPermissions()) {
            if (p.getUri().equals(tree) && p.isReadPermission()) return tree;
        }
        return null;
    }

    private static boolean isStickerLike(String name, String mime) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.startsWith(".")) return false;
        if (n.endsWith(".webp") || n.endsWith(".png")) return true;
        return mime != null && (mime.equals("image/webp") || mime.equals("image/png"));
    }

    private static boolean isGifLike(String name, String mime) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.startsWith(".")) return false;
        if (n.endsWith(".mp4") || n.endsWith(".gif") || n.endsWith(".webp")) return true;
        return mime != null && (mime.startsWith("video/") || mime.equals("image/gif") || mime.equals("image/webp"));
    }

    private void walk(Uri tree, String docId, int depth, List<WaItem> out, boolean stickers) {
        if (out.size() >= 600) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE};
        List<String> subdirs = new ArrayList<>();
        try (Cursor c = getContentResolver().query(children, cols, null, null, null)) {
            if (c == null) return;
            while (c.moveToNext()) {
                String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    subdirs.add(id);
                } else if (stickers ? isStickerLike(name, mime) : isGifLike(name, mime)) {
                    WaItem it = new WaItem();
                    it.uri = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    it.name = name == null ? "gif" : name;
                    it.mime = mime == null ? "" : mime;
                    it.date = c.isNull(3) ? 0 : c.getLong(3);
                    it.size = c.isNull(4) ? 0 : c.getLong(4);
                    out.add(it);
                }
            }
        } catch (Exception ignored) {
        }
        if (depth < 2) for (String d : subdirs) walk(tree, d, depth + 1, out, stickers);
    }

    /** Lista GIFs o stickers y avisa al editor con window.onWaList({...}). */
    private void listWa(String kind) {
        new Thread(() -> {
            try {
                Uri tree = waTree(kind);
                JSONObject res = new JSONObject();
                res.put("kind", kind);
                if (tree == null) {
                    res.put("need", true);
                } else {
                    List<WaItem> list = new ArrayList<>();
                    walk(tree, DocumentsContract.getTreeDocumentId(tree), 0, list, "stickers".equals(kind));
                    Collections.sort(list, (a, b) -> Long.compare(b.date, a.date));
                    if (list.size() > 400) list = new ArrayList<>(list.subList(0, 400));
                    waItems = list;
                    JSONArray arr = new JSONArray();
                    for (WaItem it : list) {
                        JSONObject o = new JSONObject();
                        o.put("name", it.name);
                        o.put("mime", it.mime);
                        o.put("date", it.date);
                        o.put("size", it.size);
                        arr.put(o);
                    }
                    res.put("items", arr);
                    res.put("v", System.currentTimeMillis());
                }
                js("window.onWaList&&window.onWaList(" + res + ")");
            } catch (Exception e) {
                js("window.onWaList&&window.onWaList({error:true,kind:'" + kind + "'})");
            }
        }).start();
    }

    /** Sirve /wa/thumb/N (miniatura JPEG) y /wa/file/N (el archivo). */
    private WebResourceResponse serveWa(String path) {
        try {
            String[] parts = path.split("/");
            if (parts.length < 2) return null;
            int i = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
            List<WaItem> list = waItems;
            if (i < 0 || i >= list.size()) return null;
            WaItem it = list.get(i);
            if ("file".equals(parts[0])) {
                InputStream in = getContentResolver().openInputStream(it.uri);
                if (in == null) return null;
                return new WebResourceResponse(it.mime.isEmpty() ? "application/octet-stream" : it.mime, null, in);
            }
            if ("thumb".equals(parts[0])) {
                File dir = new File(getCacheDir(), "wathumb");
                if (!dir.exists()) dir.mkdirs();
                String low = it.name.toLowerCase(Locale.ROOT);
                boolean png = !(it.mime.startsWith("video/") || low.endsWith(".mp4"));
                File f = new File(dir, Integer.toHexString(it.uri.toString().hashCode()) + "_" + it.date + (png ? ".png" : ".jpg"));
                if (!f.exists()) {
                    Bitmap bm = thumb(it);
                    if (bm == null) return null;
                    try (FileOutputStream fo = new FileOutputStream(f)) {
                        if (png) bm.compress(Bitmap.CompressFormat.PNG, 100, fo);
                        else bm.compress(Bitmap.CompressFormat.JPEG, 75, fo);
                    }
                    bm.recycle();
                }
                return new WebResourceResponse(png ? "image/png" : "image/jpeg", null, new FileInputStream(f));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Bitmap thumb(WaItem it) {
        String n = it.name.toLowerCase(Locale.ROOT);
        boolean video = it.mime.startsWith("video/") || n.endsWith(".mp4");
        if (video) {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(this, it.uri);
                return r.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 220, 220);
            } catch (Exception e) {
                return null;
            } finally {
                try {
                    r.release();
                } catch (Exception ignored) {
                }
            }
        }
        try {
            ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), it.uri);
            return ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                float k = Math.min(1f, 220f / Math.max(w, h));
                decoder.setTargetSize(Math.max(1, (int) (w * k)), Math.max(1, (int) (h * k)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
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
        public void waList(String kind) {
            listWa("stickers".equals(kind) ? "stickers" : "gifs");
        }

        @JavascriptInterface
        public void waConnect(String kind) {
            final String k = "stickers".equals(kind) ? "stickers" : "gifs";
            runOnUiThread(() -> {
                pendingTreeKind = k;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                try {
                    i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri(
                            "com.android.externalstorage.documents",
                            WA_MEDIA + ("stickers".equals(k) ? "WhatsApp Stickers" : "WhatsApp Animated Gifs")));
                } catch (Exception ignored) {
                }
                try {
                    startActivityForResult(i, TREE_REQ);
                } catch (Exception e) {
                    js("window.onWaConnected&&window.onWaConnected(false,'" + k + "')");
                }
            });
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
