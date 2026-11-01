# addfn.py FILE BRANCH FUNCNAME : resolve remaining conflicts as ours, append FUNCNAME (with doc/@Test) from BRANCH's version before class end
import re,subprocess,sys
f,br,fn=sys.argv[1:4]
s=open(f).read()
s=re.sub(r'<<<<<<< [^\n]*\n(.*?)=======\n(.*?)>>>>>>> [^\n]*\n',lambda m:m.group(1),s,flags=re.S)
t=subprocess.run(['git','show',br+':'+f],capture_output=True,text=True).stdout
k=t.index('fun '+fn+'(')
# walk back over annotations/doc
start=t.rfind('\n\n',0,k)+2
j=t.index('{',t.index(')',k)) if True else 0
# find body opening brace: first '{' after signature line end
j=t.index('{',k)
d=0;p=j
while True:
    if t[p]=='{': d+=1
    elif t[p]=='}':
        d-=1
        if d==0: break
    p+=1
body=t[start:p+1]
if 'fun '+fn+'(' in s: print("already present?!")
c=s.rstrip().rfind('}')
s=s[:c].rstrip('\n')+"\n\n"+body+"\n}\n"
open(f,'w').write(s); print("added",fn,len(body.splitlines()),"lines")
