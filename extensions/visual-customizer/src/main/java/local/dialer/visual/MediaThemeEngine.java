package local.dialer.visual;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.media.AudioManager;
import android.net.Uri;
import android.view.ViewGroup;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.VideoView;
import android.content.Context;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Media selection and bounded previews; never intercepts Android ringtone or InCallService audio. */
public final class MediaThemeEngine {
    public static final int PICK_COVER = 3401;
    public static final int PICK_VIDEO = 3402;
    public static final int PICK_TONE = 3403;
    private static final ExecutorService COVER_DECODER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "DialerVisual-CoverDecoder");
        thread.setDaemon(true);
        return thread;
    });
    private MediaThemeEngine() { }

    /** Read in the background so image decoding cannot delay answer/reject UI. */
    private static ImageView incomingCover(Context context) {
        String value = PreferenceRepository.get(context, PreferenceRepository.COVER_URI, "");
        if (value.isEmpty()) return null;
        Uri uri = Uri.parse(value);
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setContentDescription("Portada personalizada");
        WeakReference<ImageView> imageRef = new WeakReference<>(image);
        ContentResolver resolver = context.getApplicationContext().getContentResolver();
        COVER_DECODER.execute(() -> {
            Bitmap bitmap = null;
            try (InputStream first = resolver.openInputStream(uri)) {
                if (first == null) return;
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(first, null, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return;
                int sample = 1;
                while (bounds.outWidth / sample > 1080 || bounds.outHeight / sample > 1920) {
                    sample *= 2;
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                try (InputStream second = resolver.openInputStream(uri)) {
                    if (second != null) bitmap = BitmapFactory.decodeStream(second, null, options);
                }
            } catch (Exception ignored) { return; }
            if (bitmap == null) return;
            ImageView target = imageRef.get();
            if (target == null || target.getParent() == null) {
                bitmap.recycle();
                return;
            }
            final Bitmap ready = bitmap;
            target.post(() -> {
                ImageView attached = imageRef.get();
                if (attached == null || attached.getParent() == null) {
                    ready.recycle();
                    return;
                }
                attached.setImageBitmap(ready);
            });
        });
        return image;
    }
    public static void request(Activity activity, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (requestCode == PICK_COVER) intent.setType("image/*");
        else if (requestCode == PICK_VIDEO) intent.setType("video/*");
        else if (requestCode == PICK_TONE) intent.setType("audio/*");
        else throw new IllegalArgumentException("Unsupported media type");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        activity.startActivityForResult(intent, requestCode);
    }
    public static boolean accept(Activity activity, int requestCode, Intent result) {
        if (result == null || result.getData() == null) return false;
        String key;
        String prefix;
        if (requestCode == PICK_COVER) { key = PreferenceRepository.COVER_URI; prefix = "image/"; }
        else if (requestCode == PICK_VIDEO) { key = PreferenceRepository.VIDEO_URI; prefix = "video/"; }
        else if (requestCode == PICK_TONE) { key = PreferenceRepository.RINGTONE_URI; prefix = "audio/"; }
        else return false;
        Uri uri = result.getData();
        if (!"content".equals(uri.getScheme())) return false;
        ContentResolver resolver = activity.getContentResolver();
        String mime = resolver.getType(uri);
        if (mime == null || !mime.startsWith(prefix)) return false;
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException notPersistable) {
            return false; // Never save a URI which will be inaccessible on a future call.
        }
        String previous = PreferenceRepository.get(activity, key, "");
        PreferenceRepository.set(activity, key, uri.toString());
        if (!previous.isEmpty() && !previous.equals(uri.toString())) {
            try {
                resolver.releasePersistableUriPermission(Uri.parse(previous), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) { }
        }
        return true;
    }
    public static void clearMedia(Context context) {
        String[] keys = { PreferenceRepository.COVER_URI, PreferenceRepository.VIDEO_URI,
            PreferenceRepository.RINGTONE_URI };
        for (String key : keys) {
            String uri = PreferenceRepository.get(context, key, "");
            if (!uri.isEmpty()) {
                try {
                    context.getContentResolver().releasePersistableUriPermission(
                        Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) { }
            }
        }
        context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE)
            .edit().remove(PreferenceRepository.COVER_URI)
            .remove(PreferenceRepository.VIDEO_URI)
            .remove(PreferenceRepository.RINGTONE_URI)
            .putString(PreferenceRepository.CALL_BACKGROUND, "original").apply();
    }
    public static ImageView coverPreview(Context context) {
        String selected = PreferenceRepository.get(context, PreferenceRepository.COVER_URI, "");
        if (selected.isEmpty()) return null;
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setContentDescription("Vista previa de portada");
        // Bounds are decoded first; limiting sample size avoids large bitmap allocations.
        try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(selected))) {
            if (in == null) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, options);
            int sample = 1;
            while (options.outWidth / sample > 1080 || options.outHeight / sample > 1920) sample *= 2;
            BitmapFactory.Options actual = new BitmapFactory.Options();
            actual.inSampleSize = sample;
            try (InputStream again = context.getContentResolver().openInputStream(Uri.parse(selected))) {
                Bitmap bitmap = again == null ? null : BitmapFactory.decodeStream(again, null, actual);
                if (bitmap == null) return null;
                image.setImageBitmap(bitmap);
                return image;
            }
        } catch (Exception failure) { return null; }
    }
    public static VideoView mutedVideoPreview(Context context) {
        String uri = PreferenceRepository.get(context, PreferenceRepository.VIDEO_URI, "");
        if (uri.isEmpty()) return null;
        VideoView video = new VideoView(context);
        video.setContentDescription("Vista previa de video sin sonido");
        video.setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE);
        try { video.setVideoURI(Uri.parse(uri)); }
        catch (RuntimeException invalid) { return null; }
        video.setOnPreparedListener(player -> {
            if (video.getParent() == null || !video.isAttachedToWindow()) {
                video.stopPlayback();
                return;
            }
            player.setVolume(0.0f, 0.0f);
            player.setLooping(true);
            video.start();
        });
        video.setOnErrorListener((player, what, extra) -> {
            video.setVisibility(View.GONE);
            return true;
        });
        return video;
    }
    /**
     * Prepared for a verified IncomingCall fragment lifecycle hook; not invoked by
     * the dialpad patch. Adds background content behind the existing foreground,
     * never over answer/reject controls or video-call preview surfaces.
     */
    public static View attachIncomingBackground(FrameLayout root) {
        if (root == null) return null;
        Context context = root.getContext();
        if (PreferenceRepository.darkOnly(context) && !ThemeEngine.isNight(context)) return null;
        String mode = PreferenceRepository.get(context, PreferenceRepository.CALL_BACKGROUND, "original");
        View media = null;
        if ("cover".equals(mode)) media = incomingCover(context);
        else if ("video".equals(mode)) media = mutedVideoPreview(context);
        if (media == null) return null;
        media.setClickable(false);
        media.setFocusable(false);
        media.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(media, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return media;
    }
    public static void detachIncomingBackground(FrameLayout root, View installed) {
        if (root == null || installed == null) return;
        if (installed instanceof VideoView) ((VideoView) installed).stopPlayback();
        if (installed.getParent() == root) root.removeView(installed);
        if (installed instanceof ImageView) ((ImageView) installed).setImageDrawable(null);
    }
    public static MediaPlayer previewTone(Context context) {
        String path = PreferenceRepository.get(context, PreferenceRepository.RINGTONE_URI, "");
        if (path.isEmpty()) return null;
        try {
            MediaPlayer player = MediaPlayer.create(context, Uri.parse(path));
            if (player == null) return null;
            player.start();
            return player;
        } catch (RuntimeException invalid) { return null; }
    }
}
