package dev.tenzou.terradash;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Membaca sesi media yang sedang aktif (Spotify, radio, pemutar bawaan, dll)
 * lewat izin Notification Listener, lalu mendorong statusnya ke WebView.
 */
public class MediaListener extends NotificationListenerService {

    private static MediaListener instance;

    private MediaSessionManager manager;
    private MediaController controller;

    private final MediaController.Callback sessionCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            push();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            push();
        }

        @Override
        public void onSessionDestroyed() {
            controller = null;
            push();
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override
                public void onActiveSessionsChanged(List<MediaController> controllers) {
                    bind(controllers);
                }
            };

    public static MediaListener get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        try {
            manager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            ComponentName me = new ComponentName(this, MediaListener.class);
            manager.addOnActiveSessionsChangedListener(sessionsListener, me);
            bind(manager.getActiveSessions(me));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        if (manager != null) {
            try {
                manager.removeOnActiveSessionsChangedListener(sessionsListener);
            } catch (Exception ignored) {
            }
        }
        if (controller != null) {
            try {
                controller.unregisterCallback(sessionCallback);
            } catch (Exception ignored) {
            }
            controller = null;
        }
    }

    @Override
    public void onDestroy() {
        if (manager != null) {
            try {
                manager.removeOnActiveSessionsChangedListener(sessionsListener);
            } catch (Exception ignored) {
            }
        }
        if (instance == this) instance = null;
        super.onDestroy();
    }

    private void bind(List<MediaController> controllers) {
        MediaController pick = null;
        if (controllers != null && !controllers.isEmpty()) {
            for (MediaController c : controllers) {
                PlaybackState st = c.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                    pick = c;
                    break;
                }
            }
            if (pick == null) pick = controllers.get(0);
        }

        if (controller != null) {
            try {
                controller.unregisterCallback(sessionCallback);
            } catch (Exception ignored) {
            }
        }
        controller = pick;
        if (controller != null) {
            try {
                controller.registerCallback(sessionCallback);
            } catch (Exception ignored) {
            }
        }
        push();
    }

    private void push() {
        MainActivity.pushMedia(stateJson());
    }

    public String stateJson() {
        MediaController c = controller;
        if (c == null) return "null";
        try {
            MediaMetadata md = c.getMetadata();
            PlaybackState ps = c.getPlaybackState();

            String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (artist == null && md != null) {
                artist = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            }
            long dur = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);

            boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
            long pos = ps == null ? 0 : ps.getPosition();
            if (ps != null && playing && ps.getPlaybackSpeed() > 0) {
                pos += (long) ((SystemClock.elapsedRealtime() - ps.getLastPositionUpdateTime())
                        * ps.getPlaybackSpeed());
            }

            JSONObject o = new JSONObject();
            o.put("title", title == null ? "" : title);
            o.put("artist", artist == null ? "" : artist);
            o.put("art", artBase64(md));
            o.put("playing", playing);
            o.put("pos", Math.max(0, pos));
            o.put("dur", Math.max(0, dur));
            return o.toString();
        } catch (Exception e) {
            return "null";
        }
    }

    private static String artBase64(MediaMetadata md) {
        if (md == null) return "";
        try {
            Bitmap bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (bmp == null) bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (bmp == null) return "";

            int max = 160;
            int w = bmp.getWidth();
            int h = bmp.getHeight();
            if (w > max || h > max) {
                float scale = Math.min((float) max / w, (float) max / h);
                bmp = Bitmap.createScaledBitmap(bmp,
                        Math.max(1, Math.round(w * scale)),
                        Math.max(1, Math.round(h * scale)), true);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 90, out);
            return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    public static void command(String cmd, String arg) {
        MediaListener m = instance;
        if (m == null || m.controller == null || cmd == null) return;
        try {
            MediaController.TransportControls t = m.controller.getTransportControls();
            if (t == null) return;
            switch (cmd) {
                case "play":
                    t.play();
                    break;
                case "pause":
                    t.pause();
                    break;
                case "next":
                    t.skipToNext();
                    break;
                case "prev":
                    t.skipToPrevious();
                    break;
                case "seek":
                    try {
                        t.seekTo(Long.parseLong(arg));
                    } catch (Exception ignored) {
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception ignored) {
        }
    }
}
