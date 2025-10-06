public class histro_threshold {

    public static void main(String[] args) {
        // args optional:
        // args[0] = input image (default: "picture.png") -> ใช้โดย histogram ถ้ารองรับ
        // args[1] = T  (for threshold, default: 128)
        // args[2] = T1 (for threshold_between, default: 85)
        // args[3] = T2 (for threshold_between, default: 170)

        final String T  = (args.length >= 2) ? args[1] : "128";
        final String T1 = (args.length >= 3) ? args[2] : "85";
        final String T2 = (args.length >= 4) ? args[3] : "170";

        try {
            // 1) Histogram + Equalization -> "equalized.png"
            System.out.println("== Step 1: histogram ==");
            if (args.length >= 1) {
                histogram.main(new String[] { args[0] });
            } else {
                histogram.main(new String[0]);
            }

            // 2) Single threshold บน equalized.png
            System.out.println("\n== Step 2: threshold (single) ==");
            threshold.main(new String[] { "equalized.png", T });

            // 3) Double threshold บน equalized.png
            System.out.println("\n== Step 3: threshold_between (double) ==");
            threshold_between.main(new String[] { "equalized.png", T1, T2 });

            // 4) Image Moment บนผล single-threshold
            System.out.println("\n== Step 4: image_moment (on single-threshold result) ==");
            image_moment.main(new String[0]);

            System.out.println("\nAll done.");
            System.out.println("Outputs:");
            System.out.println("- equalized.png (from histogram)");
            System.out.println("- threshold_result/... (single & double)");
            System.out.println("- graph/... (histogram & threshold graphs)");
            System.out.println("- image_moment/picture_output/{raw_moment.png, central_moment.png, hu_moment.png}");
            System.out.println("- image_moment/txt_output/moment_report.txt");

        } catch (Exception e) {
            // ✅ แก้ตรงนี้: แสดงข้อความ + printStackTrace
            System.err.println("Pipeline error: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
}
