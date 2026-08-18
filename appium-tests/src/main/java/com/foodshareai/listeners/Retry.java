package com.foodshareai.listeners;

import com.foodshareai.utils.ConfigManager;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

public class Retry implements IRetryAnalyzer {
    private int count = 0;
    private static final int MAX_RETRY_COUNT = ConfigManager.getIntProperty("retryCount");

    @Override
    public boolean retry(ITestResult result) {
        if (!result.isSuccess()) {
            if (count < MAX_RETRY_COUNT) {
                count++;
                return true;
            }
        }
        return false;
    }
}
