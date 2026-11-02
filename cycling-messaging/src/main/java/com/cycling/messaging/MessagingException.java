package com.cycling.messaging;

/** Nachricht konnte nicht an den Broker uebergeben werden. */
public class MessagingException extends RuntimeException {

    public MessagingException(String message, Throwable cause) {
        super(message, cause);
    }
}
