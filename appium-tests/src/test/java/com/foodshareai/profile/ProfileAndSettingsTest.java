package com.foodshareai.profile;

import com.foodshareai.base.BaseTest;
import com.foodshareai.pages.DonorDashboardPage;
import com.foodshareai.pages.ProfilePage;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ProfileAndSettingsTest extends BaseTest {

    @Test(priority = 1, description = "Verify User Profile rendering and badge collection")
    public void testProfileView() {
        DonorDashboardPage dashboard = new DonorDashboardPage();
        dashboard.openProfile();

        ProfilePage profilePage = new ProfilePage();
        Assert.assertTrue(profilePage.areBadgesDisplayed(), "User gamification badges should be visible");
    }

    @Test(priority = 2, description = "Verify toggling Dark Mode theme")
    public void testToggleDarkMode() {
        ProfilePage profilePage = new ProfilePage();
        profilePage.toggleDarkMode();
    }

    @Test(priority = 3, description = "Verify user logout functionality")
    public void testLogout() {
        ProfilePage profilePage = new ProfilePage();
        profilePage.clickLogout();
    }
}
