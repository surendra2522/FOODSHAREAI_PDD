package com.foodshareai.ngo;

import com.foodshareai.base.BaseTest;
import com.foodshareai.pages.LoginPage;
import com.foodshareai.pages.NGODashboardPage;
import org.testng.Assert;
import org.testng.annotations.Test;

public class NGOWorkflowTest extends BaseTest {

    @Test(description = "NGO accepts a donation")
    public void testAcceptDonation() {
        LoginPage loginPage = new LoginPage();
        loginPage.login("ngo@example.com", "ngo123");

        NGODashboardPage dashboard = new NGODashboardPage();
        Assert.assertTrue(dashboard.areDonationsVisible(), "Donations should be visible for NGO");
        dashboard.acceptDonation();
    }
}
