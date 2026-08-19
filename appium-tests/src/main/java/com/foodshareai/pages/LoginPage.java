package com.foodshareai.pages;

import com.foodshareai.base.BasePage;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

public class LoginPage extends BasePage {

    // Locators
    private By emailField = AppiumBy.accessibilityId("email_input");
    private By passwordField = AppiumBy.accessibilityId("password_input");
    private By loginButton = AppiumBy.accessibilityId("login_button");
    private By signUpTab = AppiumBy.accessibilityId("signup_tab");
    private By loginTab = AppiumBy.accessibilityId("login_tab");
    private By roleDonorButton = AppiumBy.accessibilityId("role_donor");
    private By roleNgoButton = AppiumBy.accessibilityId("role_ngo");
    private By roleAdminButton = AppiumBy.accessibilityId("role_admin");
    private By forgotPasswordLink = AppiumBy.accessibilityId("forgot_password_link");
    private By errorMessageText = AppiumBy.accessibilityId("error_message");
    private By splashLogo = AppiumBy.accessibilityId("foodshare_logo");
    private By googleSignInButton = AppiumBy.accessibilityId("google_signin_button");

    public LoginPage enterEmail(String email) {
        sendKeys(emailField, email);
        return this;
    }

    public LoginPage enterPassword(String password) {
        sendKeys(passwordField, password);
        return this;
    }

    public LoginPage selectRole(String role) {
        switch (role.toUpperCase()) {
            case "DONOR":
                click(roleDonorButton);
                break;
            case "NGO":
                click(roleNgoButton);
                break;
            case "ADMIN":
                click(roleAdminButton);
                break;
            default:
                click(roleDonorButton);
        }
        return this;
    }

    public void clickLogin() {
        click(loginButton);
    }

    public void login(String email, String password) {
        enterEmail(email);
        enterPassword(password);
        clickLogin();
    }

    public void loginWithRole(String email, String password, String role) {
        selectRole(role);
        enterEmail(email);
        enterPassword(password);
        clickLogin();
    }

    public void clickSignUpTab() {
        click(signUpTab);
    }

    public void clickForgotPassword() {
        click(forgotPasswordLink);
    }

    public String getErrorMessage() {
        return getText(errorMessageText);
    }

    public boolean isLogoDisplayed() {
        return isDisplayed(splashLogo);
    }

    public boolean isGoogleSignInAvailable() {
        return isDisplayed(googleSignInButton);
    }
}
