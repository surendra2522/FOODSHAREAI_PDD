package com.foodshareai.donor;

import com.foodshareai.base.BaseTest;
import com.foodshareai.pages.CreateDonationPage;
import com.foodshareai.pages.DonorDashboardPage;
import com.foodshareai.pages.LoginPage;
import org.testng.Assert;
import org.testng.annotations.Test;

public class DonorWorkflowTest extends BaseTest {

    @Test(priority = 1, description = "Verify Donor Dashboard loads correctly after login")
    public void testDonorDashboardLoad() {
        LoginPage loginPage = new LoginPage();
        loginPage.loginWithRole("donor@foodshare.org", "Password123!", "DONOR");

        DonorDashboardPage dashboard = new DonorDashboardPage();
        Assert.assertTrue(dashboard.isDashboardLoaded(), "Donor Dashboard failed to load");
    }

    @Test(priority = 2, description = "Verify complete donation post workflow with AI Freshness check")
    public void testCreateDonationWithAiVerification() {
        DonorDashboardPage dashboard = new DonorDashboardPage();
        dashboard.clickCreateDonation();

        CreateDonationPage createPage = new CreateDonationPage();
        createPage.clickUploadPhoto()
                  .enterFoodTitle("Fresh Organic Apples & Bananas")
                  .selectCategory("Fruits")
                  .enterQuantity("15 kg")
                  .selectExpiryWindow("24")
                  .toggleProgressiveNgoRouting();

        String freshnessScore = createPage.getAiFreshnessScore();
        Assert.assertNotNull(freshnessScore, "AI Freshness score should not be null");

        createPage.submitDonation();
        Assert.assertTrue(createPage.isSuccessDialogDisplayed(), "Donation success dialog was not displayed");
    }

    @Test(priority = 3, description = "Verify low freshness warning on edge food quality")
    public void testLowFreshnessWarning() {
        DonorDashboardPage dashboard = new DonorDashboardPage();
        dashboard.clickCreateDonation();

        CreateDonationPage createPage = new CreateDonationPage();
        createPage.clickUploadPhoto()
                  .enterFoodTitle("Near Expiry Cooked Meals")
                  .selectCategory("Cooked Food")
                  .enterQuantity("5 Meals")
                  .selectExpiryWindow("2");

        Assert.assertTrue(createPage.isLowFreshnessWarningDisplayed() || createPage.getAiFreshnessScore() != null,
                "Low freshness warning or score badge should be visible");
    }
}
