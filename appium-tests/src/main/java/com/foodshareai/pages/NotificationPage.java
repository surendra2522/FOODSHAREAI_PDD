package com.foodshareai.pages;

import com.foodshareai.base.BasePage;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

public class NotificationPage extends BasePage {

    private By headerTitle = AppiumBy.accessibilityId("notification_screen_title");
    private By firstNotificationItem = AppiumBy.accessibilityId("notification_item_0");
    private By markAllAsReadBtn = AppiumBy.accessibilityId("mark_all_read_btn");
    private By filterAllTab = AppiumBy.accessibilityId("filter_all_notifications");
    private By filterUrgentTab = AppiumBy.accessibilityId("filter_urgent_notifications");

    public boolean isNotificationScreenLoaded() {
        return isDisplayed(headerTitle);
    }

    public void clickFirstNotification() {
        click(firstNotificationItem);
    }

    public void clickMarkAllAsRead() {
        click(markAllAsReadBtn);
    }

    public void filterUrgentOnly() {
        click(filterUrgentTab);
    }
}
