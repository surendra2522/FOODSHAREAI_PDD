package com.foodshareai.pages;

import com.foodshareai.base.BasePage;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

public class AdminDashboardPage extends BasePage {

    private By adminHeader = AppiumBy.accessibilityId("admin_dashboard_header");
    private By pendingNgoVerificationsCount = AppiumBy.accessibilityId("pending_ngo_count");
    private By approveNgoButton = AppiumBy.accessibilityId("approve_ngo_btn_0");
    private By rejectNgoButton = AppiumBy.accessibilityId("reject_ngo_btn_0");
    private By totalImpactStat = AppiumBy.accessibilityId("platform_total_impact");
    private By flaggedListingsTab = AppiumBy.accessibilityId("tab_flagged_listings");
    private By userManagementTab = AppiumBy.accessibilityId("tab_user_mgmt");
    private By exportAnalyticsBtn = AppiumBy.accessibilityId("export_analytics_btn");

    public boolean isAdminDashboardLoaded() {
        return isDisplayed(adminHeader);
    }

    public String getPendingNgoCount() {
        return getText(pendingNgoVerificationsCount);
    }

    public void approveFirstPendingNgo() {
        click(approveNgoButton);
    }

    public void rejectFirstPendingNgo() {
        click(rejectNgoButton);
    }

    public String getTotalImpactStat() {
        return getText(totalImpactStat);
    }

    public void openFlaggedListings() {
        click(flaggedListingsTab);
    }

    public void openUserManagement() {
        click(userManagementTab);
    }

    public void clickExportAnalytics() {
        click(exportAnalyticsBtn);
    }
}
