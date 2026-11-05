package local.dialer.visual;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Conservative integration with the audited InCallActivity lifecycle.
 * No telephony service, call state, call controls, event listeners or audio
 * routing are changed. All installed views and listeners are removed on pause.
 */
public final class InCallThemeHooks {
    private static final Map<Activity, Session> SESSIONS = new WeakHashMap<>();

    private InCallThemeHooks() { }

    /** Called by the verified onPostResume hook after the native method succeeds. */
    public static void onPostResume(Activity activity) {
        if (activity == null || activity.isFinishing() ||
                !PreferenceRepository.inCallEnabled(activity)) return;
        try {
            Session session;
            synchronized (SESSIONS) {
                session = SESSIONS.get(activity);
                if (session == null) {
                    session = new Session(activity);
                    SESSIONS.put(activity, session);
                }
            }
            session.attach();
        } catch (RuntimeException ignored) {
            // Appearance must never prevent answering or ending a call.
        }
    }

    /** Called before the native onPause body. */
    public static void onPause(Activity activity) {
        if (activity == null) return;
        try {
            Session session;
            synchronized (SESSIONS) { session = SESSIONS.remove(activity); }
            if (session != null) session.close();
        } catch (RuntimeException ignored) {
            // Keep the native lifecycle intact even for an invalid media URI.
        }
    }

    private static final class Session implements ViewTreeObserver.OnGlobalLayoutListener {
        private final WeakReference<Activity> owner;
        private final View decor;
        private FrameLayout backgroundHost;
        private View installedBackground;
        private View styledMute;
        private boolean attached;
        private boolean running;

        Session(Activity activity) {
            owner = new WeakReference<>(activity);
            decor = activity.getWindow().getDecorView();
        }

        void attach() {
            if (attached) return;
            attached = true;
            decor.getViewTreeObserver().addOnGlobalLayoutListener(this);
            onGlobalLayout();
        }

        @Override public void onGlobalLayout() {
            if (running) return;
            Activity activity = owner.get();
            if (activity == null || activity.isFinishing() || !attached) return;
            running = true;
            try {
                updateIncoming(activity);
                updateActiveCall(activity);
            } catch (RuntimeException ignored) {
                removeBackground();
            } finally {
                running = false;
            }
        }

        private void updateIncoming(Activity activity) {
            Context context = activity;
            if (PreferenceRepository.darkOnly(context) && !ThemeEngine.isNight(context)) {
                removeBackground();
                return;
            }
            String mode = PreferenceRepository.get(context, PreferenceRepository.CALL_BACKGROUND, "original");
            if (!"cover".equals(mode) && !"video".equals(mode)) {
                removeBackground();
                return;
            }
            View container = find("background_fragment_container");
            View foreground = find("foreground_container");
            View answer = find("answer_method_container");
            boolean incoming = container instanceof FrameLayout &&
                container.isShown() && foreground != null && foreground.isShown() &&
                answer != null && answer.isShown() &&
                container.getParent() == foreground.getParent();
            if (!incoming || isVisible("incoming_preview_texture_view") ||
                    isVisible("incoming_preview_surface_view")) {
                removeBackground();
                return;
            }
            FrameLayout host = (FrameLayout) container;
            if (backgroundHost == host && installedBackground != null &&
                    installedBackground.getParent() == host) return;
            removeBackground();
            installedBackground = MediaThemeEngine.attachIncomingBackground(host);
            if (installedBackground != null) backgroundHost = host;
        }

        private void updateActiveCall(Activity activity) {
            View mute = find("btn_mute");
            if (mute == styledMute) return;
            // Restore the previous instance when fragments are swapped.
            if (styledMute != null) CallScreenStyler.restoreVerifiedControls(decor);
            styledMute = null;
            if (mute == null || !mute.isShown()) return;
            CallScreenStyler.styleVerifiedControls(decor);
            styledMute = mute;
        }

        private boolean isVisible(String name) {
            View view = find(name);
            return view != null && view.isShown();
        }

        private View find(String name) {
            Activity activity = owner.get();
            if (activity == null) return null;
            int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
            return id == 0 ? null : decor.findViewById(id);
        }

        private void removeBackground() {
            if (backgroundHost != null && installedBackground != null) {
                MediaThemeEngine.detachIncomingBackground(backgroundHost, installedBackground);
            }
            installedBackground = null;
            backgroundHost = null;
        }

        void close() {
            attached = false;
            ViewTreeObserver observer = decor.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
            removeBackground();
            CallScreenStyler.restoreVerifiedControls(decor);
            styledMute = null;
        }
    }
}
