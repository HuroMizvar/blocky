import json, sys
import train as T
from testset import TEST
data = T.build_train()
m = T.train_nb(data, min_count=2)
cls = m["classes"]
out = ["BLOCKY-NB 1", "\t".join(cls), "\t".join(str(d) for d in m["docs"])]
for f in sorted(m["feats"]):
    v = m["feats"][f]
    out.append(f + "\t" + ",".join(f"{i}:{c}" for i, c in enumerate(v) if c))
open(sys.argv[1], "w", encoding="utf-8").write("\n".join(out) + "\n")
# očakávané výsledky pre Kotlin test (rovnaký model)
exp = []
for name, gold in TEST:
    p, conf = T.predict(m, name)
    exp.append(f"{name}\t{p if (p and conf >= 0.6) else '-'}")
open(sys.argv[2], "w", encoding="utf-8").write("\n".join(exp) + "\n")
print(len(m["feats"]), "features")
