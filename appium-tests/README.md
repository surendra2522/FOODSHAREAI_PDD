# FoodShareAI Appium E2E Automation Framework

This project contains the End-to-End automation testing framework for the FoodShareAI Android application.

## Tech Stack
- **Java 17**
- **Appium 2.x**
- **Selenium WebDriver**
- **TestNG**
- **Maven**
- **Extent Reports**
- **Apache POI (Excel)**
- **Log4j2**

## Prerequisites
1. Install Node.js and Appium 2.x (`npm install -g appium`).
2. Install UiAutomator2 driver (`appium driver install uiautomator2`).
3. Ensure Android SDK is installed and `ANDROID_HOME` is set.
4. Maven installed.
5. Java 17 installed.

## Project Structure
- `src/main/java`: Core framework, base classes, utilities, and page objects.
- `src/test/java`: Test cases organized by module.
- `config.properties`: Global configuration for devices and app paths.
- `reports/`: Extent HTML reports generated after execution.
- `excel/`: Detailed Excel test reports.

## How to Run
1. Start Appium server: `appium`.
2. Open a terminal in the `appium-tests` folder.
3. Run all tests: `mvn clean test`.
4. To run specific suite: `mvn test -DsuiteXmlFile=testng.xml`.

## Reporting
- After execution, check `reports/ExtentReport.html` for visual results.
- Check `excel/FoodShareAI_Appium_Test_Report.xlsx` for detailed test case tracking.
