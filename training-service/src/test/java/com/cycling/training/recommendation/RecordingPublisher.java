package com.cycling.training.recommendation;

import com.cycling.messaging.MessagePublisher;
import com.cycling.messaging.MessagingException;

import java.util.ArrayList;
import java.util.List;

/** Merkt sich alle Nachrichten; kann auf Wunsch fuer ein Topic fehlschlagen. */
class RecordingPublisher implements MessagePublisher {

    record Sent(String topic, Object message, boolean retained) {
    }

    final List<Sent> sent = new ArrayList<>();
    String failingTopic;

    @Override
    public synchronized void publish(String topic, Object message) {
        record(topic, message, false);
    }

    @Override
    public synchronized void publishRetained(String topic, Object message) {
        record(topic, message, true);
    }

    private void record(String topic, Object message, boolean retained) {
        if (topic.equals(failingTopic)) throw new MessagingException("nicht verbunden", null);
        sent.add(new Sent(topic, message, retained));
    }

    synchronized <T> List<T> on(String topic, Class<T> type) {
        return sent.stream().filter(s -> s.topic().equals(topic)).map(s -> type.cast(s.message())).toList();
    }
}
