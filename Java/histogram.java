// histogram.java
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import javax.imageio.ImageIO;

public class histogram {
    public static void main(String[] args) throws Exception {
        BufferedImage img = ImageIO.read(new File("picture.png"));
        int I = img.getHeight();  // rows
        int J = img.getWidth();   // cols

        System.out.println("I[i,j] = size(I) = [" + I + ", " + J + "]");
        System.out.println("All pixel size = " + (I * J));

        int[] h = new int[256];
        long T = 0L;
        boolean isGray = true;

        // --- คำนวณ histogram ของภาพต้นฉบับ + T, mean ---
        for (int u = 0; u < I; u++) {
            for (int v = 0; v < J; v++) {
                int rgb = img.getRGB(v, u);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8)  & 0xFF;
                int b =  rgb        & 0xFF;

                if (!(r == g && g == b)) isGray = false;

                int gray = (int)(0.299 * r + 0.587 * g + 0.114 * b);
                if (gray < 0) gray = 0;
                if (gray > 255) gray = 255;

                h[gray]++;
                T += gray;
            }
        }

        double mean = T / (double)(I * J);
        int N = I * J;

        System.out.println("T = " + T);
        System.out.println("mean(I) = " + mean);
        System.out.println("Check image type: " + (isGray ? "Grayscale" : "Color"));

        // === PDF / CDF ===
        double[] pdf = new double[256];
        double[] cdf = new double[256];
        for (int i = 0; i < 256; i++) {
            pdf[i] = (double) h[i] / N;
            cdf[i] = (i == 0) ? pdf[i] : (cdf[i - 1] + pdf[i]);
        }

        // === Histogram Equalization mapping ===
        int[] mapEq = new int[256];
        for (int i = 0; i < 256; i++) {
            int m = (int) Math.round(255.0 * cdf[i]);
            if (m < 0) m = 0;
            if (m > 255) m = 255;
            mapEq[i] = m;
        }

        // === สร้างภาพ equalized ===
        BufferedImage eq = new BufferedImage(J, I, BufferedImage.TYPE_BYTE_GRAY);
        for (int u = 0; u < I; u++) {
            for (int v = 0; v < J; v++) {
                int rgb = img.getRGB(v, u);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8)  & 0xFF;
                int b =  rgb        & 0xFF;
                int gray = (int)(0.299 * r + 0.587 * g + 0.114 * b);
                if (gray < 0) gray = 0;
                if (gray > 255) gray = 255;

                int g2 = mapEq[gray];
                int px = (g2 << 16) | (g2 << 8) | g2;
                eq.setRGB(v, u, 0xFF000000 | px);
            }
        }
        ImageIO.write(eq, "png", new File("equalized.png"));
        System.out.println("Saved: equalized.png");

        // === ฮิสโตแกรมของภาพ equalized (คำนวณแบบเร็วจาก mapping) ===
        int[] hEq = new int[256];
        for (int i = 0; i < 256; i++) {
            hEq[ mapEq[i] ] += h[i];
        }

        // ====== สร้างโฟลเดอร์สำหรับผลลัพธ์ (.txt) และกราฟ ======
        File resultDir = new File("histogram_result");
        if (!resultDir.exists()) resultDir.mkdirs();

        File graphDir = new File("graph/histogram");
        if (!graphDir.exists()) graphDir.mkdirs();

        // ====== เขียนไฟล์ .txt ======
        // h(i)
        try (PrintWriter pw = new PrintWriter(new File(resultDir, "histogram.txt"))) {
            pw.println("Histogram h(i):");
            for (int i = 0; i < 256; i++) pw.println(i + " : " + h[i]);
        }

        // pdf(i)
        try (PrintWriter pw = new PrintWriter(new File(resultDir, "pdf.txt"))) {
            pw.println("PDF(i) = h(i) / N:");
            for (int i = 0; i < 256; i++) pw.printf("%d : %.9f%n", i, pdf[i]);
        }

        // cdf(i)
        try (PrintWriter pw = new PrintWriter(new File(resultDir, "cdf.txt"))) {
            pw.println("CDF(i) = sum_{j<=i} PDF(j):");
            for (int i = 0; i < 256; i++) pw.printf("%d : %.9f%n", i, cdf[i]);
        }

        // h_eq(i)
        try (PrintWriter pw = new PrintWriter(new File(resultDir, "histogram_equalized.txt"))) {
            pw.println("Histogram h_eq(i) [equalized]:");
            for (int i = 0; i < 256; i++) pw.println(i + " : " + hEq[i]);
        }

        // =================== วาดกราฟ ===================
        int maxOrig = maxOf(h);
        int maxEqH  = maxOf(hEq);
        int yMax = Math.max(maxOrig, maxEqH);

        drawHistogram(h,   new File(graphDir, "histogram.png").getPath(),           "Histogram (Original)",  yMax);
        drawHistogram(hEq, new File(graphDir, "histogram_equalized.png").getPath(), "Histogram (Equalized)", yMax);
        drawCDF(cdf,       new File(graphDir, "cdf.png").getPath(),                 "CDF (0-255)");

        System.out.println("\nSaved .txt to folder: result/");
        System.out.println("Saved graphs to folder: graph/");
    }

    // ===== helper: วาดฮิสโตแกรมเป็นไฟล์ PNG =====
    private static void drawHistogram(int[] h, String filename, String title, int fixedYmax) throws Exception {
        int K = 256, barW = 2, margin = 50;
        int chartW = K * barW, chartH = 300;
        int width = chartW + margin * 2, height = chartH + margin * 2;

        int max = (fixedYmax > 0) ? fixedYmax : maxOf(h);
        if (max == 0) max = 1;

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // พื้นหลัง
        g2.setColor(Color.WHITE); g2.fillRect(0, 0, width, height);

        // แกน
        g2.setColor(Color.BLACK); g2.setStroke(new BasicStroke(2f));
        int x0 = margin, y0 = height - margin;
        g2.drawLine(x0, y0, x0, y0 - chartH);  // Y
        g2.drawLine(x0, y0, x0 + chartW, y0);  // X

        // แท่ง
        g2.setColor(new Color(60,120,200));
        for (int i = 0; i < K; i++) {
            double ratio = (double) h[i] / max;
            int hPix = (int) Math.round(ratio * chartH);
            int x = x0 + i * barW;
            int y = y0 - hPix;
            g2.fillRect(x, y, barW, hPix);
        }

        // สเกล X
        g2.setColor(Color.BLACK);
        for (int i = 0; i <= 255; i += 50) {
            int x = x0 + i * barW;
            g2.drawLine(x, y0, x, y0 + 5);
            g2.drawString(String.valueOf(i), x - 10, y0 + 20);
        }

        // สเกล Y
        int steps = 5;
        for (int s = 0; s <= steps; s++) {
            int val = max * s / steps;
            int y = y0 - (int)((double)val / max * chartH);
            g2.drawLine(x0 - 5, y, x0, y);
            g2.drawString(String.valueOf(val), x0 - 45, y + 5);
        }

        g2.drawString(title, x0 + chartW/2 - 60, height - 10);
        g2.dispose();
        ImageIO.write(out, "png", new File(filename));
    }

    // ===== helper: วาด CDF (เส้น) เป็นไฟล์ PNG =====
    private static void drawCDF(double[] cdf, String filename, String title) throws Exception {
        int K = 256, barW = 2, margin = 50;
        int chartW = K * barW, chartH = 300;
        int width = chartW + margin * 2, height = chartH + margin * 2;

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // พื้นหลัง
        g2.setColor(Color.WHITE); g2.fillRect(0, 0, width, height);

        // แกน
        g2.setColor(Color.BLACK); g2.setStroke(new BasicStroke(2f));
        int x0 = margin, y0 = height - margin;
        g2.drawLine(x0, y0, x0, y0 - chartH);      // Y
        g2.drawLine(x0, y0, x0 + chartW, y0);      // X

        // เส้น CDF (0..1 สเกลขึ้นไป chartH)
        g2.setColor(new Color(30,160,90));
        g2.setStroke(new BasicStroke(2.2f));
        int prevX = x0, prevY = y0 - (int) Math.round(cdf[0] * chartH);
        for (int i = 1; i < K; i++) {
            int x = x0 + i * barW;
            int y = y0 - (int) Math.round(cdf[i] * chartH);
            g2.drawLine(prevX, prevY, x, y);
            prevX = x; prevY = y;
        }

        // สเกล X
        g2.setColor(Color.BLACK);
        for (int i = 0; i <= 255; i += 50) {
            int x = x0 + i * barW;
            g2.drawLine(x, y0, x, y0 + 5);
            g2.drawString(String.valueOf(i), x - 10, y0 + 20);
        }

        // สเกล Y (0..1 แบ่ง 5 ขั้น)
        for (int s = 0; s <= 5; s++) {
            double t = s / 5.0;           // 0, 0.2, ..., 1
            int y = y0 - (int) Math.round(t * chartH);
            g2.drawLine(x0 - 5, y, x0, y);
            g2.drawString(String.format("%.1f", t), x0 - 35, y + 5);
        }

        g2.drawString(title, x0 + chartW/2 - 40, height - 10);
        g2.dispose();
        ImageIO.write(out, "png", new File(filename));
    }

    private static int maxOf(int[] a) {
        int m = 0;
        for (int x : a) if (x > m) m = x;
        return m;
    }
}
