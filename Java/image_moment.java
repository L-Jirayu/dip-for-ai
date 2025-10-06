import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import javax.imageio.ImageIO;

public class image_moment {

    public static void main(String[] args) throws Exception {
        String path = (args.length > 0) ? args[0] : "threshold_result/threshold_single/threshold_1.png";
        BufferedImage img = ImageIO.read(new File(path));
        if (img == null) throw new IllegalArgumentException("Cannot read image: " + path);

        // ===== ใช้ภาพแบบ Threshold เสมอ: แปลงเป็น Binary 0/1 แล้วคำนวณทั้งหมดบน 0/1 =====
        double[][] f = toGrayMatrix(img); // 0..255
        toBinary01InPlace(f);             // บังคับ 0/1

        // -------- Raw moments (บน Binary) --------
        double M00 = rawMoment(f, 0, 0);      // จำนวนพิกเซลวัตถุ
        double S10 = rawMoment(f, 1, 0);      // sum(j * f)
        double S01 = rawMoment(f, 0, 1);      // sum(i * f)
        double xbar = (M00 != 0.0) ? S10 / M00 : 0.0;
        double ybar = (M00 != 0.0) ? S01 / M00 : 0.0;

        // ---- สำหรับการแสดงผล: M10, M01 ให้เท่ากับ centroid ตามที่คุณต้องการ ----
        double M10_disp = xbar;
        double M01_disp = ybar;

        // -------- Central moments (บน Binary) --------
        double mu11 = centralMoment(f, 1, 1);
        double mu20 = centralMoment(f, 2, 0);
        double mu02 = centralMoment(f, 0, 2);

        // -------- Hu moments (φ1..φ7) บน Binary --------
        double[] hu = huMoments(f);

        // ===== terminal output =====
        System.out.printf("Raw Moments%n");
        System.out.printf("M00 = %.2f%n", M00);
        System.out.printf("M10 = %.2f,  M01 = %.2f%n", M10_disp, M01_disp);
        System.out.printf("Centroid (x̄, ȳ) = (%.2f, %.2f)%n", xbar, ybar);

        System.out.println();
        System.out.printf("Central Moments%n");
        System.out.printf("mu00 = %.2f%n", mu00(f)); // เท่ากับ M00 (เพราะรวม f)
        System.out.printf("mu11 = %.2f%n", mu11);
        System.out.printf("mu20 = %.2f%n", mu20);
        System.out.printf("mu02 = %.2f%n", mu02);

        System.out.println();
        System.out.println("Hu Moments");
        for (int i = 0; i < 7; i++) {
            System.out.printf("phi%d = %.6e%n", i + 1, hu[i]);
        }

        // ===== output folders =====
        File imgDir = new File("image_moment/picture_output");
        if (!imgDir.exists()) imgDir.mkdirs();
        File txtDir = new File("image_moment/txt_output");
        if (!txtDir.exists()) txtDir.mkdirs();

        // ===== save .txt =====
        try (PrintWriter pw = new PrintWriter(new File(txtDir, "moment_report.txt"))) {
            pw.printf("Raw Moments%n");
            pw.printf("M00 = %.2f%n", M00);
            pw.printf("M10 = %.2f,  M01 = %.2f%n", M10_disp, M01_disp);
            pw.printf("Centroid (x̄, ȳ) = (%.2f, %.2f)%n%n", xbar, ybar);

            pw.printf("Central Moments%n");
            pw.printf("mu00 = %.2f%n", mu00(f));
            pw.printf("mu11 = %.2f%n", mu11);
            pw.printf("mu20 = %.2f%n", mu20);
            pw.printf("mu02 = %.2f%n%n", mu02);

            pw.println("Hu Moments");
            for (int i = 0; i < 7; i++) {
                pw.printf("phi%d = %.6e%n", i + 1, hu[i]);
            }
        }

        // ===== save images =====
        BufferedImage rawOut = overlayCentroidWithText(
            img, xbar, ybar,
            String.format("Raw: M00=%.0f", M00),
            String.format("M10=%.2f, M01=%.2f", M10_disp, M01_disp),
            String.format("Centroid (%.2f, %.2f)", xbar, ybar)
        );
        ImageIO.write(rawOut, "png", new File(imgDir, "raw_moment.png"));

        BufferedImage cenOut = overlayCentroidWithText(
            img, xbar, ybar,
            String.format("Central: mu11=%.2f", mu11),
            String.format("mu20=%.2f, mu02=%.2f", mu20, mu02)
        );
        ImageIO.write(cenOut, "png", new File(imgDir, "central_moment.png"));

        BufferedImage huOut = overlayHuWithAxis(img, xbar, ybar, mu20, mu02, mu11, hu);
        ImageIO.write(huOut, "png", new File(imgDir, "hu_moment.png"));

        System.out.println();
        System.out.println("Saved:");
        System.out.println("  image_moment/picture_output/raw_moment.png");
        System.out.println("  image_moment/picture_output/central_moment.png");
        System.out.println("  image_moment/picture_output/hu_moment.png");
        System.out.println("  image_moment/txt_output/moment_report.txt");
    }

    // ---------- draw centroid + multi-line label ----------
    private static BufferedImage overlayCentroidWithText(BufferedImage src, double cx, double cy, String... lines) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, null);

            int x = (int) Math.round(cx);
            int y = (int) Math.round(cy);
            g.setColor(Color.GREEN);
            int len = 8;
            g.drawLine(x - len, y, x + len, y);
            g.drawLine(x, y - len, x, y + len);
            int r = 5;
            g.drawOval(x - r, y - r, r * 2, r * 2);

            drawBottomLabel(g, w, h, lines);
        } finally {
            g.dispose();
        }
        return out;
    }

    // ---------- Hu + principal axis ----------
    private static BufferedImage overlayHuWithAxis(
            BufferedImage src, double cx, double cy,
            double mu20, double mu02, double mu11, double[] hu
    ) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, null);

            // centroid
            int x = (int) Math.round(cx);
            int y = (int) Math.round(cy);
            g.setColor(Color.GREEN);
            int len = 8;
            g.drawLine(x - len, y, x + len, y);
            g.drawLine(x, y - len, x, y + len);
            int r = 5;
            g.drawOval(x - r, y - r, r * 2, r * 2);

            // principal axis
            double theta = 0.5 * Math.atan2(2.0 * mu11, (mu20 - mu02));
            double L = 0.45 * Math.hypot(w, h);
            int x1 = (int) Math.round(cx - L * Math.cos(theta));
            int y1 = (int) Math.round(cy - L * Math.sin(theta));
            int x2 = (int) Math.round(cx + L * Math.cos(theta));
            int y2 = (int) Math.round(cy + L * Math.sin(theta));

            g.setColor(new Color(255, 200, 0));
            g.setStroke(new BasicStroke(2.0f));
            g.drawLine(x1, y1, x2, y2);

            // Hu (log10|phi|)
            String[] lines = new String[2];
            StringBuilder a = new StringBuilder("Hu (log10|phi|): ");
            for (int i = 0; i < 4; i++) {
                double v = hu[i];
                double lv = (v == 0.0) ? Double.NEGATIVE_INFINITY : Math.log10(Math.abs(v));
                a.append(String.format("φ%d=%.2f", i + 1, lv));
                if (i < 3) a.append("  ");
            }
            StringBuilder b = new StringBuilder();
            for (int i = 4; i < 7; i++) {
                double v = hu[i];
                double lv = (v == 0.0) ? Double.NEGATIVE_INFINITY : Math.log10(Math.abs(v));
                if (i == 4) b.append("        ");
                b.append(String.format("φ%d=%.2f", i + 1, lv));
                if (i < 6) b.append("  ");
            }
            lines[0] = a.toString();
            lines[1] = b.toString();

            drawBottomLabel(g, w, h, lines);
        } finally {
            g.dispose();
        }
        return out;
    }

    // ---------- bottom-left label ----------
    private static void drawBottomLabel(Graphics2D g, int w, int h, String... lines) {
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();
        int pad = 6, gap = 3;
        int tw = 0, th = 0;
        for (String s : lines) {
            if (s == null) continue;
            tw = Math.max(tw, fm.stringWidth(s));
            th += fm.getHeight() + gap;
        }
        if (th > 0) th -= gap;

        int boxW = tw + pad * 2;
        int bx = Math.max(8, w - boxW - 8);
        int by = h - th - 8;

        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(bx - pad, by - fm.getAscent() - pad, boxW, th + pad * 2, 12, 12);
        g.setColor(Color.WHITE);

        int y = by;
        for (String s : lines) {
            if (s == null) continue;
            g.drawString(s, bx, y);
            y += fm.getHeight() + gap;
        }
    }

    // ---------- Moments (คำนวณบน Binary 0/1) ----------
    // M_pq = Σ_i Σ_j x^q y^p f(i,j)  (x=j, y=i; zero-origin)
    public static double rawMoment(double[][] f, int p, int q) {
        int h = f.length, w = f[0].length;
        double M = 0.0;
        for (int i = 0; i < h; i++) {
            double yiPow = powInt(i, p);
            for (int j = 0; j < w; j++) {
                double xjPow = powInt(j, q);
                M += yiPow * xjPow * f[i][j]; // f เป็น 0/1 แล้ว
            }
        }
        return M;
    }

    // μ_pq = Σ_i Σ_j (x-x̄)^q (y-ȳ)^p f(i,j)
    public static double centralMoment(double[][] f, int p, int q) {
        int h = f.length, w = f[0].length;
        double M00 = 0.0, M10 = 0.0, M01 = 0.0;
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                double fij = f[i][j]; // 0/1
                M00 += fij;
                M10 += j * fij;
                M01 += i * fij;
            }
        }
        if (M00 == 0.0) return 0.0;
        double xbar = M10 / M00, ybar = M01 / M00;

        double mu = 0.0;
        for (int i = 0; i < h; i++) {
            double y = i - ybar;
            double yPow = powInt(y, p);
            for (int j = 0; j < w; j++) {
                double xPow = powInt(j - xbar, q);
                mu += yPow * xPow * f[i][j];
            }
        }
        return mu;
    }

    // η_pq = μ_pq / μ_00^{1 + (p+q)/2}
    public static double normalizedCentralMoment(double[][] f, int p, int q) {
        double mu00 = mu00(f);
        if (mu00 == 0.0) return 0.0;
        double muPQ = centralMoment(f, p, q);
        double gamma = 1.0 + 0.5 * (p + q);
        return muPQ / Math.pow(mu00, gamma);
    }

    public static double mu00(double[][] f) {
        return rawMoment(f, 0, 0);
    }

    // Hu φ1..φ7 (บน Binary)
    public static double[] huMoments(double[][] f) {
        double n20 = normalizedCentralMoment(f, 2, 0);
        double n02 = normalizedCentralMoment(f, 0, 2);
        double n11 = normalizedCentralMoment(f, 1, 1);
        double n30 = normalizedCentralMoment(f, 3, 0);
        double n03 = normalizedCentralMoment(f, 0, 3);
        double n21 = normalizedCentralMoment(f, 2, 1);
        double n12 = normalizedCentralMoment(f, 1, 2);

        double[] phi = new double[7];
        phi[0] = n20 + n02;
        phi[1] = (n20 - n02) * (n20 - n02) + 4.0 * n11 * n11;
        phi[2] = (n30 - 3 * n12) * (n30 - 3 * n12) + (3 * n21 - n03) * (3 * n21 - n03);
        phi[3] = (n30 + n12) * (n30 + n12) + (n21 + n03) * (n21 + n03);
        phi[4] = (n30 - 3 * n12) * (n30 + n12) * ((n30 + n12) * (n30 + n12) - 3 * (n21 + n03) * (n21 + n03))
               + (3 * n21 - n03) * (n21 + n03) * (3 * (n30 + n12) * (n30 + n12) - (n21 + n03) * (n21 + n03));
        phi[5] = (n20 - n02) * ((n30 + n12) * (n30 + n12) - (n21 + n03) * (n21 + n03))
               + 4.0 * n11 * (n30 + n12) * (n21 + n03);
        phi[6] = (3 * n21 - n03) * (n30 + n12) * ((n30 + n12) * (n30 + n12) - 3 * (n21 + n03) * (n21 + n03))
               - (n30 - 3 * n12) * (n21 + n03) * (3 * (n30 + n12) * (n30 + n12) - (n21 + n03) * (n21 + n03));
        return phi;
    }

    // integer-power (non-negative)
    private static double powInt(double base, int exp) {
        if (exp == 0) return 1.0;
        double r = 1.0;
        for (int k = 0; k < exp; k++) r *= base;
        return r;
    }

    // ---------- RGB -> Gray ----------
    public static double[][] toGrayMatrix(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        double[][] g = new double[h][w];
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                int rgb = img.getRGB(j, i);
                int r = (rgb >> 16) & 0xFF;
                int gr = (rgb >> 8) & 0xFF;
                int b = (rgb) & 0xFF;
                double gray = 0.299 * r + 0.587 * gr + 0.114 * b;
                g[i][j] = gray;
            }
        }
        return g;
    }

    // ---------- force binary: 0/1 ----------
    private static void toBinary01InPlace(double[][] f) {
        int h = f.length, w = f[0].length;
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                f[i][j] = (f[i][j] >= 128.0) ? 1.0 : 0.0; // บังคับ 0/1 เสมอ
            }
        }
    }
}
