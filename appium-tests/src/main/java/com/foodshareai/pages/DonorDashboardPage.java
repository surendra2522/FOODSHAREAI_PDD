package com.foodshareai.pages;

import com.foodshareai.base.BasePage;
import org.openqa.selenium.By;

public class DonorDashboardPage extends BasePage {
    private By createDonationBtn = By.id("com.aistudio.foodshare.kbyqwe:id/btn_create_donation");
    private By historyTab = By.id("com.aistudio.foodshare.kbyqwe:id/nav_history");
    private By profileTab = By.id("com.aistudio.foodshare.kbyqwe:id/nav_profile");

    public void clickCreateDonation() {
        click(createDonationBtn);
    }

    public void goToHistory() {
        click(historyTab);
    }
}
