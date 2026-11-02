package com.cycling.delivery;

/**
 * Versand fehlgeschlagen. permanent=true heisst: ein neuer Versuch aendert nichts
 * (z. B. Token ungueltig, keine Rechte) -> sofort FAILED.
 */
public class DeliveryException extends Exception {

    private final boolean permanent;

    private DeliveryException(String message, boolean permanent, Throwable cause) {
        super(message, cause);
        this.permanent = permanent;
    }

    public static DeliveryException permanent(String message, Throwable cause) {
        return new DeliveryException(message, true, cause);
    }

    public static DeliveryException temporary(String message, Throwable cause) {
        return new DeliveryException(message, false, cause);
    }

    public boolean isPermanent() {
        return permanent;
    }
}
