package com.smartbillingpro.print;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * {@link DocCanvas} that writes PDF pages with Apache PDFBox. A system TrueType
 * font is embedded when available (so names in any script that font covers
 * print correctly); otherwise the built-in Helvetica is used.
 */
public final class PdfCanvas implements DocCanvas, AutoCloseable {

    private static final String[][] FONT_CANDIDATES = {
        {"C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/segoeuib.ttf"},
        {"C:/Windows/Fonts/arial.ttf", "C:/Windows/Fonts/arialbd.ttf"},
        {"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"},
        {"/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf", "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"},
        {"/System/Library/Fonts/Supplemental/Arial.ttf", "/System/Library/Fonts/Supplemental/Arial Bold.ttf"},
    };

    private final PDDocument doc = new PDDocument();
    private final PDFont regular;
    private final PDFont bold;
    private final boolean embedded;
    private PDPageContentStream stream;
    private float pageHeight;

    public PdfCanvas() {
        PDFont r = null;
        PDFont b = null;
        for (String[] pair : FONT_CANDIDATES) {
            if (new File(pair[0]).isFile() && new File(pair[1]).isFile()) {
                try {
                    r = PDType0Font.load(doc, new File(pair[0]));
                    b = PDType0Font.load(doc, new File(pair[1]));
                    break;
                } catch (IOException | RuntimeException e) {
                    r = null;
                    b = null;
                }
            }
        }
        embedded = r != null;
        regular = r != null ? r : new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        bold = b != null ? b : new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    }

    /** Start a new page of the given size in points. */
    public void newPage(float width, float height) {
        try {
            closeStream();
            PDPage page = new PDPage(new PDRectangle(width, height));
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
            pageHeight = height;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void newA4(boolean landscape) {
        PDRectangle a4 = PDRectangle.A4;
        if (landscape) {
            newPage(a4.getHeight(), a4.getWidth());
        } else {
            newPage(a4.getWidth(), a4.getHeight());
        }
    }

    private PDFont pdf(DocFont f) {
        return f.bold() ? bold : regular;
    }

    /** Replace characters the font cannot draw so text never aborts the PDF. */
    private String safe(String text, PDFont font) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            if (cp == '\t' || cp == '\n' || cp == '\r') {
                ch = " ";
            }
            try {
                font.encode(ch);
                sb.append(ch);
            } catch (IOException | IllegalArgumentException e) {
                sb.append('?');
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    @Override
    public float textWidth(String text, DocFont font) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            PDFont f = pdf(font);
            return f.getStringWidth(safe(text, f)) / 1000f * font.size();
        } catch (IOException e) {
            return text.length() * font.size() * 0.5f;
        }
    }

    @Override
    public float ascent(DocFont font) {
        return font.size() * 0.78f;
    }

    @Override
    public float lineHeight(DocFont font) {
        return font.size() * 1.32f;
    }

    @Override
    public void text(String text, float x, float baselineY, DocFont font, Color color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try {
            PDFont f = pdf(font);
            stream.beginText();
            stream.setFont(f, font.size());
            stream.setNonStrokingColor(color);
            stream.newLineAtOffset(x, pageHeight - baselineY);
            stream.showText(safe(text, f));
            stream.endText();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float width, Color color) {
        try {
            stream.setStrokingColor(color);
            stream.setLineWidth(width);
            stream.moveTo(x1, pageHeight - y1);
            stream.lineTo(x2, pageHeight - y2);
            stream.stroke();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void fillRect(float x, float y, float w, float h, Color color) {
        try {
            stream.setNonStrokingColor(color);
            stream.addRect(x, pageHeight - y - h, w, h);
            stream.fill();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean usesEmbeddedFont() {
        return embedded;
    }

    private void closeStream() throws IOException {
        if (stream != null) {
            stream.close();
            stream = null;
        }
    }

    public void save(Path file) throws IOException {
        closeStream();
        java.nio.file.Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        doc.save(tmp.toFile());
        java.nio.file.Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void close() throws IOException {
        closeStream();
        doc.close();
    }
}
