package com.hms.exception;

/** Creating one more of something would go past the hospital's plan limit. Answered as 403. */
public class PlanLimitReachedException extends RuntimeException {
    public PlanLimitReachedException(String message) {
        super(message);
    }
}
