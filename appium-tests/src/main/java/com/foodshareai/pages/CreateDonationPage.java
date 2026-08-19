package com.foodshareai.pages;

import com.foodshareai.base.BasePage;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

public class CreateDonationPage extends BasePage {

    private By uploadPhotoButton = AppiumBy.accessibilityId("upload_food_photo");
    private By foodTitleInput = AppiumBy.accessibilityId("food_title_input");
    private By categoryDropdown = AppiumBy.accessibilityId("category_select");
    private By quantityInput = AppiumBy.accessibilityId("quantity_input");
    private By expiryWindowSelect = AppiumBy.accessibilityId("expiry_window_select");
    private By aiFreshnessScoreText = AppiumBy.accessibilityId("ai_freshness_score");
    private By aiVerificationStatus = AppiumBy.accessibilityId("ai_verification_badge");
    private By ngoRoutingToggle = AppiumBy.accessibilityId("ngo_routing_toggle");
    private By submitDonationBtn = AppiumBy.accessibilityId("submit_donation_button");
    private By successDialog = AppiumBy.accessibilityId("donation_success_dialog");
    private By lowFreshnessWarning = AppiumBy.accessibilityId("low_freshness_warning");

    public CreateDonationPage clickUploadPhoto() {
        click(uploadPhotoButton);
        return this;
    }

    public CreateDonationPage enterFoodTitle(String title) {
        sendKeys(foodTitleInput, title);
        return this;
    }

    public CreateDonationPage selectCategory(String category) {
        click(categoryDropdown);
        click(AppiumBy.accessibilityId("cat_" + category.toLowerCase().replace(" ", "_")));
        return this;
    }

    public CreateDonationPage enterQuantity(String quantity) {
        sendKeys(quantityInput, quantity);
        return this;
    }

    public CreateDonationPage selectExpiryWindow(String hours) {
        click(expiryWindowSelect);
        click(AppiumBy.accessibilityId("expiry_" + hours + "h"));
        return this;
    }

    public String getAiFreshnessScore() {
        return getText(aiFreshnessScoreText);
    }

    public String getAiVerificationStatus() {
        return getText(aiVerificationStatus);
    }

    public CreateDonationPage toggleProgressiveNgoRouting() {
        click(ngoRoutingToggle);
        return this;
    }

    public void submitDonation() {
        click(submitDonationBtn);
    }

    public boolean isSuccessDialogDisplayed() {
        return isDisplayed(successDialog);
    }

    public boolean isLowFreshnessWarningDisplayed() {
        return isDisplayed(lowFreshnessWarning);
    }
}
