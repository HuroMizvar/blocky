# Skutočné položky používateľa (export z aplikácie) – správne kategórie (2 opravené podľa obsahu).
REAL = """Arašidy čili|SLADKOSTI
Camaro Mexiko Bier|ALKOHOL
MP.Stierače,FLAT,2ks|AUTO_MOTO
K.Čok. mliečna 100g|SLADKOSTI
K. pomaranč 0,33l|NAPOJE
Kuracie rezne|MASO
Old Spice stick 50ml|DROGERIA
Old Spice White 50ml|DROGERIA
Smotana 33% 250ml|MLIECNE
Cibuľa červená|OVOCIE_ZELENINA
* 2 110124 EFECTA 95|PHM
Minipizza Cheeseb.|MRAZENE_HOTOVE
Milkshake čokoláda|MLIECNE
Káv. nápoj Cappu.|NAPOJE
Tvarohový tyčinka|MLIECNE
Croissant nugátový|PECIVO
Fuet saláma 170g|MASO
štrúdľove cesto 300g|MRAZENE_HOTOVE
Movino Ca.Sauv.0,75l|ALKOHOL
Ciabatta 300 g|PECIVO
K.Gouda blok48%250g|MLIECNE
K.Camembert.120g|MLIECNE
Saguaro Karpatka|NAPOJE
Primitivo Puglia|ALKOHOL
Oriešky biel. čok.|SLADKOSTI
Mini Brownies|SLADKOSTI
Zemiaky sladké|OVOCIE_ZELENINA
Kávový nápoj Latte|NAPOJE
Tvaroh.tyč. malina|MLIECNE
Sój.omáčka150ml|TRVANLIVE
POKÉ GRANDE 440G|MRAZENE_HOTOVE"""
REAL = [tuple(l.rsplit("|",1)) for l in REAL.split("\n")]
if __name__ == "__main__":
    import sys, train as T
    m = T.train_nb(T.build_train()); rules = T.load_rules(sys.argv[1])
    ok_r = ok_a = 0
    for n, g in REAL:
        r = T.by_rules(rules, n); p, c = T.predict(m, n); ai = p if c >= 0.6 else None
        f = ai or r
        ok_r += (r == g); ok_a += (f == g)
        if f != g: print(f"  {n:28s} správne={g:15s} pravidlá={r} AI={ai}")
    print(f"pravidlá {ok_r}/{len(REAL)}   AI+pravidlá {ok_a}/{len(REAL)}")
