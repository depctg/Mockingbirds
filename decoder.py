import sys
import argparse

parser = argparse.ArgumentParser(description='machine code to assembly decoder for MIPS-C ISA')
parser.add_argument('filename', metavar='FILENAME', type=str,
        help='name of file contains machine code')
parser.add_argument('-t', '--text', type=int, default=0x00003000,
        help='start address of text segment.(default 0x00003000)')
parser.add_argument('-d', '--data', type=int, default=0x00000000,
        help='start address of data segment.(default 0x00000000)')
parser.add_argument('-m', '--mars', action='store_true', default=False,
        help='generate code Assemable in Mars')
args = parser.parse_args()

tag_reg = {}

def _decode(ins):
    ins = bin(int(line,16))[2:]
    ins = ('0' * 32 + ins)[-32:]
    codes = {}
    codes['line'] = line_num
    codes['op'] = ins[0:6]
    codes['rs'] = _getRegName(ins[6:11])
    codes['rt'] = _getRegName(ins[11:16])
    codes['imm16'] = ins[16:32]
    codes['imm26'] = int(ins[10:32], 2)
    codes['rd'] = _getRegName(ins[16:21])
    codes['rd_raw'] = ins[16:21]
    codes['sh'] = ins[21:26]
    codes['func'] = ins[26:32]
    return codes

def _getRegName(reg):
    regName = {
            '00000': '$zero',
            '01000': '$t0',
            '01001': '$t1',
            '01010': '$t2',
            '01011': '$t3',
            '01100': '$t4',
            '01101': '$t5',
            '01110': '$t6',
            '01111': '$t7',
            '11111': '$ra'
            }
    if reg in regName.keys():
        return regName[reg]
    else:
        return '$' + str(int(reg, 2))

def _getSignedBin(b):
    if b[0] == '0':
        return int(b, 2)
    else:
        b2 = '1' + '0' * (len(b)-1)
        return int(b[1:],2) - int(b2, 2)

def parseSpecial(codes):
    return FuncLUT[codes['func']](codes)

def parseRegMM(codes):
    return RegMMLUT[codes['rd_raw']](codes)

def parseJ(name):
    def _parseJ(codes):
        objectLine = codes['imm26'] * 4
        if objectLine in tag_reg.keys():
            label = tag_reg[objectLine]
        else:
            label = name + '_' + str(len(tag_reg))
            tag_reg[objectLine] = label
        return name + '\t' + label
    return _parseJ

def parseJR(codes):
    return 'jr' + '\t' + codes['rs']

def parseB(name):
    def _parseB(codes):
        objectLine = _getSignedBin(codes['imm16']) * 4 + 4 + codes['line']
        if objectLine in tag_reg.keys():
            label = tag_reg[objectLine]
        else:
            label = name + '_' + str(len(tag_reg))
            tag_reg[objectLine] = label
        return name + '\t' + codes['rs'] + ', ' + codes['rt'] + ', ' + label
    return _parseB

def parseBZ(name):
    def _parseBZ(codes):
        s = parseB(name)(codes)
        l = s.split(', ')
        return l[0] + ', ' + l[2]
    return _parseBZ

def parseR(name):
    def _parseR(codes):
        return name + '\t' + codes['rd'] + ', ' + codes['rs'] + ', ' + codes['rt']
    return _parseR

def parseI(name):
    def _parseI(codes):
        return name + '\t' + codes['rt'] + ', ' + codes['rs'] + ', ' + hex(int(codes['imm16'], 2))
    return _parseI

def parseLUI(codes):
    s = parseI('lui')(codes)
    l = s.split(', ')
    return l[0] + ', ' + l[2]

def parseMem(name):
    def _parseMem(codes):
        if (args.mars):
            return name + '\t' + codes['rt'] + ', ' + 'dat+' + hex(int(codes['imm16'], 2)) + '(' + codes['rs'] + ')'
        else:
            return name + '\t' + codes['rt'] + ', ' + hex(int(codes['imm16'], 2)) + '(' + codes['rs'] + ')'
    return _parseMem

OpLUT = {
        '000000' : parseSpecial,
        '000001' : parseRegMM,
        '000010' : parseJ('j'),
        '000011' : parseJ('jal'),
        '000100' : parseB('beq'),
        '001101' : parseI('ori'),
        '001111' : parseLUI,
        '100011' : parseMem('lw'),
        '101011' : parseMem('sw')
        }
FuncLUT = {
        '000000' : lambda codes : 'nop',
        '001000' : parseJR,
        '100001' : parseR('addu'),
        '100011' : parseR('subu')
        }
RegMMLUT = {
        '00000' : parseBZ('bltz')
        }

#  Pass0
line_num = args.text;
for line in open(args.filename):
    if 'raw' in line:
        continue
    codes = _decode(line)
    OpLUT[codes['op']](codes)

    line_num = line_num + 4
#  Pass1
if args.mars:
    print('.data')
    print('dat: .space 1')
    print('.text')
line_num = args.text;
for line in open(args.filename):
    if 'raw' in line:
        continue

    codes = _decode(line)
    if line_num in tag_reg.keys():
        print(tag_reg[line_num] + ': ', end = '\n')

    print(OpLUT[codes['op']](codes))

    line_num = line_num + 4
