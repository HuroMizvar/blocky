# -*- coding: utf-8 -*-
import json, math, random, re, sys, unicodedata
from collections import defaultdict, Counter
from vocab import V, STORE_BRANDS, UNITS
from testset import TEST

random.seed(42)

# ---------- normalizácia a príznaky (MUSÍ sa zhodovať s Kotlin ItemClassifier) ----------
def normalize(s):
    s = unicodedata.normalize("NFD", s)
    s = "".join(ch for ch in s if unicodedata.category(ch) != "Mn")
    s = s.lower()
    s = re.sub(r"[^a-z0-9%., ]", " ", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s

STOP = {"ks", "kg", "g", "l", "ml", "bal", "x", "pet", "plech", "fl", "cca", "vaz", "sk", "pack", "the", "and",
        "s", "a", "z", "na", "do", "pre", "v", "so", "od", "zo", "k"}
VOW = set("aeiouy")

def tokens(n):
    out = []
    n = re.sub(r"([a-z])([0-9])", r"\1 \2", n)
    n = re.sub(r"([0-9])([a-z])", r"\1 \2", n)
    for t in re.split(r"[ .,%]+", n):
        if len(t) < 2 or any(c.isdigit() for c in t) or t in STOP:
            continue
        out.append(t)
    return out

def features(name):
    n = normalize(name)
    f = set()
    toks = tokens(n)
    for t in toks:
        f.add("w:" + t)
        for k in range(3, min(6, len(t)) + 1):
            f.add("p:" + t[:k])
        cs = t[0] + "".join(c for c in t[1:] if c not in VOW)
        if len(cs) >= 2:
            f.add("c:" + cs[:4])
        b = "_" + t + "_"
        for i in range(len(b) - 2):
            f.add("g:" + b[i:i + 3])
    for a, b2 in zip(toks, toks[1:]):
        f.add("b:" + a[:4] + "_" + b2[:4])
    return f

# ---------- generovanie trénovacích dát ----------
def strip_diac(s):
    return "".join(ch for ch in unicodedata.normalize("NFD", s) if unicodedata.category(ch) != "Mn")

def abbrev_word(w):
    if len(w) <= 4 or not w.isalpha():
        return w
    r = random.random()
    if r < 0.45:
        return w[:random.randint(3, min(6, len(w) - 1))] + "."
    if r < 0.65:
        cs = w[0] + "".join(c for c in w[1:] if c.lower() not in "aeiouyáéíóúýäô")
        return cs[:random.randint(3, 5)] + ("." if random.random() < 0.5 else "")
    return w

def variant(base, cat):
    words = base.split()
    style = random.random()
    if style < 0.25:          # prirodzený zápis
        s = base[0].upper() + base[1:]
    elif style < 0.5:         # veľké bez diakritiky
        s = strip_diac(base).upper()
    elif style < 0.85:        # skratky
        ws = [abbrev_word(w) if i > 0 or random.random() < 0.5 else w for i, w in enumerate(strip_diac(base).split())]
        sep = "" if random.random() < 0.4 else " "
        s = ""
        for w in ws:
            s += w if (s == "" or s.endswith(".") and sep == "") else (" " + w)
        s = s.upper()
    else:                     # len prvé slovo(á)
        s = strip_diac(" ".join(words[:max(1, len(words) - 1)])).upper()
    if random.random() < 0.15:
        s = random.choice(STORE_BRANDS) + " " + s
    if random.random() < 0.6:
        u = random.choice(UNITS.get(cat, [""]))
        if u:
            s = s + " " + (u.upper() if s.isupper() else u)
    return s

def build_train(per_item=30):
    data = []
    for cat, items in V.items():
        k = max(per_item, int(per_item * 60 / max(1, len(items))))   # malé kategórie dostanú viac variantov
        for base in items:
            data.append((base, cat))
            for _ in range(k):
                data.append((variant(base, cat), cat))
    return data

# ---------- Naive Bayes ----------
def train_nb(data, min_count=2):
    classes = sorted(V.keys())
    ci = {c: i for i, c in enumerate(classes)}
    docs = Counter()
    fc = defaultdict(lambda: [0] * len(classes))
    for name, cat in data:
        docs[cat] += 1
        for f in features(name):
            fc[f][ci[cat]] += 1
    feats = {f: v for f, v in fc.items() if sum(v) >= min_count}
    return {"classes": classes, "docs": [docs[c] for c in classes], "feats": feats}

def predict(model, name, extra=None, alpha=0.3):
    classes = model["classes"]
    C = len(classes)
    feats = features(name)
    tot = [0.0] * C
    # súčet príznakov v triede (pre normalizáciu)
    if "_tot" not in model:
        t = [0] * C
        for v in model["feats"].values():
            for i in range(C):
                t[i] += v[i]
        model["_tot"] = t
        model["_V"] = len(model["feats"])
    T, Vn = model["_tot"], model["_V"]
    nd = sum(model["docs"])
    score = [math.log(model["docs"][i] / nd) * 0.0 for i in range(C)]  # rovnomerný prior
    known = 0
    for f in feats:
        v = model["feats"].get(f)
        if v is None:
            continue
        known += 1
        for i in range(C):
            score[i] += math.log((v[i] + alpha) / (T[i] + alpha * Vn))
    if known == 0:
        return None, 0.0
    m = max(score)
    ex = [math.exp(s - m) for s in score]
    z = sum(ex)
    best = max(range(C), key=lambda i: score[i])
    # podpora: koľko „silných“ príznakov (slovo, predpona, kostra) jednoznačne ukazuje na víťaznú triedu
    support = 0
    for f in feats:
        if f[0] == "g" or f[0] == "b" or (f[0] == "p" and len(f) < 6):
            continue
        v = model["feats"].get(f)
        if v is None:
            continue
        t = sum(v)
        if t >= 3 and v[best] / t >= 0.6:
            support += 1
    if support == 0:
        return None, 0.0
    return classes[best], ex[best] / z

# ---------- pravidlá z Kotlinu (pre porovnanie) ----------
def load_rules(path):
    src = open(path, encoding="utf-8").read()
    body = src[src.index("private val rules"):src.index("fun categorizeItem")]
    rules = []
    for m in re.finditer(r"Category\.(\w+) to listOf\((.*?)\)", body, re.S):
        keys = re.findall(r'"([^"]*)"', m.group(2))
        rules.append((m.group(1), keys))
    return rules

def by_rules(rules, name):
    padded = " " + normalize(name) + " "
    for cat, keys in rules:
        if any((" " + k) in padded for k in keys):
            return cat
    return None

if __name__ == "__main__":
    data = build_train()
    print("train examples", len(data))
    model = train_nb(data)
    print("features", len(model["feats"]))
    rules = load_rules(sys.argv[1])
    thr = float(sys.argv[2]) if len(sys.argv) > 2 else 0.5
    stats = Counter()
    errs = []
    for name, gold in TEST:
        r = by_rules(rules, name)
        p, conf = predict(model, name)
        ai = p if conf >= thr else None
        combo = r or ai
        aif = ai or r
        stats["aifirst"] += (aif == gold); stats["aifirst_none"] += (aif is None)
        stats["rules"] += (r == gold); stats["rules_none"] += (r is None)
        stats["ai"] += (ai == gold); stats["ai_none"] += (ai is None)
        stats["combo"] += (combo == gold); stats["combo_none"] += (combo is None)
        if combo != gold:
            errs.append((name, gold, r, p, round(conf, 2)))
    n = len(TEST)
    for k in ["rules", "ai", "combo", "aifirst"]:
        print(f"{k:6s} správne {stats[k]}/{n} = {stats[k]/n:.1%}   bez kategórie {stats[k+'_none']}")
    for e in errs:
        print("  ", e)
