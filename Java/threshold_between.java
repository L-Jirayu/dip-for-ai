// threshold_between.java  (inclusive range: T1 <= gray <= T2)
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import javax.imageio.ImageIO;

public class threshold_between {
    public static void main(String[] args) throws IOException {
        // ====== พารามิเตอร์ ======
        String inName = (args.length >= 1) ? args[0] : "equalized.png";
        int T1 = (args.length >= 2) ? parseOrDefault(args[1], 85)  : 85;
        int T2 = (args.length >= 3) ? parseOrDefault(args[2], 170) : 170;

        // clamp 0..255 และบังคับ T1 < T2
        T1 = clamp8(T1);
        T2 = clamp8(T2);
        if (T1 >= T2) {
            int tmp = T1;
            T1 = Math.max(0, Math.min(254, T2 - 1));
            T2 = Math.min(255, Math.max(1, tmp + 1));
            System.out.println("Note: swapped/adjusted thresholds to enforce T1<T2");
        }
        final int GAP = 16;

        // ====== โฟลเดอร์ผลลัพธ์ ======
        File outDir = new File("threshold_result/threshold_double");
        if (!outDir.exists()) outDir.mkdirs();
        File graphDir = new File("graph/threshold");
        if (!graphDir.exists()) graphDir.mkdirs();
        File gpDir = new File(outDir, "geoprops");
        if (!gpDir.exists()) gpDir.mkdirs();

        // ====== อ่านรูป ======
        BufferedImage img = ImageIO.read(new File(inName));
        if (img == null) throw new IllegalArgumentException("Cannot read image: " + inName);
        int I = img.getHeight(), J = img.getWidth(), N = I * J;
        System.out.println("Input: " + inName + "  Size: [" + I + ", " + J + "]  T1=" + T1 + "  T2=" + T2);
        System.out.println("Rule: white=255 for T1 <= gray <= T2 (inclusive interval)");

        // ====== Histogram ======
        int[] hist = new int[256];
        for (int u = 0; u < I; u++)
            for (int v = 0; v < J; v++)
                hist[img.getRGB(v, u) & 0xFF]++;

        // ====== Band-pass (T1 <= x <= T2 => white) ======
        BufferedImage betweenOne = new BufferedImage(J, I, BufferedImage.TYPE_BYTE_GRAY);
        long countIn = 0, countOut = 0;
        for (int u = 0; u < I; u++) {
            for (int v = 0; v < J; v++) {
                int gray = img.getRGB(v, u) & 0xFF;
                boolean inRange = (gray >= T1) && (gray <= T2);   // <-- รวมปลาย
                int bin = inRange ? 255 : 0;
                if (inRange) countIn++; else countOut++;
                betweenOne.setRGB(v, u, 0xFF000000 | (bin<<16) | (bin<<8) | bin);
            }
        }
        ImageIO.write(betweenOne, "png", new File(outDir, "between_1.png"));

        // ====== Band-stop (complement): นอกช่วง => white ======
        BufferedImage betweenZero = new BufferedImage(J, I, BufferedImage.TYPE_BYTE_GRAY);
        for (int u = 0; u < I; u++) {
            for (int v = 0; v < J; v++) {
                int gray = img.getRGB(v, u) & 0xFF;
                boolean outRange = (gray < T1) || (gray > T2);    // <-- ตรงข้ามแบบรวมปลาย
                int bin = outRange ? 255 : 0;
                betweenZero.setRGB(v, u, 0xFF000000 | (bin<<16) | (bin<<8) | bin);
            }
        }
        ImageIO.write(betweenZero, "png", new File(outDir, "between_0.png"));

        // ====== GeoProps ======
        final int BIN_THR = 128;
        geopros.BothResult brOne  = geopros.measureBoth(betweenOne, BIN_THR);
        geopros.Result whiteIn    = brOne.white;               // พิกเซลในช่วงเป็น 1 (ขาว)
        geopros.BothResult brZeroRaw = geopros.measureBoth(betweenZero, 128);
        geopros.BothResult brZero    = geopros.swap(brZeroRaw);
        geopros.Result outBlack      = brZero.black; // นอกช่วงเป็นดำ(0)


        System.out.printf("[between_1] WHITE(1, T1<=x<=T2) area=%d, centroid=(%.3f, %.3f)%n",
                whiteIn.area, whiteIn.cx, whiteIn.cy);
        System.out.printf("[between_0] BLACK(0, outside) area=%d, centroid=(%.3f, %.3f)%n",
                outBlack.area, outBlack.cx, outBlack.cy);

        // ====== Report ======
        File resultDir = new File(outDir, "txt_result");
        if (!resultDir.exists()) resultDir.mkdirs();
        try (PrintWriter pw = new PrintWriter(new File(resultDir, "threshold_between_report.txt"))) {
            pw.println("Input file: " + inName);
            pw.println("Size (I,J): " + I + ", " + J);
            pw.println("N (pixels): " + N);
            pw.println("Thresholds (inclusive): T1=" + T1 + "  T2=" + T2);
            pw.println();
            pw.println("--- Pixel counts ---");
            pw.printf("between_1: In-range white(1)=%d, black(0)=%d%n", countIn, countOut);
            pw.printf("between_0: Out-range black(0)=%d%n", outBlack.area);
            pw.println();
            pw.println("=== Geometric Properties on between_1 (T1<=x<=T2, white=1) ===");
            writeGeo(pw, "WHITE (1)", whiteIn);
            pw.println();
            pw.println("=== Geometric Properties on between_0 (outside, black=0) ===");
            writeGeo(pw, "BLACK (0)", outBlack);
        }

        // ====== Annotated Centroid Images ======
        BufferedImage oneAnn  = deepCopy(betweenOne);
        BufferedImage zeroAnn = deepCopy(betweenZero);
        drawCentroid(oneAnn,  whiteIn.cx,  whiteIn.cy,  new Color(0,200,0),   "Centroid IN(1)");
        drawCentroid(zeroAnn, outBlack.cx, outBlack.cy, new Color(220,60,60), "Centroid OUT(0)");
        ImageIO.write(oneAnn,  "png", new File(gpDir, "between_1gp.png"));
        ImageIO.write(zeroAnn, "png", new File(gpDir, "between_0gp.png"));

        // ====== Compare Images ======
        BufferedImage eq = inName.equalsIgnoreCase("equalized.png") ? img : ImageIO.read(new File("equalized.png"));
        saveTripleSideBySide(
                eq, betweenOne, betweenZero, GAP,
                "Equalized",
                "Between (T1≤gray≤T2)",
                "Outside (gray<T1 or gray>T2)",
                new File(outDir, "compare_eq_between.png").getAbsolutePath()
        );
        saveDoubleSideBySide(
                oneAnn, zeroAnn, GAP,
                "Between (IN) [Centroid]",
                "Outside [Centroid]",
                new File(gpDir, "compare_between_centroids.png").getAbsolutePath()
        );

        // ====== Histogram Graph (แสดงขนาด In/Out ด้วย) ======
        drawHistogramWithDoubleThresholdStyled(hist, T1, T2, countIn, countOut, new File(graphDir, "double_threshold.png"));
    }

    private static int clamp8(int x) { return Math.max(0, Math.min(255, x)); }
    private static int parseOrDefault(String s, int def) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return def; }
    }

    private static void writeGeo(PrintWriter pw, String title, geopros.Result r) {
        pw.println("  -- " + title + " --");
        if (!r.hasObject()) {
            pw.println("     Area: 0 (no object)");
            pw.println("     Centroid: N/A");
            pw.println("     Bounding Box: N/A");
        } else {
            pw.println("     Area: " + r.area + " pixels");
            pw.printf ("     Centroid: (x=%.3f, y=%.3f)%n", r.cx, r.cy);
            pw.printf ("     BBox: [i_min=%d, j_min=%d] .. [i_max=%d, j_max=%d]%n",
                       r.minI, r.minJ, r.maxI, r.maxJ);
        }
    }

    // === Utilities ===
    private static BufferedImage deepCopy(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = copy.createGraphics();
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return copy;
    }

    private static void drawCentroid(BufferedImage img, double cx, double cy, Color color, String label) {
        if (Double.isNaN(cx) || Double.isNaN(cy)) return;
        int x = (int)Math.round(cx), y = (int)Math.round(cy);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color); g2.setStroke(new BasicStroke(2f));
        g2.drawLine(x-8, y, x+8, y); g2.drawLine(x, y-8, x, y+8);
        g2.drawOval(x-5, y-5, 10, 10);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 12f));
        g2.setColor(Color.WHITE); g2.drawString(label, x+12, y-6);
        g2.dispose();
    }

    private static void saveTripleSideBySide(BufferedImage left, BufferedImage mid, BufferedImage right,
                                             int gap, String l, String m, String r, String outPath) throws IOException {
        int h = Math.max(left.getHeight(), Math.max(mid.getHeight(), right.getHeight()));
        int w = left.getWidth() + gap + mid.getWidth() + gap + right.getWidth();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(30,30,30)); g2.fillRect(0,0,w,h);
        g2.drawImage(left, 0, 0, null);
        g2.setColor(new Color(45,45,45)); g2.fillRect(left.getWidth(), 0, gap, h);
        int xMid = left.getWidth() + gap;
        g2.drawImage(mid, xMid, 0, null);
        g2.setColor(new Color(45,45,45)); g2.fillRect(xMid + mid.getWidth(), 0, gap, h);
        int xRight = xMid + mid.getWidth() + gap;
        g2.drawImage(right, xRight, 0, null);
        g2.setColor(Color.WHITE);
        g2.drawString(l, 8, 16); g2.drawString(m, xMid + 8, 16); g2.drawString(r, xRight + 8, 16);
        g2.dispose(); ImageIO.write(out, "png", new File(outPath));
    }

    private static void saveDoubleSideBySide(BufferedImage left, BufferedImage right,
                                             int gap, String l, String r, String outPath) throws IOException {
        int h = Math.max(left.getHeight(), right.getHeight());
        int w = left.getWidth() + gap + right.getWidth();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(30,30,30)); g2.fillRect(0,0,w,h);
        g2.drawImage(left, 0, 0, null);
        g2.setColor(new Color(45,45,45)); g2.fillRect(left.getWidth(), 0, gap, h);
        int xRight = left.getWidth() + gap;
        g2.drawImage(right, xRight, 0, null);
        g2.setColor(Color.WHITE);
        g2.drawString(l, 8, 16); g2.drawString(r, xRight + 8, 16);
        g2.dispose(); ImageIO.write(out, "png", new File(outPath));
    }

    private static void drawHistogramWithDoubleThresholdStyled(int[] h, int T1, int T2, long inCount, long outCount, File outFile) throws IOException {
        int K=256, barW=2, margin=50, chartH=300;
        int chartW=K*barW, width=chartW+margin*2, height=chartH+margin*2+36;
        int max=maxOf(h); if(max==0) max=1;

        BufferedImage out=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2=out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE); g2.fillRect(0,0,width,height);

        int x0=margin, y0=height-margin;

        // headings
        g2.setColor(Color.BLACK);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 14f));
        String headL="0 = Black (outside)";
        String headR="1 = White (T1≤x≤T2)";
        g2.drawString(headL, x0, margin-18);
        int wR=g2.getFontMetrics().stringWidth(headR);
        g2.drawString(headR, x0+chartW-wR, margin-18);

        // sizes
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));
        String sizes=String.format("Size — In(1): %d   Out(0): %d", inCount, outCount);
        int sw=g2.getFontMetrics().stringWidth(sizes);
        g2.setColor(Color.DARK_GRAY);
        g2.drawString(sizes, x0+chartW/2 - sw/2, margin-2);

        // axes
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(x0,y0,x0,y0-chartH);
        g2.drawLine(x0,y0,x0+chartW,y0);

        // bars
        g2.setColor(new Color(60,120,200));
        for(int i=0;i<K;i++){
            double ratio=(double)h[i]/max;
            int hPix=(int)Math.round(ratio*chartH);
            g2.fillRect(x0+i*barW, y0-hPix, barW, hPix);
        }

        // ticks X
        g2.setColor(Color.BLACK);
        for(int i=0;i<=255;i+=50){
            int x=x0+i*barW;
            g2.drawLine(x,y0,x,y0+5);
            g2.drawString(String.valueOf(i), x-10, y0+20);
        }

        // ticks Y
        int steps=5;
        for(int s=0;s<=steps;s++){
            int val=max*s/steps;
            int y=y0-(int)((double)val/max*chartH);
            g2.drawLine(x0-5,y,x0,y);
            g2.drawString(String.valueOf(val), x0-45, y+5);
        }

        // T1, T2
        g2.setColor(Color.RED);
        g2.setStroke(new BasicStroke(2f));
        int xT1=x0+T1*barW, xT2=x0+T2*barW;
        g2.drawLine(xT1, y0-chartH, xT1, y0);
        g2.drawLine(xT2, y0-chartH, xT2, y0);
        g2.drawString("T1 = "+T1, xT1+6, y0-chartH+16);
        g2.drawString("T2 = "+T2, xT2+6, y0-chartH+32);

        // footer
        g2.setColor(Color.BLACK);
        g2.drawString("Histogram (0..255) with Double Threshold (inclusive)", x0+chartW/2-150, height-10);

        g2.dispose();
        ImageIO.write(out, "png", outFile);
    }

    private static int maxOf(int[] a){int m=0; for(int x:a) if(x>m) m=x; return m;}
}
