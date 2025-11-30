# main.py
# ใช้: python main.py <image_path> [threshold]
# ถ้าไม่ส่งอาร์กิวเมนต์: จะลองหา picture.png หรือ picture.jpg ในโฟลเดอร์เดียวกัน
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from maclearn import train_ml, predict_with_ml, build_mock_dataset, k_fold_cross_validation, PureDecisionTree, PureRandomForest

from dip import (
    read_image_grayscale,
    rgb_to_grayscale,
    histogram_equalize,
    threshold_binary,
    size_area,
    centroid,
    raw_moment,
    central_moment,
    hu_moments,
    save_visualizations_png,
    detect_shape_by_hu,
    plot_histogram,
    plot_cdf,
    plot_histogram_equalization,
)
from maclearn import train_ml, predict_with_ml

def main():
    # เลือกไฟล์อัตโนมัติถ้าไม่ส่งอาร์กิวเมนต์
    if len(sys.argv) < 2:
        here = os.path.dirname(os.path.abspath(__file__))
        cand = [os.path.join(here, "picture.png"), os.path.join(here, "picture.jpg")]
        path = next((p for p in cand if os.path.exists(p)), None)
        if not path:
            print("Usage: python main.py <image_path> [threshold]")
            sys.exit(2)
    else:
        path = sys.argv[1]

    T = int(sys.argv[2]) if len(sys.argv) >= 3 else 128

    out_lines = []
    def log(s):
        print(s)
        out_lines.append(s)

    log("=== DIP PIPELINE (Pillow for I/O; logic = pure Python) ===")
    log(f"[Input] {path}")

    # 1) Load → grayscale (0..255)
    gray = read_image_grayscale(path)
    H, W = len(gray), len(gray[0])
    log(f"[Load] size = {W}x{H}")
    gray = rgb_to_grayscale(gray)  # safety

    # 1.5) Plot histogram / CDF
    plot_histogram(gray, "histogram_input.png")
    plot_cdf(gray, "cdf_input.png")
    log("[Plots] histogram_input.png, cdf_input.png")

    # 2) Histogram Equalization
    eq = histogram_equalize(gray)
    log("[HistEq] done")
    plot_histogram_equalization(eq, "histogram_equalized.png")
    log("[Plots] histogram_equalized.png")

    # 3) Thresholding
    log(f"[Threshold] T = {T}")
    mask = threshold_binary(eq, T)

    # 4) Geometric & Moments
    A = size_area(mask)
    Cx, Cy = centroid(mask)
    log(f"[Geo] area={A}, centroid=({Cx:.2f},{Cy:.2f})")

    M00 = raw_moment(mask, 0, 0)
    M10 = raw_moment(mask, 1, 0)
    M01 = raw_moment(mask, 0, 1)
    log(f"[RawMoment] M00={M00:.4f}, M10={M10:.4f}, M01={M01:.4f}")

    if M00 > 0:
        xc = M10 / M00
        yc = M01 / M00
        mu20 = central_moment(mask, 2, 0, xc, yc)
        mu02 = central_moment(mask, 0, 2, xc, yc)
        mu11 = central_moment(mask, 1, 1, xc, yc)
        log(f"[CentralMoment] mu20={mu20:.4f}, mu02={mu02:.4f}, mu11={mu11:.4f}")
    else:
        xc, yc = Cx, Cy
        log("[CentralMoment] empty mask")

    hu = hu_moments(mask)
    log("[Hu] 7 invariants:")
    for i, v in enumerate(hu, 1):
        log(f"  φ{i}: {v:.6f}")

    # Hu-based shape detection
    shape_name, shape_dist = detect_shape_by_hu(mask)
    log(f"[Shape(Hu)] {shape_name} (distance={shape_dist:.6f})")

    # =========================================================
    # ส่วนที่เพิ่ม: K-Fold Validation (เพื่อโชว์ความเก๋า)
    # =========================================================
    log("\n[K-Fold] Validating Models with Augmented Data...")
    
    # โหลดข้อมูลมาเพื่อทำ K-Fold
    X_mock, y_mock = build_mock_dataset()
    log(f"[Dataset] Generated {len(X_mock)} samples for validation.")

    # ทดสอบ Decision Tree
    log(">> Testing Decision Tree (5-Fold):")
    acc_dt = k_fold_cross_validation(PureDecisionTree, X_mock, y_mock, k=5, model_params={'max_depth': 10})
    log(f"   Decision Tree Avg Accuracy: {acc_dt:.2f}%")

    # ทดสอบ Random Forest
    log(">> Testing Random Forest (5-Fold):")
    acc_rf = k_fold_cross_validation(PureRandomForest, X_mock, y_mock, k=5, model_params={'n_estimators': 20, 'max_depth': 10})
    log(f"   Random Forest Avg Accuracy: {acc_rf:.2f}%")

    # =========================================================
    # ส่วนเดิม: Train Final Model (ใช้ข้อมูลทั้งหมด) เพื่อทำนายภาพจริง
    # =========================================================
    log("\n[ML] Training Final Model on ALL mock data...")
    clf_dt, clf_rf = train_ml() # train_ml จะไปเรียก build_mock_dataset ตัวใหม่เอง

    label_dt,  prob_dt  = predict_with_ml(mask, clf_dt)
    label_rf,  prob_rf  = predict_with_ml(mask, clf_rf)

    log(f"[Result] Decision Tree predicted: {label_dt} ({prob_dt:.2f}%)")
    log(f"[Result] Random Forest predicted: {label_rf} ({prob_rf:.2f}%)")

    # Save visuals
    save_visualizations_png(gray, eq, mask, (Cx, Cy), (xc, yc))
    log("[Saved] out_gray.png, out_equalized.png, out_threshold.png, out_overlay.png")

    # Save text report
    with open("dip_result.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(out_lines) + "\n")
    log("[Saved] dip_result.txt")

if __name__ == "__main__":
    main()
