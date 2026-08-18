package com.foodshareai.listeners;

import com.aventstack.extentreports.Status;
import com.foodshareai.utils.ReportUtils;
import com.foodshareai.utils.ScreenshotUtils;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

public class TestListener implements ITestListener {

    @Override
    public void onStart(ITestContext context) {
        ReportUtils.initReport();
    }

    @Override
    public void onTestStart(ITestResult result) {
        ReportUtils.startTest(result.getMethod().getMethodName());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        ReportUtils.getTest().log(Status.PASS, "Test Passed");
    }

    @Override
    public void onTestFailure(ITestResult result) {
        ReportUtils.getTest().log(Status.FAIL, "Test Failed: " + result.getThrowable().getMessage());
        String screenshotPath = ScreenshotUtils.takeScreenshot(result.getMethod().getMethodName());
        ReportUtils.getTest().addScreenCaptureFromPath(screenshotPath);
    }

    @Override
    public void onFinish(ITestContext context) {
        ReportUtils.flushReport();
    }
}
