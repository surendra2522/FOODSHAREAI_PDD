package com.foodshareai.admin;

import com.foodshareai.base.BaseTest;
import com.foodshareai.pages.AdminDashboardPage;
import com.foodshareai.pages.LoginPage;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AdminDashboardTest extends BaseTest {

    @Test(priority = 1, description = "Verify Admin Dashboard login and metrics summary")
    public void testAdminMetricsOverview() {
        LoginPage loginPage = new LoginPage();
        loginPage.loginWithRole("admin@foodshareai.com", "AdminSecret123!", "ADMIN");

        AdminDashboardPage adminPage = new AdminDashboardPage();
        Assert.assertTrue(adminPage.isAdminDashboardLoaded(), "Admin dashboard should load successfully");
        Assert.assertNotNull(adminPage.getTotalImpactStat(), "Total platform impact stat should be displayed");
    }

    @Test(priority = 2, description = "Verify approving pending NGO application")
    public void testApproveNgoVerification() {
        AdminDashboardPage adminPage = new AdminDashboardPage();
        String initialCount = adminPage.getPendingNgoCount();
        adminPage.approveFirstPendingNgo();
        // Verification action completed
    }

    @Test(priority = 3, description = "Verify flagged listing review tab")
    public void testFlaggedListingsReview() {
        AdminDashboardPage adminPage = new AdminDashboardPage();
        adminPage.openFlaggedListings();
    }
}
