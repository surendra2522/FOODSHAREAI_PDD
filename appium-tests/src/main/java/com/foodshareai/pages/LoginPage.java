package com.foodshareai.pages;

import com.foodshareai.base.BasePage;
import org.openqa.selenium.By;

public class LoginPage extends BasePage {

    private By emailField = By.xpath("//android.widget.EditText[@hint='Email']");
    private By passwordField = By.xpath("//android.widget.EditText[@hint='Password']");
    private By loginButton = By.xpath("//android.widget.Button[@text='Login']");
    private By splashLogo = By.id("com.aistudio.foodshare.kbyqwe:id/splash_logo");

    public boolean isSplashDisplayed() {
        return isDisplayed(splashLogo);
    }

    public void login(String email, String password) {
        sendKeys(emailField, email);
        sendKeys(passwordField, password);
        click(loginButton);
    }

    public String getErrorMessage() {
        return getText(By.id("com.aistudio.foodshare.kbyqwe:id/error_text"));
    }
}
