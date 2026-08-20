package lk.sdcms.util;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lk.sdcms.model.Appointment;
import lk.sdcms.model.Bill;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;

/**
 * Renders a printable receipt.
 *
 * <p>Returns bytes rather than writing a file, so the servlet can stream it
 * straight to the browser. Writing to disk would mean managing a temp directory
 * and cleaning it up, for no benefit.
 */
public final class PdfReceiptGenerator {

    private static final DeviceRgb BRAND = new DeviceRgb(27, 79, 114);

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter ISSUED_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private PdfReceiptGenerator() {
    }

    public static byte[] generate(Bill bill, Appointment appointment,
                                  String strategyDescription) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            try (PdfDocument pdf = new PdfDocument(new PdfWriter(out));
                 Document doc = new Document(pdf, PageSize.A5)) {

                PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

                doc.setMargins(28, 28, 28, 28);
                doc.setFont(regular).setFontSize(9);

                header(doc, bold, regular);
                appointmentDetails(doc, bold, regular, bill, appointment);
                charges(doc, bold, regular, bill, strategyDescription);
                footer(doc, regular);
            }
            return out.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("Unable to render receipt PDF", e);
        }
    }

    private static void header(Document doc, PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("SUNRISE DENTAL CLINIC")
                .setFont(bold).setFontSize(15).setFontColor(BRAND)
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(1));

        doc.add(new Paragraph("128 Galle Road, Colombo 03  |  +94 11 234 5678")
                .setFont(regular).setFontSize(8).setFontColor(ColorConstants.DARK_GRAY)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(0));

        doc.add(new Paragraph("PATIENT RECEIPT")
                .setFont(bold).setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(8).setMarginBottom(6)
                .setBorderTop(new SolidBorder(BRAND, 1f))
                .setBorderBottom(new SolidBorder(BRAND, 1f))
                .setPaddingTop(4).setPaddingBottom(4));
    }

    private static void appointmentDetails(Document doc, PdfFont bold, PdfFont regular,
                                           Bill bill, Appointment appointment) {

        Table table = new Table(UnitValue.createPercentArray(new float[]{35, 65}))
                .useAllAvailableWidth().setMarginBottom(8);

        addDetail(table, bold, regular, "Bill No",      bill.getBillId());
        addDetail(table, bold, regular, "Appointment",  appointment.getAppointmentNo());
        addDetail(table, bold, regular, "Issued",       bill.getIssuedDate().format(ISSUED_FMT));
        addDetail(table, bold, regular, "Patient",      appointment.getPatient().getFullName());
        addDetail(table, bold, regular, "Contact",      appointment.getPatient().getContactNo());
        addDetail(table, bold, regular, "Dentist",      appointment.getDentist().getFullName());
        addDetail(table, bold, regular, "Treatment",    appointment.getTreatment().getName());
        addDetail(table, bold, regular, "Visit date",
                appointment.getAppointmentDate().format(DATE_FMT)
                        + " at " + appointment.getAppointmentTime().format(TIME_FMT));

        doc.add(table);
    }

    private static void addDetail(Table table, PdfFont bold, PdfFont regular,
                                  String label, String value) {
        table.addCell(new Cell().add(new Paragraph(label).setFont(bold).setFontSize(8))
                .setBorder(Border.NO_BORDER).setPadding(1.5f));
        table.addCell(new Cell().add(new Paragraph(value).setFont(regular).setFontSize(8))
                .setBorder(Border.NO_BORDER).setPadding(1.5f));
    }

    private static void charges(Document doc, PdfFont bold, PdfFont regular,
                                Bill bill, String strategyDescription) {

        Table table = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                .useAllAvailableWidth();

        table.addHeaderCell(headerCell("Description", bold, TextAlignment.LEFT));
        table.addHeaderCell(headerCell("Amount (LKR)", bold, TextAlignment.RIGHT));

        addCharge(table, regular, "Consultation fee",  bill.getConsultationFee());
        addCharge(table, regular, "Treatment charge",  bill.getTreatmentCost());

        // Only shown when non-zero, so a standard-rate receipt is not cluttered
        // with a line reading "Discount 0.00".
        if (bill.getDiscountAmount() != null
                && bill.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            addCharge(table, regular, strategyDescription,
                    bill.getDiscountAmount().negate());
        }

        addCharge(table, regular, "VAT (8%)", bill.getTaxAmount());

        table.addCell(new Cell().add(new Paragraph("TOTAL PAYABLE").setFont(bold).setFontSize(9))
                .setBorderTop(new SolidBorder(BRAND, 1f))
                .setBorderBottom(Border.NO_BORDER)
                .setBorderLeft(Border.NO_BORDER).setBorderRight(Border.NO_BORDER)
                .setPadding(4));

        table.addCell(new Cell()
                .add(new Paragraph(MONEY.format(bill.getTotalAmount()))
                        .setFont(bold).setFontSize(9)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorderTop(new SolidBorder(BRAND, 1f))
                .setBorderBottom(Border.NO_BORDER)
                .setBorderLeft(Border.NO_BORDER).setBorderRight(Border.NO_BORDER)
                .setPadding(4));

        doc.add(table);

        doc.add(new Paragraph("Payment status: " + bill.getPaymentStatus())
                .setFont(regular).setFontSize(8).setMarginTop(6));
    }

    private static Cell headerCell(String text, PdfFont bold, TextAlignment align) {
        return new Cell()
                .add(new Paragraph(text).setFont(bold).setFontSize(8)
                        .setTextAlignment(align))
                .setBackgroundColor(new DeviceRgb(234, 242, 248))
                .setBorder(Border.NO_BORDER).setPadding(4);
    }

    private static void addCharge(Table table, PdfFont regular,
                                  String label, BigDecimal amount) {
        table.addCell(new Cell().add(new Paragraph(label).setFont(regular).setFontSize(8))
                .setBorder(Border.NO_BORDER).setPadding(3));
        table.addCell(new Cell()
                .add(new Paragraph(MONEY.format(amount)).setFont(regular).setFontSize(8)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER).setPadding(3));
    }

    private static void footer(Document doc, PdfFont regular) {
        doc.add(new Paragraph("Thank you for visiting Sunrise Dental Clinic.")
                .setFont(regular).setFontSize(8)
                .setFontColor(ColorConstants.DARK_GRAY)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(14));

        doc.add(new Paragraph("This receipt is computer generated.")
                .setFont(regular).setFontSize(7)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(1));
    }
}
