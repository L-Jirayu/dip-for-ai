import java.awt.image.BufferedImage;

public class geopros {
    public static class Result {
        public final long area;      // จำนวนพิกเซลของวัตถุ
        public final double cx;      // centroid x (คอลัมน์ j)
        public final double cy;      // centroid y (แถว i)
        public final int minI, minJ, maxI, maxJ; // bounding box [min..max], รวมขอบ
        public Result(long area, double cx, double cy, int minI, int minJ, int maxI, int maxJ) {
            this.area = area; this.cx = cx; this.cy = cy;
            this.minI = minI; this.minJ = minJ; this.maxI = maxI; this.maxJ = maxJ;
        }
        public boolean hasObject() { return area > 0; }
    }

    /** ผลลัพธ์สองมุมมอง: ขาวเป็นวัตถุ / ดำเป็นวัตถุ */
    public static class BothResult {
        public final Result white; // pixels with gray >= thr
        public final Result black; // pixels with gray <  thr
        public BothResult(Result white, Result black) { this.white = white; this.black = black; }
    }

    /** เดิม: เทียบเท่า measureWhite(bin, thr) (ขาวเป็นวัตถุ) */
    public static Result measure(BufferedImage bin, int thr) {
        return measureWhite(bin, thr);
    }

    /** ขาวเป็นวัตถุ: นับ gray >= thr */
    public static Result measureWhite(BufferedImage bin, int thr) {
        BothResult br = measureBoth(bin, thr);
        return br.white;
    }

    /** ดำเป็นวัตถุ: นับ gray < thr */
    public static Result measureBlack(BufferedImage bin, int thr) {
        BothResult br = measureBoth(bin, thr);
        return br.black;
    }

    /** คำนวณสองมุมมองในรอบเดียว เพื่อความเร็ว */
    public static BothResult measureBoth(BufferedImage bin, int thr) {
        final int I = bin.getHeight();
        final int J = bin.getWidth();

        long Aw = 0, m10w = 0, m01w = 0;
        int minIw = Integer.MAX_VALUE, minJw = Integer.MAX_VALUE;
        int maxIw = -1, maxJw = -1;

        long Ab = 0, m10b = 0, m01b = 0;
        int minIb = Integer.MAX_VALUE, minJb = Integer.MAX_VALUE;
        int maxIb = -1, maxJb = -1;

        for (int i = 0; i < I; i++) {
            for (int j = 0; j < J; j++) {
                int gray = bin.getRGB(j, i) & 0xFF; // assume R=G=B
                if (gray >= thr) { // ขาวเป็นวัตถุ
                    Aw++; m10w += j; m01w += i;
                    if (i < minIw) minIw = i;
                    if (i > maxIw) maxIw = i;
                    if (j < minJw) minJw = j;
                    if (j > maxJw) maxJw = j;
                } else { // ดำเป็นวัตถุ
                    Ab++; m10b += j; m01b += i;
                    if (i < minIb) minIb = i;
                    if (i > maxIb) maxIb = i;
                    if (j < minJb) minJb = j;
                    if (j > maxJb) maxJb = j;
                }
            }
        }

        Result rw = (Aw == 0)
            ? new Result(0, Double.NaN, Double.NaN, -1, -1, -1, -1)
            : new Result(Aw, (double)m10w / Aw, (double)m01w / Aw, minIw, minJw, maxIw, maxJw);

        Result rb = (Ab == 0)
            ? new Result(0, Double.NaN, Double.NaN, -1, -1, -1, -1)
            : new Result(Ab, (double)m10b / Ab, (double)m01b / Ab, minIb, minJb, maxIb, maxJb);

        return new BothResult(rw, rb);
    }

    /** สลับผล white <-> black (ไว้ใช้กรณีพิเศษ เช่นอยากเรียก .black แต่ได้ความหมายของ white เดิม) */
    public static BothResult swap(BothResult br) {
        return new BothResult(br.black, br.white);
    }
}
