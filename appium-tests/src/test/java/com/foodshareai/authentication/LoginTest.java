package com.foodshareai.authentication;

import com.foodshareai.base.BaseTest;
import com.foodshareai.pages.LoginPage;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LoginTest extends BaseTest {

    @Test(priority = 1, description = "Login with valid credentials")
    public void testValidLogin() {
        LoginPage loginPage = new LoginPage();
        loginPage.login("donor@example.com", "password123");
        // Add assertions for successful login
    }

    @Test(priority = 2, description = "Login with invalid credentials")
    public void testInvalidLogin() {
        LoginPage loginPage = new LoginPage();
        loginPage.login("invalid@example.com", "wrongpass");
        Assert.assertTrue(loginPage.getErrorMessage().contains("Invalid"), "Error message not displayed correctly");
    }
}
