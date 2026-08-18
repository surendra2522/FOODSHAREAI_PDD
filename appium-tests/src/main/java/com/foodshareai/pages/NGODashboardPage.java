package com.foodshareai.pages;

import com.foodshareai.base.BasePage;
import org.openqa.selenium.By;

public class NGODashboardPage extends BasePage {
    private By availableDonations = By.id("com.aistudio.foodshare.kbyqwe:id/rv_donations");
    private By acceptBtn = By.id("com.aistudio.foodshare.kbyqwe:id/btn_accept");

    public boolean areDonationsVisible() {
        return isDisplayed(availableDonations);
    }

    public void acceptDonation() {
        click(acceptBtn);
    }
}
