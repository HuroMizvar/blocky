import sys
A={}
for f in ['en1.py','en2.py','en3.py']:
    g={}; exec(open(f).read(), g); A.update(g['T'])
with open(sys.argv[1],'w') as f:
    f.write('# Bločky – anglický preklad (slovensky text → anglicky). {} = premenná časť. Generuje tools/i18n/gen.py\n')
    for k,v in A.items():
        assert '\t' not in k and '\t' not in v and k.count('{}')==v.count('{}'), (k,v)
        f.write(k.replace('\\','\\\\').replace('\n','\\n')+'\t'+v.replace('\\','\\\\').replace('\n','\\n')+'\n')
print(len(A))
