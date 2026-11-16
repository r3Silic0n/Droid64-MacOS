#!/usr/bin/env python3
"""mkapp.py BASE_APP NEW_JAR VERSION OUTDIR DOC1 DOC2...  -> OUTDIR/DroiD64-macOS-VERSION.zip
Copies the signed base app, swaps the jar, sets the version, fixes the resource seal and the
launcher's ad-hoc signature slots (-1 Info.plist, -3 CodeResources)."""
import sys,os,re,shutil,hashlib,base64,struct,plistlib,subprocess,time
base,jar,ver,outdir=sys.argv[1:5]; docs=sys.argv[5:]
name='DroiD64-macOS-'+ver; root=os.path.join(outdir,name); app=os.path.join(root,'DroiD64.app'); C=app+'/Contents/'
shutil.rmtree(root,ignore_errors=True); os.makedirs(root)
shutil.copytree(base,app,symlinks=True)
oldjar=open(C+'app/droid64.jar','rb').read(); newjar=open(jar,'rb').read()
open(C+'app/droid64.jar','wb').write(newjar)
info=open(C+'Info.plist','rb').read()
info=re.sub(rb'(<key>CFBundle(?:Short)?VersionString</key><string>)[^<]*',rb'\g<1>'+ver.encode(),info)
info=re.sub(rb'(<key>CFBundleVersion</key><string>)[^<]*',rb'\g<1>'+ver.encode(),info)
open(C+'Info.plist','wb').write(info)
cr=open(C+'_CodeSignature/CodeResources','rb').read()
o=base64.b64encode(hashlib.sha256(oldjar).digest()); n=base64.b64encode(hashlib.sha256(newjar).digest())
assert cr.count(o)==1, 'jar hash not found in seal'; cr=cr.replace(o,n); open(C+'_CodeSignature/CodeResources','wb').write(cr)
b=bytearray(open(C+'MacOS/DroiD64','rb').read())
ncmds=struct.unpack_from('<I',b,16)[0]; off=32
for i in range(ncmds):
    cmd,sz=struct.unpack_from('<II',b,off)
    if cmd==0x1d: dataoff,_=struct.unpack_from('<II',b,off+8)
    off+=sz
magic,length,count=struct.unpack_from('>III',b,dataoff); k=0
for i in range(count):
    typ,o2=struct.unpack_from('>II',b,dataoff+12+i*8); cd=dataoff+o2
    if struct.unpack_from('>I',b,cd)[0]==0xfade0c02:
        hashOff=struct.unpack_from('>I',b,cd+16)[0]; hs=b[cd+36]; assert b[cd+37]==2 and hs==32
        for slot,data in ((1,info),(3,cr)): p=cd+hashOff-slot*hs; b[p:p+hs]=hashlib.sha256(data).digest(); k+=1
assert k==2
open(C+'MacOS/DroiD64','wb').write(b)
# verify seal
seal=plistlib.load(open(C+'_CodeSignature/CodeResources','rb'))['files2']
for kk,v in seal.items():
    p=C+kk
    if 'hash2' in v: assert hashlib.sha256(open(p,'rb').read()).digest()==v['hash2'], kk
    elif 'symlink' in v: assert os.readlink(p)==v['symlink'], kk
for d in docs: shutil.copy(d,root)
os.environ['TZ']='Europe/Budapest'; time.tzset()
ts=time.strftime('%Y%m%d%H%M.%S')
subprocess.check_call(['find',root,'-exec','touch','-h','-t',ts,'{}','+'])
z=os.path.join(outdir,name+'.zip')
if os.path.exists(z): os.remove(z)
subprocess.check_call(['zip','-q','-r','-y','-X',name+'.zip',name],cwd=outdir)
print('ok',z,os.path.getsize(z),'seal entries',len(seal))
