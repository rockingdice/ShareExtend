package com.zt.shareextend;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plugin method host for presenting a share sheet via Intent
 */
public class ShareExtendPlugin
    implements MethodChannel.MethodCallHandler,
               PluginRegistry.RequestPermissionsResultListener {

  /// the authorities for FileProvider
  private static final int CODE_ASK_PERMISSION = 100;
  private static final String CHANNEL = "com.zt.shareextend/share_extend";

  private final Registrar mRegistrar;
  private List<String> list;
  private String type;

  public static void registerWith(Registrar registrar) {
    MethodChannel channel = new MethodChannel(registrar.messenger(), CHANNEL);
    final ShareExtendPlugin instance = new ShareExtendPlugin(registrar);
    registrar.addRequestPermissionsResultListener(instance);
    channel.setMethodCallHandler(instance);
  }

  private ShareExtendPlugin(Registrar registrar) {
    this.mRegistrar = registrar;
  }

  @Override
  public void onMethodCall(MethodCall call, MethodChannel.Result result) {
    if (call.method.equals("share")) {
      if (!(call.arguments instanceof Map)) {
        throw new IllegalArgumentException("Map argument expected");
      }
      // Android does not support showing the share sheet at a particular point
      // on screen.
      share((List)call.argument("list"), (String)call.argument("type"));
      result.success(null);
    } else {
      result.notImplemented();
    }
  }

  private void share(List<String> list, String type) {
    appendLog("begin share");
    if (list == null || list.isEmpty()) {
      throw new IllegalArgumentException("Non-empty list expected");
    }
    this.list = list;
    this.type = type;

    Intent shareIntent = new Intent();
    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

    if ("text".equals(type)) {
      shareIntent.setAction(Intent.ACTION_SEND);
      shareIntent.putExtra(Intent.EXTRA_TEXT, list.get(0));
      shareIntent.setType("text/plain");
    } else {
      if (ShareUtils.shouldRequestPermission(list)) {
        appendLog("should request permission");
        if (!checkPermission()) {
          appendLog("no permission");
          requestPermission();
          appendLog("permission requested");
          return;
        } else {
          appendLog("permission granted");
        }
      } else {
        appendLog("dont need request permission");
      }

      ArrayList<Uri> uriList = new ArrayList<>();
      for (String path : list) {
        File f = new File(path);
        Uri uri = ShareUtils.getUriForFile(mRegistrar.activity(), f, type);
        appendLog("get uri");
        uriList.add(uri);
      }

      if ("image".equals(type)) {
        shareIntent.setType("image/*");
      } else if ("video".equals(type)) {
        shareIntent.setType("video/*");
      } else {
        shareIntent.setType("application/*");
      }
      if (uriList.size() == 1) {
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, uriList.get(0));
        appendLog("send action");
      } else {
        shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
      }
    }
    startChooserActivity(shareIntent);
    appendLog("show activity");
  }

  private void startChooserActivity(Intent shareIntent) {
    Intent chooserIntent =
        Intent.createChooser(shareIntent, null /* dialog title optional */);
    if (mRegistrar.activity() != null) {
      mRegistrar.activity().startActivity(chooserIntent);
      appendLog("show activity 1");
    } else {
      chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      mRegistrar.context().startActivity(chooserIntent);

      appendLog("show activity 2");
    }
  }

  private boolean checkPermission() {
    return ContextCompat.checkSelfPermission(
               mRegistrar.context(),
               Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED;
  }

  private void requestPermission() {
    ActivityCompat.requestPermissions(
        mRegistrar.activity(),
        new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
        CODE_ASK_PERMISSION);
  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] perms,
                                            int[] grantResults) {
    if (requestCode == CODE_ASK_PERMISSION && grantResults.length > 0 &&
        grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      share(list, type);
    }
    return false;
  }

  public void appendLog(String text) {
    File logFile = new File("/data/mhwo-log.file");
    if (!logFile.exists()) {
      try {
        logFile.createNewFile();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    try {
      // BufferedWriter for performance, true to set append to file flag
      BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
      buf.append(text);
      buf.newLine();
      buf.close();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
}
