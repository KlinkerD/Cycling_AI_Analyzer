package com.cycling.service;

/** Es liegt noch keine Tagesempfehlung vor und der recommendation-service hat nicht rechtzeitig geantwortet. */
public class RecommendationUnavailableException extends RuntimeException {

    public RecommendationUnavailableException(String message) {
        super(message);
    }
}
