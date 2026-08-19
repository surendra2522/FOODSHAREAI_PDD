package com.foodshareai.pages;

import com.foodshareai.base.BasePage;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

public class DonorDashboardPage extends BasePage {

    private By welcomeHeader = AppiumBy.accessibilityId("donor_welcome_header");
    private By createDonationBtn = AppiumBy.accessibilityId("create_donation_fab");
    private By activeListingsCount = AppiumBy.accessibilityId("active_listings_count");
    private By totalMealsDonated = AppiumBy.accessibilityId("total_meals_stat");
    private By co2SavedStat = AppiumBy.accessibilityId("co2_saved_stat");
    private By aiInsightsBanner = AppiumBy.accessibilityId("ai_insights_banner");
    private By notificationBell = AppiumBy.accessibilityId("notification_bell_icon");
    private By profileTab = AppiumBy.accessibilityId("nav_profile_tab");
    private By historyTab = AppiumBy.accessibilityId("nav_history_tab");

    public boolean isDashboardLoaded() {
        return isDisplayed(welcomeHeader) || isDisplayed(createDonationBtn);
    }

    public void clickCreateDonation() {
        click(createDonationBtn);
    }

    public String getActiveListingsCount() {
        return getText(activeListingsCount);
    }

    public String getTotalMealsDonated() {
        return getText(totalMealsDonated);
    }

    public String getCo2Saved() {
        return getText(co2SavedStat);
    }

    public void openNotifications() {
        click(notificationBell);
    }

    public void openProfile() {
        click(profileTab);
    }

    public void openHistory() {
        click(historyTab);
    }

    public void clickAiInsights() {
        click(aiInsightsBanner);
    }
}
