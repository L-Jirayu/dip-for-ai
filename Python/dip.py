# dip.py
# ใช้ Pillow เฉพาะงาน I/O (อ่าน/เขียนภาพ), ส่วนอัลกอริทึม DIP/คณิต ทำเองล้วน
import math
from PIL import Image  # pip install pillow
import matplotlib.pyplot as plt  # pip install matplotlib
import os

# ---------------- Image I/O (read → grayscale 0..255) ----------------
def read_image_grayscale(path):
    with Image.open(path) as im:
        im = im.convert("L")
        w, h = im.size
        pix = list(im.getdata())  # flat list len = w*h
    gray = [pix[i*w:(i+1)*w] for i in range(h)]
    return gray

# ---------------- PNG writers (save .png) ----------------
def save_png_gray(path, gray):
    h, w = len(gray), len(gray[0])
    im = Image.new("L", (w, h))
    data = []
    for y in range(h):
        for x in range(w):
            v = gray[y][x]
            if v < 0: v = 0
            if v > 255: v = 255
            data.append(int(v))
    im.putdata(data)
    im.save(path, format="PNG")

def save_png_rgb(path, rgb):
    h, w = len(rgb), len(rgb[0])
    im = Image.new("RGB", (w, h))
    data = []
    for y in range(h):
        for x in range(w):
            r, g, b = rgb[y][x]
            r = 0 if r < 0 else (255 if r > 255 else int(r))
            g = 0 if g < 0 else (255 if g > 255 else int(g))
            b = 0 if b < 0 else (255 if b > 255 else int(b))
            data.append((r, g, b))
    im.putdata(data)
    im.save(path, format="PNG")

# ---------------- Drawing helpers ----------------
def gray_to_rgb(gray):
    h, w = len(gray), len(gray[0])
    out = [[[0,0,0] for _ in range(w)] for __ in range(h)]
    for y in range(h):
        for x in range(w):
            v = gray[y][x]
            if v < 0: v = 0
            if v > 255: v = 255
            out[y][x] = [int(v), int(v), int(v)]
    return out

def draw_cross(rgb, x, y, size, color, thickness=5):
    h = len(rgb)
    w = len(rgb[0]) if h > 0 else 0
    xi, yi = int(round(x)), int(round(y))
    half_t = max(1, thickness // 2)

    for dx in range(-size, size + 1):
        xx = xi + dx
        for ty in range(-half_t, half_t + 1):
            yy = yi + ty
            if 0 <= xx < w and 0 <= yy < h:
                rgb[yy][xx] = list(color)

    for dy in range(-size, size + 1):
        yy = yi + dy
        for tx in range(-half_t, half_t + 1):
            xx = xi + tx
            if 0 <= xx < w and 0 <= yy < h:
                rgb[yy][xx] = list(color)

def draw_line(rgb, x0, y0, x1, y1, color, thickness=4):
    h = len(rgb)
    w = len(rgb[0]) if h > 0 else 0
    x0 = int(round(x0)); y0 = int(round(y0))
    x1 = int(round(x1)); y1 = int(round(y1))
    dx = abs(x1 - x0); dy = -abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx + dy
    half_t = max(1, thickness // 2)

    def stamp(xx, yy):
        for oy in range(-half_t, half_t + 1):
            ry = yy + oy
            if 0 <= ry < h:
                for ox in range(-half_t, half_t + 1):
                    rx = xx + ox
                    if 0 <= rx < w:
                        rgb[ry][rx] = list(color)

    while True:
        if 0 <= x0 < w and 0 <= y0 < h:
            stamp(x0, y0)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy; x0 += sx
        if e2 <= dx:
            err += dx; y0 += sy

# ---------------- DIP core ----------------
def rgb_to_grayscale(img):
    if isinstance(img[0][0], int):
        return img
    h = len(img); w = len(img[0])
    out = [[0]*w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            r, g, b = img[y][x]
            yv = int(round(0.299*r + 0.587*g + 0.114*b))
            out[y][x] = 0 if yv < 0 else (255 if yv > 255 else yv)
    return out

def histogram_256(img):
    h = [0]*256
    for row in img:
        for v in row:
            if v < 0: v = 0
            if v > 255: v = 255
            h[v] += 1
    return h

def histogram_equalize(img):
    hst = histogram_256(img)
    total = sum(hst)
    cdf = [0]*256
    c = 0
    for i in range(256):
        c += hst[i]; cdf[i] = c
    cdf_min = next((cdf[i] for i in range(256) if cdf[i] > 0), 0)
    if total <= 1 or cdf_min == total:
        return [row[:] for row in img]
    H, W = len(img), len(img[0])
    out = [[0]*W for _ in range(H)]
    denom = total - cdf_min
    for y in range(H):
        for x in range(W):
            v = img[y][x]
            eq = round((cdf[v] - cdf_min) / denom * 255)
            eq = 0 if eq < 0 else (255 if eq > 255 else eq)
            out[y][x] = int(eq)
    return out

# ---------------- Histogram / CDF / Equalization plotting ----------------
HE_GRAPH_DIR = "he_graph"

def _ensure_he_graph_dir():
    if not os.path.exists(HE_GRAPH_DIR):
        os.makedirs(HE_GRAPH_DIR)

def plot_histogram(img, filename="histogram.png"):
    _ensure_he_graph_dir()
    hst = histogram_256(img)
    plt.figure()
    plt.bar(range(256), hst, color='gray')
    plt.title("Histogram")
    plt.xlabel("Pixel Value")
    plt.ylabel("Count")
    plt.savefig(os.path.join(HE_GRAPH_DIR, filename))
    plt.close()

def plot_cdf(img, filename="cdf.png"):
    _ensure_he_graph_dir()
    hst = histogram_256(img)
    total = sum(hst)
    cdf = [sum(hst[:i+1])/total for i in range(256)]
    plt.figure()
    plt.plot(range(256), cdf, color='blue')
    plt.title("CDF")
    plt.xlabel("Pixel Value")
    plt.ylabel("CDF")
    plt.grid(True)
    plt.savefig(os.path.join(HE_GRAPH_DIR, filename))
    plt.close()

def plot_histogram_equalization(img, filename="histogram_equalization.png"):
    _ensure_he_graph_dir()
    eq = histogram_equalize(img)
    
    # สร้าง figure ใหม่ พร้อม title ที่ถูกต้อง
    hst = histogram_256(eq)
    plt.figure()
    plt.bar(range(256), hst, color='gray')
    plt.title("Histogram Equalization")   # <-- แก้ตรงนี้
    plt.xlabel("Pixel Value")
    plt.ylabel("Count")
    plt.savefig(os.path.join(HE_GRAPH_DIR, filename))
    plt.close()


def threshold_binary(img, T):
    H, W = len(img), len(img[0])
    B = [[0]*W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            B[y][x] = 1 if img[y][x] > T else 0
    return B

def size_area(mask):
    return sum(sum(row) for row in mask)

def centroid(mask):
    H, W = len(mask), len(mask[0])
    A = 0; sx = 0.0; sy = 0.0
    for y in range(H):
        for x in range(W):
            if mask[y][x]:
                A += 1; sx += x; sy += y
    return (sx/A, sy/A) if A > 0 else (0.0, 0.0)

def raw_moment(mask, p, q):
    H, W = len(mask), len(mask[0])
    s = 0.0
    for y in range(H):
        for x in range(W):
            if mask[y][x]:
                s += (x**p) * (y**q)
    return s

def central_moment(mask, p, q, xc, yc):
    H, W = len(mask), len(mask[0])
    s = 0.0
    for y in range(H):
        for x in range(W):
            if mask[y][x]:
                s += ((x - xc)**p) * ((y - yc)**q)
    return s

def _norm_central(mask, p, q, mu00, xc, yc):
    mu = central_moment(mask, p, q, xc, yc)
    gamma = 1.0 + (p + q)/2.0
    return 0.0 if mu00 == 0 else mu / (mu00 ** gamma)

def hu_moments(mask):
    m00 = raw_moment(mask, 0, 0)
    if m00 == 0:
        return [0.0]*7
    m10 = raw_moment(mask, 1, 0)
    m01 = raw_moment(mask, 0, 1)
    xc = m10 / m00
    yc = m01 / m00
    eta20 = _norm_central(mask, 2, 0, m00, xc, yc)
    eta02 = _norm_central(mask, 0, 2, m00, xc, yc)
    eta11 = _norm_central(mask, 1, 1, m00, xc, yc)
    eta30 = _norm_central(mask, 3, 0, m00, xc, yc)
    eta12 = _norm_central(mask, 1, 2, m00, xc, yc)
    eta21 = _norm_central(mask, 2, 1, m00, xc, yc)
    eta03 = _norm_central(mask, 0, 3, m00, xc, yc)
    phi1 = eta20 + eta02
    phi2 = (eta20 - eta02)**2 + 4*(eta11**2)
    phi3 = (eta30 - 3*eta12)**2 + (3*eta21 - eta03)**2
    phi4 = (eta30 + eta12)**2 + (eta21 + eta03)**2
    phi5 = ((eta30 - 3*eta12)*(eta30 + eta12)*((eta30 + eta12)**2 - 3*(eta21 + eta03)**2)
           + (3*eta21 - eta03)*(eta21 + eta03)*(3*(eta30 + eta12)**2 - (eta21 + eta03)**2))
    phi6 = ((eta20 - eta02)*((eta30 + eta12)**2 - (eta21 + eta03)**2)
           + 4*eta11*(eta30 + eta12)*(eta21 + eta03))
    phi7 = ((3*eta21 - eta03)*(eta30 + eta12)*((eta30 + eta12)**2 - 3*(eta21 + eta03)**2)
           - (eta30 - 3*eta12)*(eta21 + eta03)*(3*(eta30 + eta12)**2 - (eta21 + eta03)**2))
    return [phi1, phi2, phi3, phi4, phi5, phi6, phi7]

# ---------------- Visualization (.png outputs) ----------------
def _principal_axis(mask, xc, yc):
    m00 = raw_moment(mask, 0, 0)
    if m00 == 0:
        return (xc, yc, xc, yc)
    mu20 = central_moment(mask, 2, 0, xc, yc)
    mu02 = central_moment(mask, 0, 2, xc, yc)
    mu11 = central_moment(mask, 1, 1, xc, yc)
    angle = 0.5 * math.atan2(2*mu11, (mu20 - mu02) if (mu20 - mu02) != 0 else 1e-12)
    H, W = len(mask), len(mask[0])
    L = max(W, H) * 0.4
    x0 = xc - L*math.cos(angle); y0 = yc - L*math.sin(angle)
    x1 = xc + L*math.cos(angle); y1 = yc + L*math.sin(angle)
    return (x0, y0, x1, y1)

def save_visualizations_png(gray, eq, mask, centroid_raw, centroid_for_central):
    save_png_gray("out_gray.png", gray)
    save_png_gray("out_equalized.png", eq)

    H, W = len(mask), len(mask[0])
    thresh = [[255 if mask[y][x] else 0 for x in range(W)] for y in range(H)]
    save_png_gray("out_threshold.png", thresh)

    base = gray_to_rgb(thresh)
    cx_raw, cy_raw = centroid_raw
    cx_c, cy_c = centroid_for_central
    draw_cross(base, cx_raw, cy_raw, size=10, color=(255, 0, 0), thickness=108)
    draw_cross(base, cx_c,  cy_c,  size=8,  color=(0, 170, 255), thickness=108)
    x0, y0, x1, y1 = _principal_axis(mask, cx_c, cy_c)
    draw_line(base, x0, y0, x1, y1, color=(0, 255, 0), thickness=12)
    save_png_rgb("out_overlay.png", base)

# ---------------- Hu-based shape detection ----------------
def _hu_log_abs(vec):
    out = []
    for v in vec:
        av = abs(v)
        out.append(0.0 if av < 1e-30 else -math.log10(av))
    return out

def _blank(N): return [[0]*N for _ in range(N)]

def _circle_mask(N, r=None):
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

def _square_mask(N, side=None):
    if side is None: side = int(0.6*N)
    M = _blank(N); y0 = (N - side)//2; x0 = (N - side)//2
    for y in range(y0, y0+side):
        for x in range(x0, x0+side):
            M[y][x] = 1
    return M

def _rect_mask(N, w_ratio=0.7, h_ratio=0.35):
    W = max(1, int(w_ratio*N)); H = max(1, int(h_ratio*N))
    M = _blank(N); y0 = (N - H)//2; x0 = (N - W)//2
    for y in range(y0, y0+H):
        for x in range(x0, x0+W):
            M[y][x] = 1
    return M

def _tri_equilateral_mask(N, side=None):
    if side is None: side = int(0.7*N)
    M = _blank(N)
    cx = N//2
    y_top = (N - side)//2
    h = int(round(side*3**0.5/2))
    y_base = min(N-1, y_top + h)
    for y in range(y_top, y_base+1):
        t = (y - y_top) / max(1, (y_base - y_top))
        half = int(round((1 - t) * (side//2)))
        xL = max(0, cx - half); xR = min(N-1, cx + half)
        for x in range(xL, xR+1):
            M[y][x] = 1
    return M

def _build_hu_templates(N=200):
    templ = {}
    for name, M in [
        ("Circle",    _circle_mask(N)),
        ("Square",    _square_mask(N)),
        ("Rectangle", _rect_mask(N, 0.7, 0.35)),
        ("Triangle",  _tri_equilateral_mask(N)),
    ]:
        templ[name] = _hu_log_abs(hu_moments(M))
    return templ

def detect_shape_by_hu(mask, templates=None):
    if templates is None:
        templates = _build_hu_templates(200)
    v = _hu_log_abs(hu_moments(mask))
    best_name, best_d = None, 1e18
    for name, t in templates.items():
        d = sum((a - b)**2 for a, b in zip(v, t))
        if d < best_d:
            best_d, best_name = d, name
    return best_name, math.sqrt(best_d)


