# usage: res.py FILE N  (reads replacement from stdin) -> replaces Nth (1-based) conflict block
#        res.py FILE N ours|theirs|both|theirs+ours
import sys,re
f,n=sys.argv[1],int(sys.argv[2]); mode=sys.argv[3] if len(sys.argv)>3 else None
s=open(f).read()
pat=re.compile(r'<<<<<<< [^\n]*\n(.*?)(?:\|\|\|\|\|\|\| [^\n]*\n.*?)?=======\n(.*?)>>>>>>> [^\n]*\n',re.S)
ms=list(pat.finditer(s)); m=ms[n-1]
o,t=m.group(1),m.group(2)
rep={'ours':o,'theirs':t,'both':o+t,'theirs+ours':t+o}.get(mode) if mode else sys.stdin.read()
s=s[:m.start()]+rep+s[m.end():]
open(f,'w').write(s); print(f"{f}: block {n}/{len(ms)} resolved")
