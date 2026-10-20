package de.modernhelden.print;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Schreibt den Heldenbogen als PDF (jede Seite als hochaufgelöstes Bild, 200 dpi). */
public final class PdfWriter {

    private PdfWriter() {}

    public static void write(ModernSheet sheet, File file) throws IOException {
        final float dpi = 200f, scale = dpi / 72f;
        int pw = Math.round(ModernSheet.W * scale), ph = Math.round(ModernSheet.H * scale);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        int n = sheet.getNumberOfPages();
        // Objektnummern: 1 Katalog, 2 Seitenbaum, danach je Seite: Seite, Inhalt, Bild
        write(out, "%PDF-1.4\n%âãÏÓ\n");
        offsets.add(out.size());
        write(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        offsets.add(out.size());
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < n; i++) kids.append(3 + i * 3).append(" 0 R ");
        write(out, "2 0 obj\n<< /Type /Pages /Kids [" + kids + "] /Count " + n + " >>\nendobj\n");
        String mb = String.format(Locale.US, "[0 0 %.2f %.2f]", ModernSheet.W, ModernSheet.H);
        for (int i = 0; i < n; i++) {
            int pageObj = 3 + i * 3, contObj = pageObj + 1, imgObj = pageObj + 2;
            BufferedImage img = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);
            sheet.paintPage(g, i);
            g.dispose();
            byte[] jpg = jpeg(img);

            offsets.add(out.size());
            write(out, pageObj + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox " + mb
                    + " /Resources << /XObject << /Im0 " + imgObj + " 0 R >> >> /Contents " + contObj + " 0 R >>\nendobj\n");
            String content = String.format(Locale.US, "q %.2f 0 0 %.2f 0 0 cm /Im0 Do Q\n", ModernSheet.W, ModernSheet.H);
            offsets.add(out.size());
            write(out, contObj + " 0 obj\n<< /Length " + content.length() + " >>\nstream\n" + content + "endstream\nendobj\n");
            offsets.add(out.size());
            write(out, imgObj + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + pw + " /Height " + ph
                    + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + jpg.length + " >>\nstream\n");
            out.write(jpg);
            write(out, "\nendstream\nendobj\n");
        }
        int xref = out.size();
        StringBuilder sb = new StringBuilder();
        sb.append("xref\n0 ").append(offsets.size() + 1).append("\n0000000000 65535 f \n");
        for (int o : offsets) sb.append(String.format("%010d 00000 n \n", o));
        sb.append("trailer\n<< /Size ").append(offsets.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        write(out, sb.toString());
        try (OutputStream fo = new FileOutputStream(file)) { out.writeTo(fo); }
    }

    private static void write(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static byte[] jpeg(BufferedImage img) throws IOException {
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(0.92f);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos)) {
            w.setOutput(ios);
            w.write(null, new IIOImage(img, null, null), p);
        } finally {
            w.dispose();
        }
        return bos.toByteArray();
    }
}
