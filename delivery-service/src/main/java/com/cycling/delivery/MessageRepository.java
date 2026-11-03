package com.cycling.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface MessageRepository extends JpaRepository<StoredMessage, String> {

    /** Loescht den Inhalt (Bilder) aelterer Nachrichten, die Zeile bleibt fuer die Historie. */
    @Transactional
    @Modifying
    @Query("update StoredMessage m set m.payload = null where m.receivedAt < :cutoff and m.payload is not null")
    int clearPayloadsReceivedBefore(@Param("cutoff") LocalDateTime cutoff);

    @Transactional
    long deleteByReceivedAtBefore(LocalDateTime cutoff);
}
