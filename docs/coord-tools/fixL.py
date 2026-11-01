f='/tmp/claude-0/trial/app/src/main/java/com/centurywar/engine/L.kt'
L=open(f).read().split('\n')
out=[]
inpart=False
i=0
while i<len(L):
    l=L[i]
    if l.startswith('    private fun part'):
        if inpart:
            # close previous part before trailing comments/blank lines
            k=len(out)
            while k>0 and (out[k-1].strip()=='' or out[k-1].strip().startswith('//') or out[k-1].strip().startswith('/**') or out[k-1].strip().startswith('*')):
                k-=1
            if out[k-1].strip()!=')':
                out[k:k]=['    )','']
        inpart=True
    elif inpart and l.strip()==')' and l.startswith('    )'):
        inpart=False
    out.append(l); i+=1
open(f,'w').write('\n'.join(out))
