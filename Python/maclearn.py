# maclearn.py — Hu-only + Pure Python DecisionTree + RandomForest
from math import sqrt, log10
from dip import hu_moments
import numpy as np
import random
from collections import Counter, defaultdict

# ---------------------------------------------------------
# Feature
# ---------------------------------------------------------
def _hu_log_abs(vec):
    out = []
    for v in vec:
        av = abs(v)
        out.append(0.0 if av < 1e-30 else -log10(av))
    return out

def extract_features(mask):
    return _hu_log_abs(hu_moments(mask))

# ---------------------------------------------------------
# Pure Python Decision Tree (Gini)
# ---------------------------------------------------------
class PureDecisionTree:
    def __init__(self, max_depth=None, min_samples_split=2):
        self.max_depth = max_depth
        self.min_samples_split = min_samples_split
        self.tree = None
        self.classes_ = None

    # ---------------------- Utility ----------------------
    def _gini(self, labels):
        total = len(labels)
        cnt = Counter(labels)
        return 1.0 - sum((c/total)**2 for c in cnt.values())

    def _best_split(self, X, y):
        n_samples, n_features = len(X), len(X[0])
        best_feat, best_thresh, best_gain = None, None, 0.0
        parent_gini = self._gini(y)

        for f in range(n_features):
            values = [x[f] for x in X]
            thresholds = sorted(set(values))

            for t in thresholds:
                left_y  = [y[i] for i in range(n_samples) if X[i][f] <= t]
                right_y = [y[i] for i in range(n_samples) if X[i][f] >  t]

                if len(left_y) == 0 or len(right_y) == 0:
                    continue

                g_left  = self._gini(left_y)
                g_right = self._gini(right_y)

                total = len(y)
                g_split = (len(left_y)/total)*g_left + (len(right_y)/total)*g_right
                gain = parent_gini - g_split

                if gain > best_gain:
                    best_gain = gain
                    best_feat = f
                    best_thresh = t

        return best_feat, best_thresh, best_gain

    # ---------------------- Build Tree ----------------------
    def _build(self, X, y, depth):
        # Stop conditions
        if len(set(y)) == 1:
            return ("leaf", y[0])

        if self.max_depth is not None and depth >= self.max_depth:
            return ("leaf", Counter(y).most_common(1)[0][0])

        if len(y) < self.min_samples_split:
            return ("leaf", Counter(y).most_common(1)[0][0])

        feat, thresh, gain = self._best_split(X, y)
        if feat is None or gain <= 1e-12:
            return ("leaf", Counter(y).most_common(1)[0][0])

        # Partition
        left_X, left_y, right_X, right_y = [], [], [], []
        for i in range(len(X)):
            if X[i][feat] <= thresh:
                left_X.append(X[i])
                left_y.append(y[i])
            else:
                right_X.append(X[i])
                right_y.append(y[i])

        return {
            "type": "node",
            "feat": feat,
            "thresh": thresh,
            "left": self._build(left_X, left_y, depth+1),
            "right": self._build(right_X, right_y, depth+1)
        }

    # ---------------------- Public API ----------------------
    def fit(self, X, y):
        self.classes_ = sorted(set(y))
        self.tree = self._build(X, y, 0)
        return self

    def _predict_one(self, x, node):
        # leaf node
        if isinstance(node, tuple) and node[0] == "leaf":
            return node[1]

        # internal node
        f = node["feat"]
        t = node["thresh"]
        if x[f] <= t:
            return self._predict_one(x, node["left"])
        return self._predict_one(x, node["right"])


    def predict(self, X):
        return [self._predict_one(x, self.tree) for x in X]

    def predict_proba(self, X):
        preds = self.predict(X)
        probs = []
        for p in preds:
            v = [0.0]*len(self.classes_)
            idx = self.classes_.index(p)
            v[idx] = 1.0
            probs.append(v)
        return probs


# ---------------------------------------------------------
# Pure Python Random Forest
# ---------------------------------------------------------
class PureRandomForest:
    def __init__(self, n_estimators=50, max_depth=None, sample_ratio=0.8):
        self.n_estimators = n_estimators
        self.max_depth = max_depth
        self.sample_ratio = sample_ratio
        self.trees = []
        self.classes_ = None

    def fit(self, X, y):
        self.classes_ = sorted(set(y))
        n_samples = len(X)

        for _ in range(self.n_estimators):
            idxs = np.random.choice(n_samples, int(n_samples*self.sample_ratio), replace=True)
            Xs = [X[i] for i in idxs]
            ys = [y[i] for i in idxs]

            tree = PureDecisionTree(max_depth=self.max_depth)
            tree.fit(Xs, ys)
            self.trees.append(tree)

        return self

    def predict(self, X):
        all_preds = []
        for tree in self.trees:
            pred = tree.predict(X)
            all_preds.append(pred)

        final = []
        for i in range(len(X)):
            votes = [all_preds[t][i] for t in range(self.n_estimators)]
            final.append(Counter(votes).most_common(1)[0][0])
        return final

    def predict_proba(self, X):
        all_preds = []
        for tree in self.trees:
            pred = tree.predict(X)
            all_preds.append(pred)

        probs = []
        for i in range(len(X)):
            votes = Counter([all_preds[t][i] for t in range(self.n_estimators)])
            total = sum(votes.values())
            row = []
            for c in self.classes_:
                row.append(votes[c] / total)
            probs.append(row)
        return probs


# ---------------------------------------------------------
# Build Mock Dataset
# ---------------------------------------------------------
def _blank(N): return [[0]*N for _ in range(N)]

def _circle(N, r=None):
    if r is None: r = int(0.35*N)
    cx = cy = N//2; r2 = r*r
    M = _blank(N)
    for y in range(N):
        for x in range(N):
            dx = x-cx; dy = y-cy
            if dx*dx+dy*dy <= r2: M[y][x] = 1
    return M

def _square(N, side=None):
    if side is None: side = int(0.6*N)
    M = _blank(N)
    y0 = x0 = (N-side)//2
    for y in range(y0, y0+side):
        for x in range(x0, x0+side):
            M[y][x] = 1
    return M

def _rectangle(N, wr=0.6, hr=0.3):
    W = int(wr*N); H = int(hr*N)
    M = _blank(N)
    y0 = (N-H)//2; x0 = (N-W)//2
    for y in range(y0, y0+H):
        for x in range(x0, x0+W):
            M[y][x] = 1
    return M

def _triangle(N, side=None):
    import math
    if side is None: side = int(0.7*N)
    M = _blank(N)
    cx = N//2; y_top = (N-side)//2
    h = int(round(side*math.sqrt(3)/2))
    y_base = y_top + h

    for y in range(y_top, y_base+1):
        t = (y - y_top)/max(1, (y_base-y_top))
        half = int(round((1-t)*(side//2)))
        xL = cx-half; xR = cx+half
        for x in range(xL, xR+1):
            M[y][x] = 1
    return M


# [เพิ่มส่วนนี้ใน maclearn.py ต่อท้ายหรือไว้ก่อน train_ml ก็ได้]

def k_fold_cross_validation(model_class, X, y, k=5, model_params=None):
    """
    ทำ K-Fold แบบ Pure Python
    model_class: ชื่อ class ของโมเดล (เช่น PureDecisionTree)
    model_params: dict พารามิเตอร์ของโมเดล (เช่น {'max_depth': 10})
    """
    if model_params is None: model_params = {}
    
    # 1. รวม X, y แล้วสลับ (Shuffle)
    data = list(zip(X, y))
    random.shuffle(data)
    
    n_samples = len(data)
    fold_size = n_samples // k
    
    accuracies = []
    print(f"\n--- Starting {k}-Fold Cross Validation ---")
    
    for i in range(k):
        # 2. แบ่งข้อมูล (Slicing)
        start = i * fold_size
        end = (i + 1) * fold_size
        
        # ก้อนที่เป็น Test (ข้อสอบ)
        test_data = data[start:end]
        # ก้อนที่เป็น Train (เอาส่วนหน้า + ส่วนหลัง มารวมกัน)
        train_data = data[:start] + data[end:]
        
        # แยกกลับเป็น X, y
        X_train = [d[0] for d in train_data]
        y_train = [d[1] for d in train_data]
        X_test  = [d[0] for d in test_data]
        y_test  = [d[1] for d in test_data]
        
        # 3. สร้างและเทรนโมเดลใหม่
        model = model_class(**model_params)
        model.fit(X_train, y_train)
        
        # 4. วัดผล
        preds = model.predict(X_test)
        correct = sum(1 for p, t in zip(preds, y_test) if p == t)
        acc = (correct / len(y_test)) * 100 if len(y_test) > 0 else 0
        
        accuracies.append(acc)
        print(f"Fold {i+1}/{k}: Accuracy = {acc:.2f}% (Train={len(X_train)}, Test={len(X_test)})")
        
    avg_acc = sum(accuracies) / k
    print(f"Average Accuracy: {avg_acc:.2f}%")
    print("---------------------------------------")
    return avg_acc

# ---------------------------------------------------------
# แก้ไข build_mock_dataset อันเดิม ให้มี Loop (Data Augmentation)
# ไม่งั้น K-Fold จะพังเพราะข้อมูลไม่พอ
# ---------------------------------------------------------
def build_mock_dataset():
    N = 200
    X = []
    y = []

    def add(label, mask):
        try:
            feat = extract_features(mask)
            # กรองค่า NaN หรือ infinity
            if all(isinstance(f, (int, float)) for f in feat):
                X.append(feat)
                y.append(label)
        except: pass

    # ปั๊มข้อมูลอย่างละ 20-30 ตัว เพื่อให้ K-Fold มีของให้เล่น
    # (ลดจำนวนลงหน่อยจะได้รันเร็วขึ้น)
    for _ in range(25):
        r = random.randint(20, 90)
        add("Circle", _circle(N, r=r))

    for _ in range(25):
        s = random.randint(30, 150)
        add("Square", _square(N, side=s))

    for _ in range(25):
        s = random.randint(40, 160)
        add("Triangle", _triangle(N, side=s))

    for _ in range(25):
        w = random.randint(40, 160)
        h = random.randint(30, 100)
        if abs(w - h) < 30: w += 40 # บังคับให้เป็นผืนผ้าชัดๆ
        add("Rectangle", _rectangle(N, wr=w/N, hr=h/N))

    return X, y


# ---------------------------------------------------------
# Train Models
# ---------------------------------------------------------
def train_ml():
    X, y = build_mock_dataset()

    dt = PureDecisionTree(max_depth=10)
    dt.fit(X, y)

    rf = PureRandomForest(n_estimators=50, max_depth=10)
    rf.fit(X, y)

    return dt, rf


# ---------------------------------------------------------
# Predict
# ---------------------------------------------------------
def predict_with_ml(mask, clf):
    feat = [extract_features(mask)]
    label = clf.predict(feat)[0]

    if hasattr(clf, "predict_proba"):
        proba = clf.predict_proba(feat)[0]
        idx = clf.classes_.index(label)
        p = proba[idx] * 100
    else:
        p = 100.0

    return label, p
