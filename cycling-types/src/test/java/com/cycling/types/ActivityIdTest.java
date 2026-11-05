package com.cycling.types;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityIdTest {

    private final ObjectMapper mapper = new ObjectMapper();

    record Holder(ActivityId id, List<ActivityId> ids) {
    }

    @Test
    void isPlainStringInJson() throws Exception {
        Holder holder = new Holder(ActivityId.of("i1"), List.of(ActivityId.of("i2")));

        String json = mapper.writeValueAsString(holder);

        assertThat(json).isEqualTo("{\"id\":\"i1\",\"ids\":[\"i2\"]}");
        assertThat(mapper.readValue(json, Holder.class)).isEqualTo(holder);
    }

    @Test
    void nullStaysNull() throws Exception {
        assertThat(mapper.readValue("{\"id\":null,\"ids\":[]}", Holder.class).id()).isNull();
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> ActivityId.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ActivityId.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringIsValue() {
        assertThat(ActivityId.of("i123")).hasToString("i123");
    }
}
