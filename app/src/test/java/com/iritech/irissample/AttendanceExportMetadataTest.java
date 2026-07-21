package com.iritech.irissample;

import com.iritech.irissample.export.AttendanceExportService;
import com.iritech.irissample.model.AttendanceRecord;
import com.iritech.irissample.model.export.AttendanceCell;
import com.iritech.irissample.model.export.AttendanceExportData;
import com.iritech.irissample.model.export.AttendanceMatrixRow;
import com.iritech.irissample.model.export.SessionInfo;
import com.iritech.irissample.model.export.StudentAttendanceSummary;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertTrue;

public class AttendanceExportMetadataTest {

    @Test
    public void newCsvFormatsIncludeSubjectMetadata() {
        AttendanceExportService service = new AttendanceExportService(null);
        AttendanceExportData data = sampleExportData();

        String summaryCsv = service.buildSummaryCsv(data, "21/07/2026");
        String matrixCsv = service.buildMatrixCsv(data, "21/07/2026");

        assertTrue(summaryCsv.startsWith("\uFEFFThông tin môn học\n"));
        assertTrue(summaryCsv.contains("Mã môn,IT4507\n"));
        assertTrue(summaryCsv.contains("Tên môn,An Toan Bao Mat\n"));
        assertTrue(summaryCsv.contains("Giảng viên,Hoang Duc Trung\n"));
        assertTrue(summaryCsv.contains("Ngày xuất,21/07/2026\n"));
        assertTrue(summaryCsv.contains("\nSTT,Mã sinh viên,Họ tên sinh viên"));

        assertTrue(matrixCsv.startsWith("\uFEFFThông tin môn học\n"));
        assertTrue(matrixCsv.contains("Mã môn,IT4507\n"));
        assertTrue(matrixCsv.contains("Tên môn,An Toan Bao Mat\n"));
        assertTrue(matrixCsv.contains("Giảng viên,Hoang Duc Trung\n"));
        assertTrue(matrixCsv.contains("Ngày xuất,21/07/2026\n"));
        assertTrue(matrixCsv.contains("\nSTT,Mã sinh viên,Họ tên sinh viên,21/07/2026\n"));
    }

    @Test
    public void excelSheetsIncludeSubjectMetadata() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        new AttendanceExcelExporter().export(
                outputStream,
                sampleExportData(),
                "21/07/2026"
        );

        String summarySheet = readZipEntry(
                outputStream.toByteArray(),
                "xl/worksheets/sheet1.xml"
        );
        String detailSheet = readZipEntry(
                outputStream.toByteArray(),
                "xl/worksheets/sheet2.xml"
        );

        assertTrue(summarySheet.contains("topLeftCell=\"A8\""));
        assertTrue(summarySheet.contains("r=\"A7\""));
        assertTrue(summarySheet.contains("<t>Thông tin môn học</t>"));
        assertTrue(summarySheet.contains("<t>IT4507</t>"));
        assertTrue(summarySheet.contains("<t>An Toan Bao Mat</t>"));
        assertTrue(summarySheet.contains("<t>Hoang Duc Trung</t>"));
        assertTrue(summarySheet.contains("<t>Ngày xuất</t>"));
        assertTrue(summarySheet.contains("<t>21/07/2026</t>"));

        assertTrue(detailSheet.contains("<t>Thông tin môn học</t>"));
        assertTrue(detailSheet.contains("<t>IT4507</t>"));
        assertTrue(detailSheet.contains("<t>Ngày xuất</t>"));
        assertTrue(detailSheet.contains("r=\"A7\""));
    }

    private static AttendanceExportData sampleExportData() {
        SessionInfo session = new SessionInfo("21/07/2026", "08:00:00");

        AttendanceMatrixRow matrixRow = new AttendanceMatrixRow(
                "SV001",
                "Nguyen Van A",
                "student@example.com",
                "0900000000"
        );
        matrixRow.putCell(new AttendanceCell(
                "21/07/2026",
                AttendanceRecord.STATUS_PRESENT,
                "07:55:00",
                "08:00:00"
        ));

        StudentAttendanceSummary summary = new StudentAttendanceSummary(
                "SV001",
                "Nguyen Van A",
                "student@example.com",
                "0900000000",
                "IT4507",
                "An Toan Bao Mat",
                1,
                1,
                0
        );

        return new AttendanceExportData(
                "IT4507",
                "An Toan Bao Mat",
                "Hoang Duc Trung",
                Collections.singletonList(session),
                Collections.singletonList(summary),
                Collections.singletonList(matrixRow)
        );
    }

    private static String readZipEntry(byte[] zipBytes, String entryName) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    ByteArrayOutputStream content = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        content.write(buffer, 0, read);
                    }
                    return new String(content.toByteArray(), StandardCharsets.UTF_8);
                }
            }
        }

        throw new AssertionError("Missing zip entry: " + entryName);
    }
}
