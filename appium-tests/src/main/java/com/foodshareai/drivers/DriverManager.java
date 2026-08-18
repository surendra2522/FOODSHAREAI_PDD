package com.foodshareai.drivers;

import com.foodshareai.utils.ConfigManager;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

public class DriverManager {
    private static ThreadLocal<AndroidDriver> driver = new ThreadLocal<>();

    public static AndroidDriver getDriver() {
        return driver.get();
    }

    public static void setupDriver() {
        try {
            UiAutomator2Options options = new UiAutomator2Options()
                    .setPlatformName(ConfigManager.getProperty("platformName"))
                    .setDeviceName(ConfigManager.getProperty("deviceName"))
                    .setAutomationName(ConfigManager.getProperty("automationName"))
                    .setAppPackage(ConfigManager.getProperty("appPackage"))
                    .setAppActivity(ConfigManager.getProperty("appActivity"))
                    .setUdid(ConfigManager.getProperty("udid"))
                    .setNoReset(true)
                    .setFullReset(false)
                    .setNewCommandTimeout(Duration.ofSeconds(60))
                    .setIgnoreHiddenApiPolicyError(true)
                    .setAutoGrantPermissions(true);

            // Optional: Install app if path is provided and file exists
            String appPath = ConfigManager.getProperty("appPath");
            if (appPath != null && !appPath.isEmpty()) {
                File appFile = new File(appPath);
                if (appFile.exists()) {
                    options.setApp(appFile.getAbsolutePath());
                }
            }

            String serverUrl = ConfigManager.getProperty("appiumServerUrl");
            // Ensure URL is handled correctly for Appium 2.x
            URL url = new URI(serverUrl).toURL();

            AndroidDriver androidDriver = new AndroidDriver(url, options);
            androidDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
            driver.set(androidDriver);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize AndroidDriver. " +
                    "Check if Appium server is running and device is connected. Error: " + e.getMessage(), e);
        }
    }

    public static void quitDriver() {
        if (driver.get() != null) {
            try {
                driver.get().quit();
            } catch (Exception e) {
                // Ignore session quit errors
            } finally {
                driver.remove();
            }
        }
    }
}
