# Attendance Report Email Test Checklist

Manual checklist for the attendance export and email attachment flow.

## Setup

- Open one subject with attendance data.
- Add at least two recipient emails for that subject.
- Keep another subject available to verify subject isolation.
- Use a test SMTP configuration only.

## Cases

1. No report exported, then send email.
   Expected: the app shows "Bạn chưa xuất báo cáo nào. Vui lòng xuất ít nhất một báo cáo trước khi gửi email." and no email is sent.

2. Export only CSV detail, then send.
   Expected: CSV detail is selected automatically, confirmation shows "CSV chi tiết", and the attached file is `.csv`.

3. Export only Excel, then send.
   Expected: Excel is selected automatically, confirmation shows "Excel", and the attached file is `.xlsx`.

4. Export all 4 report types.
   Expected: "Chọn báo cáo" shows CSV chi tiết, CSV tổng hợp, CSV dạng bảng, and Excel. Each selected report sends the matching file.

5. Export the same report type again.
   Expected: only the newest report for that type remains selectable, and the old cache file is no longer used.

6. Switch from CSV to Excel before sending.
   Expected: confirmation and email body show Excel, and the attachment is the selected `.xlsx` file.

7. Delete the selected cache file before sending.
   Expected: the app asks the user to export again and does not send email.

8. Send with no selected recipient.
   Expected: the app shows the recipient warning and does not check/send an attachment.

9. Send to multiple selected recipients.
   Expected: confirmation shows the correct recipient count, email is sent using BCC, and all recipients receive the same selected attachment.

10. Switch to another subject.
    Expected: reports from the previous subject are not selectable and cannot be sent.

11. Rotate the screen after exporting and selecting a report.
    Expected: the exported report list and selected report are restored if the cache file still exists.

12. Cancel `ACTION_CREATE_DOCUMENT`.
    Expected: no new report is registered, the previous selected report is unchanged, and no empty cache file is created.

13. Fail while saving the user-facing file.
    Expected: the app shows a save error and does not register a report for email.

14. SMTP send failure.
    Expected: the app shows the send error, keeps the selected report/cache file, and re-enables the send button.

15. SMTP send success.
    Expected: email body shows the selected report type, the attachment name/extension are correct, and managed cache files remain available for later sends.
