package local.dialer.visual;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Only aggregates supplied event categories in memory; stores no call data. */
public final class CallHistoryStats {
    private CallHistoryStats() { }

    public static Map<CallTypeResolver.Kind, Integer> count(List<CallTypeResolver.CallEvent> events) {
        EnumMap<CallTypeResolver.Kind, Integer> result =
            new EnumMap<>(CallTypeResolver.Kind.class);
        for (CallTypeResolver.Kind type : CallTypeResolver.Kind.values()) result.put(type, 0);
        if (events == null) return result;
        for (CallTypeResolver.CallEvent event : events) {
            if (event == null) continue;
            CallTypeResolver.Kind kind =
                CallTypeResolver.resolve(event.androidCallType, event.explicitlyMarkedSpam);
            result.put(kind, result.get(kind) + 1);
        }
        return result;
    }
}
