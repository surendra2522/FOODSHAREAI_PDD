package com.foodshareai.admin;

import com.foodshareai.base.BaseTest;
import com.foodshareai.pages.LoginPage;
import org.testng.annotations.Test;

public class AdminDashboardTest extends BaseTest {

    @Test(description = "Admin views user management")
    public void testUserManagement() {
        LoginPage loginPage = new LoginPage();
        loginPage.login("admin@foodshare.ai", "admin_pass");
        // Navigation to Admin Dashboard and User Management
    }
}
