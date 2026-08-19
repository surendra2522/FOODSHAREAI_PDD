package com.foodshareai.pages;

import com.foodshareai.base.BasePage;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

public class ProfilePage extends BasePage {

    private By userNameText = AppiumBy.accessibilityId("user_profile_name");
    private By userEmailText = AppiumBy.accessibilityId("user_profile_email");
    private By editProfileButton = AppiumBy.accessibilityId("edit_profile_btn");
    private By organizationField = AppiumBy.accessibilityId("org_name_input");
    private By saveProfileBtn = AppiumBy.accessibilityId("save_profile_btn");
    private By darkModeToggle = AppiumBy.accessibilityId("dark_mode_toggle");
    private By logoutButton = AppiumBy.accessibilityId("logout_button");
    private By userBadgeContainer = AppiumBy.accessibilityId("user_badges_container");

    public String getUserName() {
        return getText(userNameText);
    }

    public String getUserEmail() {
        return getText(userEmailText);
    }

    public void clickEditProfile() {
        click(editProfileButton);
    }

    public void updateOrganization(String orgName) {
        sendKeys(organizationField, orgName);
        click(saveProfileBtn);
    }

    public void toggleDarkMode() {
        click(darkModeToggle);
    }

    public void clickLogout() {
        click(logoutButton);
    }

    public boolean areBadgesDisplayed() {
        return isDisplayed(userBadgeContainer);
    }
}
