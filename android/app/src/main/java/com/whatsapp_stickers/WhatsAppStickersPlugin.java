package io.whatsapp_stickers;

import static io.whatsapp_stickers.StickerPackActivity.ADD_PACK;

import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.HashMap;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/**
 * WhatsAppStickersPlugin
 */
public class WhatsAppStickersPlugin extends BroadcastReceiver implements MethodCallHandler {
//    private final String TAG = "WhatsAppStickersPlugin";
    private  MethodChannel channel;
    private  FlutterPluginBinding binding;
    private  ActivityPluginBinding aBinding;

//    public static void registerWith(Registrar registrar) {
//        final MethodChannel channel = new MethodChannel(registrar.messenger(),
//                "io/whatsapp_stickers");
//        final WhatsAppStickersPlugin plugin = new WhatsAppStickersPlugin(registrar, channel);
//        channel.setMethodCallHandler(plugin);
//    }


//    private WhatsAppStickersPlugin( registrar, MethodChannel channel) {
//        this.registrar = registrar;
//        this.channel = channel;
//
//        IntentFilter intentFilter = new IntentFilter();
//        intentFilter.addAction(StickerPackActivity.ACTION_STICKER_PACK_RESULT);
//        intentFilter.addAction(StickerPackActivity.ACTION_STICKER_PACK_ERROR);
//        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(binding.getApplicationContext());
//        manager.registerReceiver(this, intentFilter);
//    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    @Override
    public void onMethodCall(MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "getPlatformVersion":
                result.success("Android " + android.os.Build.VERSION.RELEASE);
                break;
            case "isWhatsAppInstalled": {
                result.success(WhitelistCheck.isWhatsAppInstalled(binding.getApplicationContext()));
                break;
            }
            case "isWhatsAppConsumerAppInstalled": {
                result.success(WhitelistCheck.isWhatsAppConsumerAppInstalled(binding.getApplicationContext().getPackageManager()));
                break;
            }
            case "isWhatsAppSmbAppInstalled": {
                result.success(WhitelistCheck.isWhatsAppSmbAppInstalled(binding.getApplicationContext().getPackageManager()));
                break;
            }
            case "launchWhatsApp": {
                Intent launchIntent = binding.getApplicationContext().getPackageManager()
                        .getLaunchIntentForPackage(WhitelistCheck.CONSUMER_WHATSAPP_PACKAGE_NAME);
                aBinding.getActivity().startActivity(launchIntent);
                result.success(true);
                break;
            }
            case "isStickerPackInstalled": {
                String stickerPackIdentifier = call.argument("identifier");
                assert stickerPackIdentifier != null;
                final boolean installed = WhitelistCheck.isWhitelisted(binding.getApplicationContext(), stickerPackIdentifier);
                result.success(installed);
                break;
            }
            case "addStickerPack": {
                StickerPackActivity stickerPackActivity = new StickerPackActivity(binding.getApplicationContext());
                aBinding.addActivityResultListener(stickerPackActivity);

                String whatsAppPackage = call.argument("package");
                String stickerPackIdentifier = call.argument("identifier");
                String stickerPackName = call.argument("name");

                Intent intent = StickerPackActivity.createIntentToAddStickerPack(
                        getContentProviderAuthority(binding.getApplicationContext()), stickerPackIdentifier, stickerPackName);
                assert whatsAppPackage != null;
                intent.setPackage(
                        whatsAppPackage.isEmpty() ? WhitelistCheck.CONSUMER_WHATSAPP_PACKAGE_NAME : whatsAppPackage);

                try {
                    aBinding.getActivity().startActivityForResult(intent, ADD_PACK);
                } catch (ActivityNotFoundException e) {
                    String errorMessage = "Sticker pack not added. If you'd like to add it, make sure you update to the latest version of WhatsApp.";
                    result.error(errorMessage, "failed", e);
                }
                break;
            }
            case "updatedStickerPackContentsFile":
                String packageName = binding.getApplicationContext().getPackageName();
                String stickerPackIdentifier = call.argument("identifier");
                Uri uri = Uri.parse("content://" + packageName + ".stickercontentprovider/metadata/" + stickerPackIdentifier);
                binding.getApplicationContext().getContentResolver().notifyChange(uri, null);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    static String getContentProviderAuthority(Context context) {
        return context.getPackageName() + ".stickercontentprovider";
    }

    // BroadcastReceiver implementation.
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        // No action, exit
        if (action == null) {
            return;
        }

        if (action.equals(StickerPackActivity.ACTION_STICKER_PACK_RESULT)) {
            // Success
            Map<String, Object> content = new HashMap<>();
            content.put("action", intent.getStringExtra(StickerPackActivity.EXTRA_STICKER_PACK_ACTION));
            content.put("result", intent.getBooleanExtra(StickerPackActivity.EXTRA_STICKER_PACK_RESULT, false));
            channel.invokeMethod("onSuccess", content);
        } else if (action.equals(StickerPackActivity.ACTION_STICKER_PACK_ERROR)) {
            // Error
            String error = intent.getStringExtra(StickerPackActivity.EXTRA_STICKER_PACK_ERROR);
            Map<String, Object> content = new HashMap<>();
            content.put("action", intent.getStringExtra(StickerPackActivity.EXTRA_STICKER_PACK_ACTION));
            content.put("result", intent.getBooleanExtra(StickerPackActivity.EXTRA_STICKER_PACK_RESULT, false));
            content.put("error", intent.getStringExtra(StickerPackActivity.EXTRA_STICKER_PACK_ERROR));
            channel.invokeMethod("onError", content);
        }
    }


    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        channel = new MethodChannel(binding.getBinaryMessenger(), "io/whatsapp_stickers");
        this.binding = binding;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(StickerPackActivity.ACTION_STICKER_PACK_RESULT);
        intentFilter.addAction(StickerPackActivity.ACTION_STICKER_PACK_ERROR);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(binding.getApplicationContext());
        manager.registerReceiver(this, intentFilter);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        this.binding = binding;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        aBinding = binding;
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        aBinding = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        aBinding = binding;
    }

    @Override
    public void onDetachedFromActivity() {
        aBinding = null;
    }
}
