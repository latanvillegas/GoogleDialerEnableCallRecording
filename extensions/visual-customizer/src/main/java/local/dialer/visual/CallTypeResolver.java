package local.dialer.visual;

import android.provider.CallLog;
import java.util.List;

/** A call type must come from the verified call-history model, not UI text. */
public final class CallTypeResolver {
    public enum Kind {
        MISSED("missed", "Perdida"), INCOMING("incoming", "Recibida"),
        OUTGOING("outgoing", "Realizada"), SPAM("spam", "Spam"),
        REJECTED("rejected", "Rechazada"), BLOCKED("blocked", "Bloqueada"),
        ORIGINAL("original", "Original");

        public final String key;
        public final String label;
        Kind(String key, String label) { this.key = key; this.label = label; }
    }

    public static final class CallEvent {
        public final int androidCallType;
        public final boolean explicitlyMarkedSpam;
        public final long timestamp;
        public CallEvent(int androidCallType, boolean explicitlyMarkedSpam, long timestamp) {
            this.androidCallType = androidCallType;
            this.explicitlyMarkedSpam = explicitlyMarkedSpam;
            this.timestamp = timestamp;
        }
    }
    private CallTypeResolver() { }

    /** Spam is an independent flag; an unknown number is never inferred spam. */
    public static Kind resolve(int androidCallType, boolean explicitlyMarkedSpam) {
        if (explicitlyMarkedSpam) return Kind.SPAM;
        switch (androidCallType) {
            case CallLog.Calls.MISSED_TYPE: return Kind.MISSED;
            case CallLog.Calls.INCOMING_TYPE: return Kind.INCOMING;
            case CallLog.Calls.OUTGOING_TYPE: return Kind.OUTGOING;
            case CallLog.Calls.REJECTED_TYPE: return Kind.REJECTED;
            case CallLog.Calls.BLOCKED_TYPE: return Kind.BLOCKED;
            default: return Kind.ORIGINAL;
        }
    }

    /** A grouped row follows its most recent actual call. */
    public static Kind mostRecent(List<CallEvent> events) {
        if (events == null || events.isEmpty()) return Kind.ORIGINAL;
        CallEvent latest = null;
        for (CallEvent event : events) {
            if (event != null && (latest == null || event.timestamp > latest.timestamp)) latest = event;
        }
        return latest == null ? Kind.ORIGINAL :
            resolve(latest.androidCallType, latest.explicitlyMarkedSpam);
    }
}
