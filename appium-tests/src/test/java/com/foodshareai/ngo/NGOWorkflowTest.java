package com.foodshareai.ngo;

import com.foodshareai.base.BaseTest;
import com.foodshareai.pages.LoginPage;
import com.foodshareai.pages.NGODashboardPage;
import org.testng.Assert;
import org.testng.annotations.Test;

public class NGOWorkflowTest extends BaseTest {

    @Test(priority = 1, description = "Verify NGO Dashboard login and listing search")
    public void testNgoDashboardSearch() {
        LoginPage loginPage = new LoginPage();
        loginPage.loginWithRole("ngo@helphunger.org", "NgoPass123!", "NGO");

        NGODashboardPage ngoPage = new NGODashboardPage();
        Assert.assertTrue(ngoPage.isNgoDashboardLoaded(), "NGO Dashboard should be loaded");

        ngoPage.searchFood("Bread")
               .applyProximityFilter()
               .applyFreshnessFilter();
    }

    @Test(priority = 2, description = "Verify NGO food claim workflow")
    public void testClaimFoodListing() {
        NGODashboardPage ngoPage = new NGODashboardPage();
        ngoPage.selectFirstFoodListing();
        ngoPage.clickClaimFood();

        Assert.assertTrue(ngoPage.isClaimConfirmed(), "Claim confirmation popup should appear");
    }

    @Test(priority = 3, description = "Verify active pickup navigation from NGO screen")
    public void testNavigateToActivePickup() {
        NGODashboardPage ngoPage = new NGODashboardPage();
        ngoPage.openActivePickups();
        ngoPage.clickTrackOnMap();
    }
}
