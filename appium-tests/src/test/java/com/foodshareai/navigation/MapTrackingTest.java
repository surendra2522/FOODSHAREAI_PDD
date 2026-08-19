package com.foodshareai.navigation;

import com.foodshareai.base.BaseTest;
import com.foodshareai.pages.MapTrackingPage;
import com.foodshareai.pages.NGODashboardPage;

import org.testng.Assert;
import org.testng.annotations.Test;

public class MapTrackingTest extends BaseTest {

    @Test(priority = 1, description = "Verify OpenStreetMap route visualization for active pickup")
    public void testMapTrackingLoad() {
        NGODashboardPage ngoPage = new NGODashboardPage();
        ngoPage.openActivePickups();
        ngoPage.clickTrackOnMap();

        MapTrackingPage mapPage = new MapTrackingPage();
        Assert.assertTrue(mapPage.isMapDisplayed(), "OpenStreetMap canvas should be displayed");
    }

    @Test(priority = 2, description = "Verify handoff OTP verification on driver arrival")
    public void testHandoffOtpVerification() {
        MapTrackingPage mapPage = new MapTrackingPage();
        mapPage.enterHandoffOtp("4829");
        mapPage.clickConfirmPickup();
    }
}
