package cz.testujivsechno.inventura;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQUEST_CAMERA = 701;
    private WebView webView;
    private PermissionRequest pendingWebPermission;
    private WebViewAssetLoader assetLoader;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(android.graphics.Color.rgb(237,239,241));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(237,239,241));
        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        webView = new WebView(this);
        setContentView(webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, android.webkit.WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean asksForVideo = false;
                    for (String resource : request.getResources()) if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) asksForVideo = true;
                    if (!asksForVideo) { request.deny(); return; }
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        pendingWebPermission = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
                    }
                });
            }
        });
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) loadApp();
        else requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
    }

    private void loadApp() { webView.loadUrl("https://appassets.androidplatform.net/assets/inventura.html"); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_CAMERA) {
            boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            if (pendingWebPermission != null) {
                if (granted) pendingWebPermission.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                else pendingWebPermission.deny();
                pendingWebPermission = null;
            } else loadApp();
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    public class AndroidBridge {
        @JavascriptInterface public String getNativeCameraInfo() { return buildCameraReport().toString(); }
        @JavascriptInterface public void closeApp() { runOnUiThread(() -> finish()); }

        // HTML exports a Blob URL. Read it inside the same WebView origin, then pass bytes to Android.
        @JavascriptInterface public void saveFile(String fileName, String blobUrl) {
            String js = "(async()=>{try{const r=await fetch(" + JSONObject.quote(blobUrl) + ");const b=await r.blob();const a=new Uint8Array(await b.arrayBuffer());let s='';for(let i=0;i<a.length;i++)s+=String.fromCharCode(a[i]);Android.receiveFile(" + JSONObject.quote(fileName) + ",b.type||'application/octet-stream',btoa(s));}catch(e){console.error('Uložení souboru selhalo',e);}})();";
            runOnUiThread(() -> webView.evaluateJavascript(js, null));
        }
        @JavascriptInterface public void receiveFile(String fileName, String mimeType, String base64) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                String safeName = new File(fileName == null ? "export.bin" : fileName).getName();
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, safeName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType == null ? "application/octet-stream" : mimeType);
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Inventura");
                    values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new IllegalStateException("Nelze vytvořit soubor");
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) { if (out == null) throw new IllegalStateException("Nelze otevřít soubor"); out.write(bytes); }
                    values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0); getContentResolver().update(uri, values, null, null);
                } else {
                    File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Inventura");
                    if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Nelze vytvořit složku");
                    try (FileOutputStream out = new FileOutputStream(new File(dir, safeName))) { out.write(bytes); }
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Soubor uložen: " + safeName, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Uložení se nezdařilo: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }
    }

    private JSONObject buildCameraReport() {
        JSONObject root = new JSONObject();
        JSONArray cameras = new JSONArray();
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            root.put("apiLevel", Build.VERSION.SDK_INT);
            root.put("manufacturer", Build.MANUFACTURER);
            root.put("model", Build.MODEL);
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                JSONObject item = new JSONObject();
                item.put("id", id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                String facingName = "unknown";
                if (facing != null) {
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) facingName = "back";
                    else if (facing == CameraCharacteristics.LENS_FACING_FRONT) facingName = "front";
                    else if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) facingName = "external";
                }
                item.put("facing", facingName);
                item.put("hardwareLevel", hardwareLevel(c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)));
                item.put("flashAvailable", Boolean.TRUE.equals(c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)));
                float[] focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                JSONArray focalLengths = new JSONArray();
                if (focal != null) for (float f : focal) focalLengths.put(f);
                item.put("focalLengthsMm", focalLengths);
                android.util.Size size = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                if (size != null) { item.put("sensorWidth", size.getWidth()); item.put("sensorHeight", size.getHeight()); }
                Set<String> physical = Build.VERSION.SDK_INT >= 28 ? c.getPhysicalCameraIds() : null;
                JSONArray physicalIds = new JSONArray();
                if (physical != null) for (String physicalId : physical) physicalIds.put(physicalId);
                item.put("physicalCameraIds", physicalIds);
                int[] caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                boolean logical = false;
                if (caps != null && Build.VERSION.SDK_INT >= 28) for (int cap : caps) if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) logical = true;
                item.put("logicalMultiCamera", logical);
                cameras.put(item);
            }
            root.put("cameras", cameras);
            root.put("ok", true);
        } catch (Exception e) {
            try { root.put("ok", false); root.put("error", e.getClass().getSimpleName() + ": " + e.getMessage()); root.put("cameras", cameras); }
            catch (Exception ignored) {}
        }
        return root;
    }

    private String hardwareLevel(Integer level) {
        if (level == null) return "unknown";
        switch (level) {
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY: return "LEGACY";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED: return "LIMITED";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL: return "FULL";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3: return "LEVEL_3";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL: return "EXTERNAL";
            default: return "unknown";
        }
    }
}
