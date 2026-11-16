#!/usr/bin/env python3
"""Ad-hoc code signature for a thin arm64 Mach-O executable (CodeDirectory 0x20400, SHA-256, 4 KB pages),
with requirements, entitlements (XML + DER) and the Info.plist / CodeResources special slots.
build_superblob(binary_bytes_up_to_codelimit, ident, info, coderes, reqs, ent, der, exec_seg) -> bytes"""
import hashlib,struct
def blob(magic,payload): return struct.pack('>II',magic,8+len(payload))+payload
def code_directory(code,ident,special,exec_base,exec_limit,exec_flags,flags=0x2):
    page=4096; n=(len(code)+page-1)//page
    hashes=b''.join(hashlib.sha256(code[i*page:(i+1)*page]).digest() for i in range(n))
    nspecial=len(special)
    ident_b=ident.encode()+b'\0'
    hdr_len=88  # v0x20400 header incl. execSeg fields
    ident_off=hdr_len
    hash_off=ident_off+len(ident_b)+nspecial*32
    spec=b''.join(special[nspecial-1-i] for i in range(nspecial))  # slot -n .. -1
    total=hash_off+len(hashes)
    h=struct.pack('>IIIIIIIIIBBBBIIIIQQQQ' if False else '>IIIIIIIII', 0xfade0c02,total,0x20400,flags,hash_off,ident_off,nspecial,n,len(code))
    h+=struct.pack('>BBBBI',32,2,0,12,0)            # hashSize, hashType, platform, pageSize(2^12), spare2
    h+=struct.pack('>II',0,0)                        # scatterOffset, teamOffset (v0x20100/0x20200)
    h+=struct.pack('>IQ',0,0)                        # spare3, codeLimit64 (v0x20300)
    h+=struct.pack('>QQQ',exec_base,exec_limit,exec_flags)  # v0x20400
    assert len(h)==hdr_len,len(h)
    return h+ident_b+spec+hashes
def superblob(code,ident,info,coderes,reqs,ent,der,exec_base,exec_limit,exec_flags):
    special=[hashlib.sha256(info).digest(), hashlib.sha256(reqs).digest(), hashlib.sha256(coderes).digest() if coderes is not None else b'\0'*32,
             b'\0'*32, hashlib.sha256(ent).digest(), b'\0'*32, hashlib.sha256(der).digest()]
    cd=code_directory(code,ident,special,exec_base,exec_limit,exec_flags)
    items=[(0,cd),(2,reqs),(5,ent),(7,der),(0x10000,blob(0xfade0b01,b''))]
    off=12+8*len(items); idx=b''; body=b''
    for t,bl in items:
        idx+=struct.pack('>II',t,off+len(body)); body+=bl
    return struct.pack('>III',0xfade0cc0,12+len(idx)+len(body),len(items))+idx+body
def parse(b):
    ncmds=struct.unpack_from('<I',b,16)[0]; off=32; segs={}
    for _ in range(ncmds):
        cmd,sz=struct.unpack_from('<II',b,off)
        if cmd==0x1d: sig=(off,)+struct.unpack_from('<II',b,off+8)
        if cmd==0x19:
            name=b[off+8:off+24].split(b'\0')[0].decode(); vm,vs,fo,fs=struct.unpack_from('<QQQQ',b,off+24); segs[name]=(off,vm,vs,fo,fs)
        off+=sz
    return sig,segs
def blobs_of(b):
    (_,d,ds),_=parse(b); cnt=struct.unpack_from('>I',b,d+8)[0]; out={}
    for i in range(cnt):
        t,o=struct.unpack_from('>II',b,d+12+i*8); m,l=struct.unpack_from('>II',b,d+o); out[t]=b[d+o:d+o+l]
    return out
def resign(binary,ident,info,coderes,reqs,ent,der):
    b=bytearray(binary); (lc,dataoff,datasize),segs=parse(b)
    t=segs['__TEXT']
    # the signature size does not depend on the hashes: put the final sizes into the header first,
    # because the header is part of the first code page
    probe=superblob(bytes(b[:dataoff]),ident,info,coderes,reqs,ent,der,t[3],t[4],1)
    size=len(probe)+((-len(probe))%16)
    struct.pack_into('<II',b,lc+8,dataoff,size)
    so,vm,vs,fo,fs=segs['__LINKEDIT']; fs2=dataoff+size-fo
    struct.pack_into('<QQQQ',b,so+24,vm,(fs2+0x3fff)//0x4000*0x4000,fo,fs2)
    sb=superblob(bytes(b[:dataoff]),ident,info,coderes,reqs,ent,der,t[3],t[4],1)
    assert len(sb)==len(probe)
    sb+=b'\0'*(size-len(sb))
    return bytes(b[:dataoff])+sb
