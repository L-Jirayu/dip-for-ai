# maclearn.py — NN classifier using Hu-only (log-abs) features (fixed & full)
from math import sqrt, log10
from dip import hu_moments

# ---------------- Feature ----------------
def _hu_log_abs(vec):
    out = []
    for v in vec:
        av = abs(v)
        out.append(0.0 if av < 1e-30 else -log10(av))
    return out

def extract_features(mask):
    # ใช้เฉพาะ Hu (log-abs) → invariant ต่อ scale/rotation/translation
    hu = hu_moments(mask)       # 7 ค่า
    return _hu_log_abs(hu)      # 7 ค่า

# ---------------- Simple NN ----------------
class SimpleNN:
    def __init__(self):
        self.protos = []  # [(label, feature_vector)]

    def add(self, label, feat_vec):
        self.protos.append((label, feat_vec[:]))

    def predict(self, feat_vec):
        best_label, best_d2 = None, 1e18
        for lab, fv in self.protos:
            d2 = 0.0
            for a, b in zip(feat_vec, fv):
                d2 += (a - b) * (a - b)
            if d2 < best_d2:
                best_d2 = d2
                best_label = lab
        return best_label, best_d2 ** 0.5

# ---------------- Binary masks (no libs) ----------------
def _blank(N): return [[0]*N for _ in range(N)]

def _circle(N, r=None):
    if r is None: r = int(0.35*N)
    cx = cy = N//2; r2 = r*r
    M = _blank(N)
    for y in range(N):
        dy = y - cy
        for x in range(N):
            dx = x - cx
            if dx*dx + dy*dy <= r2:
                M[y][x] = 1
    return M

def _square(N, side=None):
    if side is None: side = int(0.6*N)
    M = _blank(N); y0 = (N - side)//2; x0 = (N - side)//2
    for y in range(y0, y0+side):
        for x in range(x0, x0+side):
            M[y][x] = 1
    return M

def _rectangle(N, w_ratio=0.60, h_ratio=0.30):
    # วาดผืนผ้า กว้าง=W, สูง=H โดยอิงสัดส่วนกับ N และจัดกลางภาพ
    W = max(1, int(w_ratio*N)); H = max(1, int(h_ratio*N))
    M = _blank(N); y0 = (N - H)//2; x0 = (N - W)//2
    for y in range(y0, y0+H):
        for x in range(x0, x0+W):
            M[y][x] = 1
    return M

def _triangle_equilateral(N, side=None):
    if side is None: side = int(0.7*N)
    import math
    M = _blank(N)
    cx = N//2
    y_top = (N - side)//2
    h = int(round(side*math.sqrt(3)/2))
    y_base = min(N-1, y_top + h)
    for y in range(y_top, y_base+1):
        t = (y - y_top) / max(1, (y_base - y_top))
        half = int(round((1 - t) * (side//2)))
        xL = max(0, cx - half); xR = min(N-1, cx + half)
        for x in range(xL, xR+1):
            M[y][x] = 1
    return M

# ---------------- Build classifier (base + many) ----------------
def build_default_classifier():
    N = 200
    clf = SimpleNN()

    # helper: ลดการเขียนซ้ำ
    def add_proto(label, mask):
        clf.add(label, extract_features(mask))

    # ==== ชุดเดิม (คงไว้) ====
    add_proto("เหรียญ/ลูกบอล",           _circle(N))                  # วงกลม
    add_proto("กล่อง/หน้าต่าง",           _square(N))                  # สี่เหลี่ยมจัตุรัส (1:1)
    add_proto("ป้ายเตือน/สามเหลี่ยม",     _triangle_equilateral(N))    # สามเหลี่ยมด้านเท่า
    add_proto("มือถือ",                  _rectangle(N, 0.60, 0.30))    # ≈ 2.00 : 1 (ผอมยาว)
    add_proto("นามบัตร/การ์ด",            _rectangle(N, 0.64, 0.40))    # ≈ 1.60 : 1

    # ==== เพิ่มแบบเยอะ ๆ (โปรโตฯ เดี่ยว/คลาส) ====
    # วงกลม
    add_proto("นาฬิกาแขวน",               _circle(N))
    add_proto("ฝาขวด/กระดุมใหญ่",          _circle(N))
    add_proto("ล้อรถ/จานดาวเทียม",         _circle(N))

    # สี่เหลี่ยมจัตุรัส
    add_proto("ไอคอนแอป/สติ๊กกี้โน้ต",     _square(N))
    add_proto("จอสมาร์ตวอทช์",            _square(N))

    # สามเหลี่ยม
    add_proto("ปุ่มเล่นสื่อ (Play)",       _triangle_equilateral(N))
    add_proto("ป้ายระวังงานก่อสร้าง",      _triangle_equilateral(N))

    # มือถือ/การ์ด (สัดส่วนใกล้เคียง)
    add_proto("มือถือจอใหญ่",              _rectangle(N, 0.62, 0.30))   # ≈2.07:1
    add_proto("มือถือจอเล็ก",              _rectangle(N, 0.58, 0.31))   # ≈1.87:1
    add_proto("การ์ดสมาชิก/ATM",           _rectangle(N, 0.66, 0.41))   # ≈1.61:1

    # แท็บเล็ต/อีบุ๊ก
    add_proto("แท็บเล็ต (4:3 แนวตั้ง)",     _rectangle(N, 0.56, 0.42))   # ≈1.33:1
    add_proto("แท็บเล็ต (4:3 แนวนอน)",     _rectangle(N, 0.68, 0.51))   # ≈1.33:1
    add_proto("อีรีดเดอร์",                _rectangle(N, 0.54, 0.44))   # ≈1.23:1

    # โน้ตบุ๊ค/จอคอม
    add_proto("โน้ตบุ๊ค (16:9)",          _rectangle(N, 0.70, 0.39))   # ≈1.79:1
    add_proto("โน้ตบุ๊ค (3:2)",           _rectangle(N, 0.66, 0.44))   # ≈1.50:1
    add_proto("โน้ตบุ๊ค (16:10)",         _rectangle(N, 0.68, 0.42))   # ≈1.62:1
    add_proto("จอคอม 21:9",               _rectangle(N, 0.78, 0.33))   # ≈2.36:1
    add_proto("จอคอม 32:9",               _rectangle(N, 0.82, 0.28))   # ≈2.93:1
    add_proto("จอคอม 5:4",                _rectangle(N, 0.62, 0.50))   # ≈1.24:1
    add_proto("จอคอม 4:3",                _rectangle(N, 0.64, 0.48))   # ≈1.33:1

    # ทีวี/ป้ายดิจิทัล
    add_proto("ทีวี 16:9",                 _rectangle(N, 0.74, 0.42))   # ≈1.76:1
    add_proto("ทีวี 4:3",                  _rectangle(N, 0.60, 0.45))   # ≈1.33:1
    add_proto("ป้ายดิจิทัลแนวตั้ง",         _rectangle(N, 0.36, 0.64))   # ≈0.56:1
    add_proto("ป้ายดิจิทัลแนวนอน",         _rectangle(N, 0.80, 0.30))   # ≈2.67:1
    add_proto("ป้ายบิลบอร์ด",              _rectangle(N, 0.82, 0.34))   # ≈2.41:1
    add_proto("แบนเนอร์เว็บไซต์",           _rectangle(N, 0.88, 0.26))   # ≈3.38:1

    # เอกสาร/หนังสือ
    add_proto("กระดาษ A4 แนวตั้ง",          _rectangle(N, 0.60, 0.42))   # ≈1.43:1
    add_proto("กระดาษ A4 แนวนอน",          _rectangle(N, 0.75, 0.35))   # ≈2.14:1
    add_proto("หนังสือเล่ม (แนวตั้ง)",      _rectangle(N, 0.50, 0.60))   # ≈0.83:1
    add_proto("โปสเตอร์แนวตั้ง",            _rectangle(N, 0.48, 0.72))   # ≈0.67:1
    add_proto("โปสเตอร์แนวนอน",            _rectangle(N, 0.84, 0.40))   # ≈2.10:1
    add_proto("ซองจดหมาย DL",              _rectangle(N, 0.80, 0.30))   # ≈2.67:1

    # อุปกรณ์อิเล็กฯ/ของใช้
    add_proto("คีย์บอร์ด",                  _rectangle(N, 0.80, 0.28))   # ≈2.86:1
    add_proto("เมาส์แพด",                   _rectangle(N, 0.60, 0.50))   # ≈1.20:1
    add_proto("รีโมตทีวี",                  _rectangle(N, 0.42, 0.74))   # ≈0.57:1
    add_proto("กล่องรับสัญญาณ",             _rectangle(N, 0.58, 0.32))   # ≈1.81:1
    add_proto("เพาเวอร์แบงก์",              _rectangle(N, 0.52, 0.38))   # ≈1.37:1

    # วัตถุสำนักงาน
    add_proto("กระดานไวท์บอร์ด",            _rectangle(N, 0.82, 0.36))   # ≈2.28:1
    add_proto("แฟ้มเอกสาร",                 _rectangle(N, 0.56, 0.46))   # ≈1.22:1
    add_proto("สมุดโน้ตพ็อกเก็ต",           _rectangle(N, 0.45, 0.62))   # ≈0.73:1

    # อุปกรณ์ภาพ/วิดีโอ
    add_proto("เฟรมรูปภาพ 3:2",             _rectangle(N, 0.66, 0.44))   # ≈1.50:1
    add_proto("เฟรมรูปภาพ 1:1",             _square(N))                  # 1:1
    add_proto("เฟรมพาโนรามา",               _rectangle(N, 0.88, 0.30))   # ≈2.93:1

    # ป้าย/บัตร/แท็ก
    add_proto("บัตรนักศึกษา",               _rectangle(N, 0.64, 0.40))   # ≈1.60:1
    add_proto("บัตรโดยสาร",                 _rectangle(N, 0.62, 0.38))   # ≈1.63:1
    add_proto("แท็กสินค้า",                  _rectangle(N, 0.50, 0.30))   # ≈1.67:1

    # แผงวงจร/อุปกรณ์งานช่าง
    add_proto("บอร์ดไมโครคอนโทรลเลอร์",     _rectangle(N, 0.70, 0.38))   # ≈1.84:1
    add_proto("ชุดรีเลย์/เพาเวอร์ซัพพลาย",   _rectangle(N, 0.62, 0.36))   # ≈1.72:1

    return clf
