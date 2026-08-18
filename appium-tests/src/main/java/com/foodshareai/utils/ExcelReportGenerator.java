package com.foodshareai.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileOutputStream;
import java.io.IOException;

public class ExcelReportGenerator {

    public static void main(String[] args) {
        generateInitialReport();
    }

    public static void generateInitialReport() {
        try (Workbook workbook = new XSSFWorkbook()) {
            createSummarySheet(workbook);
            createTestSheet(workbook, "Authentication", 30);
            createTestSheet(workbook, "Donor Module", 60);
            createTestSheet(workbook, "NGO Module", 60);
            createTestSheet(workbook, "Admin Module", 50);
            createTestSheet(workbook, "Navigation", 20);
            createTestSheet(workbook, "Notifications", 20);
            createTestSheet(workbook, "History", 20);
            createTestSheet(workbook, "Settings", 10);
            createTestSheet(workbook, "Performance", 15);
            createTestSheet(workbook, "Security", 15);

            try (FileOutputStream fileOut = new FileOutputStream("excel/FoodShareAI_Appium_Test_Report.xlsx")) {
                workbook.write(fileOut);
            }
            System.out.println("Excel report template generated successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createSummarySheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Executive Summary");
        String[] headers = {"Metric", "Value"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(getHeaderStyle(workbook));
        }
        
        String[][] data = {
            {"Total Test Cases", "300"},
            {"Executed", "0"},
            {"Passed", "0"},
            {"Failed", "0"},
            {"Blocked", "0"},
            {"Skipped", "0"},
            {"Pass Percentage", "0%"},
            {"Execution Date", "-"},
            {"Build Version", "1.0.0"},
            {"Tester Name", "Appium Framework"}
        };

        for (int i = 0; i < data.length; i++) {
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(data[i][0]);
            row.createCell(1).setCellValue(data[i][1]);
        }
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private static void createTestSheet(Workbook workbook, String sheetName, int count) {
        Sheet sheet = workbook.createSheet(sheetName);
        String[] headers = {"Test Case ID", "Module", "Feature", "Preconditions", "Steps", "Expected Result", "Priority", "Severity", "Automation Status", "Actual Result", "Pass/Fail", "Execution Time"};
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = getHeaderStyle(workbook);
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (int i = 1; i <= count; i++) {
            Row row = sheet.createRow(i);
            row.createCell(0).setCellValue(sheetName.substring(0, 3).toUpperCase() + "_" + String.format("%03d", i));
            row.createCell(1).setCellValue(sheetName);
            row.createCell(2).setCellValue("Feature " + i);
            row.createCell(8).setCellValue("Automated");
            row.createCell(10).setCellValue("Pending");
        }
        
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        sheet.createFreezePane(0, 1);
    }

    private static CellStyle getHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }
}
