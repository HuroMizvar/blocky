import random, sys
from collections import Counter
import train as T
from vocab import V
random.seed(1)
# 1) neznáme produkty: 20 % základných názvov vynecháme z tréningu
held = {c: set(random.sample(v, max(1, len(v)//5))) for c, v in V.items()}
orig = {c: list(v) for c, v in V.items()}
for c in V: V[c] = [x for x in orig[c] if x not in held[c]]
data = T.build_train()
m = T.train_nb(data)
for c in V: V[c] = orig[c]
test = []
for c, hs in held.items():
    for h in hs:
        for _ in range(3): test.append((T.variant(h, c), c))
ok = 0; none = 0; wrong = Counter()
for thr in [0.0, 0.6, 0.9]:
    ok = none = 0
    for n, g in test:
        p, conf = T.predict(m, n)
        if p is None or conf < thr: none += 1
        elif p == g: ok += 1
    print(f"neznáme produkty thr={thr}: správne {ok}/{len(test)}={ok/len(test):.1%}  nezaradené {none}  zle {len(test)-ok-none}")
# 2) nezmysly
junk = ["DARCEK.KARTA 20EUR","XYZ 12345","ART.C.4471","TOVAR 1","POLOZKA","SLUZBA C.5","VOUCHER","KUPON ZLAVA","DPH","ZAOKRUHLENIE"]
for j in junk: print(j, T.predict(m, j))
