package com.foodshareai.pages;

import com.foodshareai.base.BasePage;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

public class MapTrackingPage extends BasePage {

    private By mapCanvas = AppiumBy.accessibilityId("osm_map_component");
    private By driverMarker = AppiumBy.accessibilityId("driver_map_marker");
    private By pickupMarker = AppiumBy.accessibilityId("pickup_map_marker");
    private By etaText = AppiumBy.accessibilityId("tracking_eta_text");
    private By confirmHandoffOtpInput = AppiumBy.accessibilityId("otp_handoff_input");
    private By confirmPickupBtn = AppiumBy.accessibilityId("confirm_pickup_button");
    private By statusBadge = AppiumBy.accessibilityId("pickup_status_badge");

    public boolean isMapDisplayed() {
        return isDisplayed(mapCanvas);
    }

    public String getEta() {
        return getText(etaText);
    }

    public String getPickupStatus() {
        return getText(statusBadge);
    }

    public MapTrackingPage enterHandoffOtp(String otp) {
        sendKeys(confirmHandoffOtpInput, otp);
        return this;
    }

    public void clickConfirmPickup() {
        click(confirmPickupBtn);
    }
}
