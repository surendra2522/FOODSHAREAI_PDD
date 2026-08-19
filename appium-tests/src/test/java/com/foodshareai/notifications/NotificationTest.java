package com.foodshareai.notifications;

import com.foodshareai.base.BaseTest;
import com.foodshareai.pages.DonorDashboardPage;
import com.foodshareai.pages.NotificationPage;
import org.testng.Assert;
import org.testng.annotations.Test;

public class NotificationTest extends BaseTest {

    @Test(priority = 1, description = "Verify opening notification center and filtering urgent alerts")
    public void testNotificationCenter() {
        DonorDashboardPage dashboard = new DonorDashboardPage();
        dashboard.openNotifications();

        NotificationPage notificationPage = new NotificationPage();
        Assert.assertTrue(notificationPage.isNotificationScreenLoaded(), "Notification screen should load");
        notificationPage.filterUrgentOnly();
    }

    @Test(priority = 2, description = "Verify mark all notifications as read")
    public void testMarkAllAsRead() {
        NotificationPage notificationPage = new NotificationPage();
        notificationPage.clickMarkAllAsRead();
    }
}
