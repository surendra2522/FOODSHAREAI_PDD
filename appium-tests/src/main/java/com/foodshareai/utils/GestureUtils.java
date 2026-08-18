package com.foodshareai.utils;

import com.foodshareai.drivers.DriverManager;
import com.google.common.collect.ImmutableMap;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;

public class GestureUtils {

    public static void longClick(WebElement element) {
        ((JavascriptExecutor) DriverManager.getDriver()).executeScript("mobile: longClickGesture", ImmutableMap.of(
                "elementId", ((RemoteWebElement) element).getId(),
                "duration", 2000
        ));
    }

    public static void scrollDown() {
        boolean canScrollMore = (Boolean) ((JavascriptExecutor) DriverManager.getDriver()).executeScript("mobile: scrollGesture", ImmutableMap.of(
                "left", 100, "top", 100, "width", 200, "height", 200,
                "direction", "down",
                "percent", 3.0
        ));
    }

    public static void swipeLeft(WebElement element) {
        ((JavascriptExecutor) DriverManager.getDriver()).executeScript("mobile: swipeGesture", ImmutableMap.of(
                "elementId", ((RemoteWebElement) element).getId(),
                "direction", "left",
                "percent", 0.75
        ));
    }
}
