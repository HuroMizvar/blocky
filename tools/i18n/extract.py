import re, sys, os, json
ROOT='/home/claude/blocky/app/src/main/java/sk/miro/blocky/'
FILES=['ui/Components.kt','ui/HouseholdScreens.kt','ui/ItemsScreen.kt','ui/MainViewModel.kt','ui/ReceiptScreens.kt',
 'ui/ReportScreens.kt','ui/ScannerScreen.kt','ui/SettingsScreen.kt','MainActivity.kt','domain/Analytics.kt',
 'data/Repository.kt','data/EkasaClient.kt','data/Updater.kt','data/Backup.kt','sync/HouseholdSync.kt','sync/FirestoreRest.kt',
 'domain/FixedExpenses.kt','data/PhoneAi.kt','data/FetchWorker.kt']
def literals(src):
    """yield (start,end,template) for each "..." literal (not triple)"""
    i=0; n=len(src); out=[]
    while i<n:
        c=src[i]
        if src.startswith('//',i): i=src.find('\n',i); i=n if i<0 else i; continue
        if src.startswith('/*',i): j=src.find('*/',i); i=n if j<0 else j+2; continue
        if src.startswith('"""',i): j=src.find('"""',i+3); i=j+3; continue
        if c=="'" :
            j=i+1
            while j<n and src[j]!="'": j+= 2 if src[j]=='\\' else 1
            i=j+1; continue
        if c=='"':
            j=i+1; buf=[]
            while j<n and src[j]!='"':
                if src[j]=='\\':
                    e=src[j+1]; buf.append({'n':'\n','t':'\t','"':'"','\\':'\\','$':'$',"'":"'"}.get(e,e)); j+=2; continue
                if src[j]=='$' and j+1<n and src[j+1]=='{':
                    d=1; k=j+2
                    while d>0:
                        if src[k]=='{': d+=1
                        elif src[k]=='}': d-=1
                        elif src[k]=='"':  # nested string inside template
                            k+=1
                            while src[k]!='"': k+= 2 if src[k]=='\\' else 1
                        k+=1
                    buf.append('{}'); j=k; continue
                if src[j]=='$' and j+1<n and (src[j+1].isalpha() or src[j+1]=='_'):
                    k=j+1
                    while k<n and (src[k].isalnum() or src[k]=='_'): k+=1
                    buf.append('{}'); j=k; continue
                buf.append(src[j]); j+=1
            out.append((i,j+1,''.join(buf))); i=j+1; continue
        i+=1
    return out
SK=re.compile(r'[áäčďéíĺľňóôŕšťúýžÁÄČĎÉÍĽĹŇÓÔŔŠŤÚÝŽ]')
def visible(t, ctx):
    if not re.search(r'[A-Za-zÀ-ž]', t): return False
    if re.search(r'@Query|Regex\(|SELECT |UPDATE |INSERT|DELETE FROM|getString\(|putString|getInt\(|putInt|getBoolean|putBoolean|getLong|putLong|optString|optInt|optLong|optBoolean|\.put\(|getSharedPreferences|header\(|fieldStr|kindKey|queue\(', ctx): return False
    if SK.search(t): return True
    words=re.findall(r'[A-Za-z]{2,}', t)
    if ' ' in t.strip() and len(words)>=2: return True
    # single slovak words without diacritics e.g. "Pridať", "Spolu"? catch capitalized words
    if re.fullmatch(r'[A-Z][a-z]+( [a-z]+)*', t) : return True
    return False
res={}
for f in FILES:
    src=open(ROOT+f).read()
    lits=literals(src)
    # merge chains "a" + "b"
    k=0
    while k<len(lits):
        s,e,t=lits[k]; chain=[t]; m=k
        while m+1<len(lits) and re.fullmatch(r'\s*\+\s*', src[lits[m][1]:lits[m+1][0]]):
            m+=1; chain.append(lits[m][2])
        line_start=src.rfind('\n',0,s)+1; line_end=src.find('\n',e)
        ctx=src[line_start:line_end]
        items=[''.join(chain)] if len(chain)>1 else []
        items+=chain
        for t2 in items:
            if visible(t2, ctx): res.setdefault(t2, f)
        k=m+1
json.dump(res, open('keys.json','w'), ensure_ascii=False, indent=0)
print(len(res))
