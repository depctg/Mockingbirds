ins_set = [
  'addiu REG, REG, IMM16',
  'ori REG, REG, IMM16',
  'lui REG, IMM16',
  'addu REG, REG, REG',
  'subu REG, REG, REG',
  'and REG, REG, REG',
  'or REG, REG, REG',
  'xor REG, REG, REG',
  'lw REG, ADDR',
  'sw REG, ADDR',
  'sll REG, REG, SH',
  'srl REG, REG, SH',
]
ins_unique_datapath_set = [
  'addiu REG, REG, IMM16',
  'lui REG, IMM16',
  'addu REG, REG, REG',
  'lw REG, ADDR',
  'sw REG, ADDR',
  'sll REG, REG, SH',
]
ins_unused_set = [
  'addi REG, REG, REG',
  'add REG, REG, IMM16',
  'sub REG, REG, REG',
]

def random_regs(i, *default_regs)
  default_regs.map {|r| "$#{r}"} + Array.new(i).map {"$#{rand(32)}"}
end
def random_imm16
  hexchar = '0123456789abcdef'.split('')
  '0x' + Array.new(4).map {hexchar.sample}.join
end
def random_imm32
  hexchar = '0123456789abcdef'.split('')
  '0x' + Array.new(8).map {hexchar.sample}.join
end
def random_addr
  '0x0000($0)'
end
def random_sh
  rand(31).to_s
end

def build_ins(ins, regs)
  ins = ins.dup
  loop while ins.sub! 'REG', regs.sample
  loop while ins.sub! 'IMM16', random_imm16
  loop while ins.sub! 'ADDR', random_addr
  loop while ins.sub! 'SH', random_sh
  ins
end

def build_case(ins_list, lbl)
  regs = random_regs 3
  res = regs.map {|r| "li #{r}, #{random_imm32}"}
  res += ins_list.map.with_index {|i, idx| "#{lbl}_#{idx}: #{build_ins i, regs}"}
  res.join("\n")
end

# p build_ins(ins_unique_datapath_set[0], random_regs(3))
# puts build_case(ins_unique_datapath_set, 'case')
puts ins_unique_datapath_set.permutation(5).map.with_index {|l, idx| build_case(l, "case#{idx}")}.join("\n\n")
