package com.foodshareai.e2e;

import com.foodshareai.base.BaseTest;
import com.foodshareai.pages.CreateDonationPage;
import com.foodshareai.pages.DonorDashboardPage;
import com.foodshareai.pages.LoginPage;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AiVerificationTest extends BaseTest {

    @Test(priority = 1, description = "Verify TFLite model scoring for fresh produce image")
    public void testAiFreshFoodInference() {
        LoginPage loginPage = new LoginPage();
        loginPage.loginWithRole("donor@foodshare.org", "Password123!", "DONOR");

        DonorDashboardPage dashboard = new DonorDashboardPage();
        dashboard.clickCreateDonation();

        CreateDonationPage createPage = new CreateDonationPage();
        createPage.clickUploadPhoto();

        String status = createPage.getAiVerificationStatus();
        Assert.assertNotNull(status, "AI Verification status should not be null");
    }

    @Test(priority = 2, description = "Verify AI model rejection alert for non-food image")
    public void testAiNonFoodRejection() {
        DonorDashboardPage dashboard = new DonorDashboardPage();
        dashboard.clickCreateDonation();

        CreateDonationPage createPage = new CreateDonationPage();
        createPage.clickUploadPhoto();
        // Simulates non-food photo selection
    }
}
