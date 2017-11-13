package com.comapi.chat.internal;

import java.util.concurrent.TimeUnit;

/**
 * Controls number of calls allowed in a set timescale.
 *
 * @author Marcin Swierczek
 * @since 1.0.1
 */
public class CallLimiter {

    private final long largeTimeFrame;
    private final long smallTimeFrame;
    private final int largeCallsLimit;
    private final int smallCallLimit;

    private final long shutdownPeriod;
    private long shutdownEndTime;
    private boolean isShutdown;

    private long largeLimitTimestamp;
    private int largeLimitCalls;

    private long smallLimitTimestamp;
    private int smallLimitCalls;

    /**
     * Recommended constructor.
     *
     * @param largeTimeFrame Time frame for large call limit.
     * @param largeCallsLimit Number of calls allowed in large timescale. Calling more times will result in a shutdown scenario.
     * @param largeTimeUnit Time units in which the large time frame is provided.
     * @param smallTimeFrame Time frame for small call limit.
     * @param smallCallLimit Number of calls allowed in small timescale.
     * @param smallTimeUnit Time units in which the small time frame is provided.
     * @param shutdownPeriodInMinutes For how long the calls will be blocked after reaching large limit.
     */
    public CallLimiter(int largeTimeFrame, int largeCallsLimit, TimeUnit largeTimeUnit, int smallTimeFrame, int smallCallLimit, TimeUnit smallTimeUnit, long shutdownPeriodInMinutes) {

        this.largeTimeFrame = TimeUnit.MILLISECONDS.convert(largeTimeFrame, largeTimeUnit);
        this.largeCallsLimit = largeCallsLimit;
        this.smallTimeFrame = TimeUnit.MILLISECONDS.convert(smallTimeFrame, smallTimeUnit);
        this.smallCallLimit = smallCallLimit;
        this.shutdownPeriod = TimeUnit.MILLISECONDS.convert(shutdownPeriodInMinutes, TimeUnit.MINUTES);
        this.isShutdown = false;

        long newTimestamp = System.currentTimeMillis();
        largeLimitTimestamp = newTimestamp;
        smallLimitTimestamp = newTimestamp;
    }

    /**
     * Check if next call is allowed and increase the counts.
     *
     * @return True if a next call is allowed.
     */
    public boolean checkAndIncrease() {

        long newTimestamp = System.currentTimeMillis();

        if (isShutdown) {
            if (newTimestamp > shutdownEndTime) {
                isShutdown = false;
                largeLimitCalls = 0;
                smallLimitCalls = 0;
            } else {
                return false;
            }
        }

        if (newTimestamp - smallLimitTimestamp > smallTimeFrame) {
            smallLimitCalls = 0;
            smallLimitTimestamp = newTimestamp;
        } else {
            if (smallLimitCalls >= smallCallLimit) {
                return false;
            } else {
                smallLimitCalls += 1;
            }
        }

        if (newTimestamp - largeLimitTimestamp > largeTimeFrame) {
            largeLimitCalls = 0;
            largeLimitTimestamp = newTimestamp;
        } else {
            if (largeLimitCalls >= largeCallsLimit) {
                isShutdown = true;
                shutdownEndTime = newTimestamp + shutdownPeriod;
                return false;
            } else {
                largeLimitCalls += 1;
            }
        }

        return true;
    }
}