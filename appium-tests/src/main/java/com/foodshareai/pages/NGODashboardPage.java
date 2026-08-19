package com.foodshareai.pages;

import com.foodshareai.base.BasePage;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

public class NGODashboardPage extends BasePage {

    private By ngoHeader = AppiumBy.accessibilityId("ngo_dashboard_header");
    private By searchInput = AppiumBy.accessibilityId("search_food_input");
    private By distanceFilterBtn = AppiumBy.accessibilityId("distance_filter_5km");
    private By freshOnlyFilterBtn = AppiumBy.accessibilityId("fresh_only_filter");
    private By firstFoodCard = AppiumBy.accessibilityId("food_card_0");
    private By claimFoodBtn = AppiumBy.accessibilityId("claim_food_btn");
    private By activePickupsTab = AppiumBy.accessibilityId("tab_active_pickups");
    private By viewRouteMapBtn = AppiumBy.accessibilityId("view_route_map_btn");
    private By claimConfirmationModal = AppiumBy.accessibilityId("claim_confirmation_modal");

    public boolean isNgoDashboardLoaded() {
        return isDisplayed(ngoHeader) || isDisplayed(firstFoodCard);
    }

    public NGODashboardPage searchFood(String query) {
        sendKeys(searchInput, query);
        return this;
    }

    public NGODashboardPage applyProximityFilter() {
        click(distanceFilterBtn);
        return this;
    }

    public NGODashboardPage applyFreshnessFilter() {
        click(freshOnlyFilterBtn);
        return this;
    }

    public void selectFirstFoodListing() {
        click(firstFoodCard);
    }

    public void clickClaimFood() {
        click(claimFoodBtn);
    }

    public boolean isClaimConfirmed() {
        return isDisplayed(claimConfirmationModal);
    }

    public void openActivePickups() {
        click(activePickupsTab);
    }

    public void clickTrackOnMap() {
        click(viewRouteMapBtn);
    }
}
