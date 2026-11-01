import sys,re
s=open(sys.argv[1]).read()
for i,m in enumerate(re.finditer(r'<<<<<<< .*?>>>>>>> [^\n]*\n',s,re.S),1):
    ln=s[:m.start()].count('\n')+1
    print(f"### block {i} at line {ln}"); print(m.group(0))
