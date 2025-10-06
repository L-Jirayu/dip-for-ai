// threshold.java
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import javax.imageio.ImageIO;

public class threshold {
    public static void main(String[] args) throws IOException {
        // ====== พารามิเตอร์ ======
        String inName = (args.length >= 1) ? args[0] : "equalized.png";
        int T = 128;
        if (args.length >= 2) {
            try {
                T = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid T, fallback to 128");
                T = 128;
            }
        }
        T = Math.max(0, Math.min(255, T));
        final int GAP = 16;

        // ====== โฟลเดอร์ผลลัพธ์ ======
        File outDir = new File("threshold_result/threshold_single");
        if (!outDir.exists()) outDir.mkdirs();
        File graphDir = new File("graph/threshold");
        if (!graphDir.exists()) graphDir.mkdirs();
        File gpDir = new File(outDir, "geoprops");
        if (!gpDir.exists()) gpDir.mkdirs();

        // ====== อ่านรูป ======
        BufferedImage img = ImageIO.read(new File(inName));
        if (img == null) throw new IllegalArgumentException("Cannot read image: " + inName);
        int I = img.getHeight(), J = img.getWidth(), N = I * J;
        System.out.println("Input: " + inName + "  Size: [" + I + ", " + J + "]  T=" + T);

        // ====== Histogram ======
        int[] hist = new int[256];
        for (int u = 0; u < I; u++) {
            for (int v = 0; v < J; v++) {
                int gray = img.getRGB(v, u) & 0xFF;
                hist[gray]++;
            }
        }

        // ====== Threshold: gray>=T -> 255, else 0 (threshold_1) ======
        BufferedImage thrOne = new BufferedImage(J, I, BufferedImage.TYPE_BYTE_GRAY);
        long countWhite = 0, countBlack = 0; // บน threshold_1 (white=gray>=T)
        for (int u = 0; u < I; u++) {
            for (int v = 0; v < J; v++) {
                int gray = img.getRGB(v, u) & 0xFF;
                int bin = (gray >= T) ? 255 : 0;
                if (bin == 255) countWhite++; else countBlack++;
                thrOne.setRGB(v, u, 0xFF000000 | (bin<<16) | (bin<<8) | bin);
            }
        }
        ImageIO.write(thrOne, "png", new File(outDir, "threshold_1.png"));

        // ====== Threshold (inverted): gray<T -> 255, else 0 (threshold_0) ======
        BufferedImage thrZero = new BufferedImage(J, I, BufferedImage.TYPE_BYTE_GRAY);
        for (int u = 0; u < I; u++) {
            for (int v = 0; v < J; v++) {
                int gray = img.getRGB(v, u) & 0xFF;
                int bin = (gray < T) ? 255 : 0;
                thrZero.setRGB(v, u, 0xFF000000 | (bin<<16) | (bin<<8) | bin);
            }
        }
        ImageIO.write(thrZero, "png", new File(outDir, "threshold_0.png"));

        // ====== GeoProps: วัดทั้งสองภาพ โดย 1=ขาว(255), 0=ดำ(0) ======
        final int BIN_THR = 128; // แยก 0/255
        // บน threshold_1
        geopros.BothResult oneBR = geopros.measureBoth(thrOne, BIN_THR);
        geopros.Result oneWhite = oneBR.white; // WHITE(1) = gray>=T

        // บน threshold_0 (สลับเพื่อให้ .black หมายถึง gray<T ตามที่ต้องการรายงาน)
        geopros.BothResult zeroBR_raw = geopros.measureBoth(thrZero, BIN_THR);
        geopros.BothResult zeroBR = geopros.swap(zeroBR_raw);
        geopros.Result zeroBlack = zeroBR.black; // BLACK(0) (report) = gray<T

        // Console log (ยืนยันผล)
        System.out.printf("[threshold_1] WHITE(1) area=%d, centroid=(%.3f, %.3f)%n",
                oneWhite.area, oneWhite.cx, oneWhite.cy);
        System.out.printf("[threshold_0] BLACK(0) area=%d, centroid=(%.3f, %.3f)%n",
                zeroBlack.area, zeroBlack.cx, zeroBlack.cy);

        // invariants check
        if (oneWhite.area != countWhite)
            System.out.printf("WARN: threshold_1 WHITE area=%d but counted=%d%n", oneWhite.area, countWhite);
        if (zeroBlack.area != countBlack)
            System.out.printf("WARN: threshold_0 BLACK area=%d but expected=%d%n", zeroBlack.area, countBlack);

        // ====== สรุปผลลงไฟล์ ======
        File resultDir = new File(outDir, "txt_result");
        if (!resultDir.exists()) resultDir.mkdirs();
        try (PrintWriter pw = new PrintWriter(new File(resultDir, "threshold_report.txt"))) {
            pw.println("Input file: " + inName);
            pw.println("Size (I,J): " + I + ", " + J);
            pw.println("N (pixels): " + N);
            pw.println("Threshold T: " + T);
            pw.println("Note: In both binary images, we use 1=white(255) and 0=black(0).");
            pw.println();

            // Summary counts
            pw.println("--- Pixel counts ---");
            pw.println(String.format("threshold_1: white(1)=%d", countWhite));
            pw.println(String.format("threshold_0: black(0)=%d", countBlack));
            pw.println();

            // threshold_1
            pw.println("=== Geometric Properties on threshold_1 (1=white=gray>=T) ===");
            writeGeo(pw, "WHITE (1)", oneWhite);
            pw.println();

            // threshold_0
            pw.println("=== Geometric Properties on threshold_0 (0=black=gray<T) ===");
            writeGeo(pw, "BLACK (0)", zeroBlack);
        }

        // ====== วาดภาพ centroid แยกไฟล์ (geoprops) ======
        BufferedImage thrOneAnn  = deepCopy(thrOne);
        BufferedImage thrZeroAnn = deepCopy(thrZero);
        drawCentroid(thrOneAnn,  oneWhite.cx,  oneWhite.cy,  new Color(0, 200, 0),   "Centroid WHITE(1)");
        drawCentroid(thrZeroAnn, zeroBlack.cx, zeroBlack.cy, new Color(220, 60, 60), "Centroid BLACK(0)");
        ImageIO.write(thrOneAnn,  "png", new File(gpDir, "thereshold_1gp.png"));
        ImageIO.write(thrZeroAnn, "png", new File(gpDir, "thereshold_0gp.png"));

        // ====== ทำภาพรวม 3 รูป (โชว์ Equalized + 2 threshold) ======
        BufferedImage eq = inName.equalsIgnoreCase("equalized.png") ? img : ImageIO.read(new File("equalized.png"));
        saveTripleSideBySide(
                eq, thrOne, thrZero, GAP,
                "Equalized", "Thr (>=T → WHITE=1)", "Thr (<T → WHITE=1) / Report BLACK(0)",
                new File(outDir, "compare_eq_thr.png").getAbsolutePath()
        );

        // === ภาพคู่: threshold ที่มี centroid (เก็บใน geoprops) ===
        saveDoubleSideBySide(
                thrOneAnn, thrZeroAnn, GAP,
                "Thr (>=T → WHITE=1)  [Centroid WHITE(1)]",
                "Thr (<T → WHITE=1)  [Report BLACK(0) Centroid]",
                new File(gpDir, "compare_thr_centroids.png").getAbsolutePath()
        );
        System.out.println("Saved: " + new File(gpDir, "compare_thr_centroids.png").getPath());


        // ====== วาดกราฟ Histogram + เส้น Threshold + คำอธิบาย 0/1 และขนาด White/Black ======
        drawHistogramWithThresholdStyled(hist, T, countWhite, countBlack, new File(graphDir, "single_threshold.png"));
        System.out.println("Saved Graph in: graph/threshold/single_threshold.png");
        System.out.println("Saved: " + new File(gpDir, "thereshold_1gp.png").getPath());
        System.out.println("Saved: " + new File(gpDir, "thereshold_0gp.png").getPath());
    }

    // === helper: เขียน GeoProps ทีละเซ็ต ===
    private static void writeGeo(PrintWriter pw, String title, geopros.Result r) {
        pw.println("  -- " + title + " --");
        if (!r.hasObject()) {
            pw.println("     Area: 0 (no object)");
            pw.println("     Centroid: N/A");
            pw.println("     Bounding Box: N/A");
        } else {
            pw.println("     Area: " + r.area + " pixels");
            pw.println(String.format("     Centroid: (x=%.3f, y=%.3f)  [x=column(j), y=row(i)]", r.cx, r.cy));
            pw.println(String.format("     BBox (rows i, cols j): [i_min=%d, j_min=%d] .. [i_max=%d, j_max=%d]",
                    r.minI, r.minJ, r.maxI, r.maxJ));
        }
    }

    // === รวม 3 รูป ===
    private static void saveTripleSideBySide(BufferedImage left, BufferedImage mid, BufferedImage right,
                                             int gap, String leftLabel, String midLabel, String rightLabel,
                                             String outPath) throws IOException {
        int h = Math.max(left.getHeight(), Math.max(mid.getHeight(), right.getHeight()));
        int w = left.getWidth() + gap + mid.getWidth() + gap + right.getWidth();

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(30, 30, 30)); g2.fillRect(0, 0, w, h);
        int xLeft = 0;
        g2.drawImage(left, xLeft, 0, null);
        g2.setColor(new Color(45, 45, 45)); g2.fillRect(left.getWidth(), 0, gap, h);
        int xMid = left.getWidth() + gap;
        g2.drawImage(mid, xMid, 0, null);
        g2.setColor(new Color(45, 45, 45)); g2.fillRect(xMid + mid.getWidth(), 0, gap, h);
        int xRight = xMid + mid.getWidth() + gap;
        g2.drawImage(right, xRight, 0, null);

        g2.setColor(Color.WHITE);
        g2.drawString(leftLabel, 8, 16);
        g2.drawString(midLabel, xMid + 8, 16);
        g2.drawString(rightLabel, xRight + 8, 16);

        g2.dispose();
        ImageIO.write(out, "png", new File(outPath));
    }

    // === รวม 2 รูป (สำหรับเวอร์ชันที่มี centroid) ===
    private static void saveDoubleSideBySide(BufferedImage left, BufferedImage right,
                                            int gap, String leftLabel, String rightLabel,
                                            String outPath) throws IOException {
        int h = Math.max(left.getHeight(), right.getHeight());
        int w = left.getWidth() + gap + right.getWidth();

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(30, 30, 30)); g2.fillRect(0, 0, w, h);

        int xLeft = 0;
        g2.drawImage(left, xLeft, 0, null);

        g2.setColor(new Color(45, 45, 45)); 
        g2.fillRect(left.getWidth(), 0, gap, h);

        int xRight = left.getWidth() + gap;
        g2.drawImage(right, xRight, 0, null);

        g2.setColor(Color.WHITE);
        g2.drawString(leftLabel, 8, 16);
        g2.drawString(rightLabel, xRight + 8, 16);

        g2.dispose();
        ImageIO.write(out, "png", new File(outPath));
    }

    // === helper: copy ภาพแบบลึกเพื่อวาดทับโดยไม่ทำลายต้นฉบับ ===
    private static BufferedImage deepCopy(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = copy.createGraphics();
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return copy;
    }

    // === helper: วาดกากบาท + วงกลมเล็กที่ตำแหน่ง (cx, cy) ===
    private static void drawCentroid(BufferedImage img, double cx, double cy, Color color, String label) {
        if (Double.isNaN(cx) || Double.isNaN(cy)) return;
        int x = (int)Math.round(cx);
        int y = (int)Math.round(cy);

        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(color);
        g2.setStroke(new BasicStroke(2f));

        int r = 5; // รัศมีวงกลม
        // crosshair
        g2.drawLine(x - 8, y, x + 8, y);
        g2.drawLine(x, y - 8, x, y + 8);
        // circle
        g2.drawOval(x - r, y - r, 2*r, 2*r);

        // ป้ายชื่อเล็กๆ ข้างจุด
        if (label != null && !label.isEmpty()) {
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 12f));
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(label);
            int th = fm.getHeight();
            int pad = 4;
            int bx = x + 10, by = y - 10;
            g2.setColor(new Color(0,0,0,140));
            g2.fillRoundRect(bx - pad, by - th, tw + pad*2, th + pad, 8, 8);
            g2.setColor(Color.WHITE);
            g2.drawString(label, bx, by - 2);
        }

        g2.dispose();
    }

    // === วาด Histogram + Threshold + คำอธิบาย 0/1 + ขนาด White/Black ===
    private static void drawHistogramWithThresholdStyled(int[] h, int T, long whiteCount, long blackCount, File outFile) throws IOException {
    int K = 256, barW = 2, margin = 50;
    int chartW = K * barW, chartH = 300;
    int width = chartW + margin * 2, height = chartH + margin * 2 + 36; // ขยับเพิ่มนิดหน่อย
    int max = maxOf(h);
    if (max == 0) max = 1;

    BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = out.createGraphics();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // พื้นหลังขาว
    g2.setColor(Color.WHITE);
    g2.fillRect(0, 0, width, height);

    // ข้อความหัวบน: 0 = Back, 1 = Write
    g2.setColor(Color.BLACK);
    g2.setFont(g2.getFont().deriveFont(Font.BOLD, 14f));
    String headL = "0 = Back";
    String headR = "1 = Write";
    int x0 = margin, y0 = height - margin;
    int topTextY = margin - 18; // ขยับขึ้นเล็กน้อย
    g2.drawString(headL, x0, topTextY);
    int headRWidth = g2.getFontMetrics().stringWidth(headR);
    g2.drawString(headR, x0 + chartW - headRWidth, topTextY);

    // ข้อมูล Size ของ White/Black (ย้ายขึ้นมาใต้หัว)
    g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));
    String sizes = String.format("Size — White(1): %d   Black(0): %d", whiteCount, blackCount);
    int sw = g2.getFontMetrics().stringWidth(sizes);
    g2.setColor(Color.DARK_GRAY);
    int sizesY = topTextY + 16; // วางบรรทัดถัดจากหัว
    g2.drawString(sizes, x0 + chartW/2 - sw/2, sizesY);

    // แกน
    g2.setColor(Color.BLACK);
    g2.setStroke(new BasicStroke(2f));
    g2.drawLine(x0, y0, x0, y0 - chartH);  // Y
    g2.drawLine(x0, y0, x0 + chartW, y0);  // X

    // แท่ง histogram
    g2.setColor(new Color(60, 120, 200));
    for (int i = 0; i < K; i++) {
        double ratio = (double) h[i] / max;
        int hPix = (int) Math.round(ratio * chartH);
        int x = x0 + i * barW;
        int y = y0 - hPix;
        g2.fillRect(x, y, barW, hPix);
    }

    // ticks X
    g2.setColor(Color.BLACK);
    for (int i = 0; i <= 255; i += 50) {
        int x = x0 + i * barW;
        g2.drawLine(x, y0, x, y0 + 5);
        g2.drawString(String.valueOf(i), x - 10, y0 + 20);
    }

    // ticks Y
    int steps = 5;
    for (int s = 0; s <= steps; s++) {
        int val = max * s / steps;
        int y = y0 - (int)((double)val / max * chartH);
        g2.drawLine(x0 - 5, y, x0, y);
        g2.drawString(String.valueOf(val), x0 - 45, y + 5);
    }

    // เส้น Threshold สีแดง
    g2.setColor(Color.RED);
    int xT = x0 + T * barW;
    g2.setStroke(new BasicStroke(2f));
    g2.drawLine(xT, y0 - chartH, xT, y0);
    g2.drawString("T = " + T, xT + 6, y0 - chartH + 16);

    // ===== ชื่อกราฟไว้ล่างเหมือนเดิม =====
    g2.setColor(Color.BLACK);
    g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));
    g2.drawString("Histogram (0..255) with Threshold", x0 + chartW/2 - 90, height - 10);


    g2.dispose();
    ImageIO.write(out, "png", outFile);
}

    private static int maxOf(int[] a) {
        int m = 0;
        for (int x : a) if (x > m) m = x;
        return m;
    }
}
