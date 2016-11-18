.data
dat: .space 1
.text
ori	$t0, $t0, 0x1
ori	$t1, $t1, 0x0
ori	$t2, $t2, 0x2
ori	$t3, $t3, 0x4
lui	$t4, 0x9
lui	$t5, 0x6
addu	$t0, $t0, $t2
addu	$t0, $t0, $t4
subu	$t5, $t5, $t3
subu	$t5, $t5, $t1
sw	$t5, dat+0x0($t3)
ori	$t5, $t5, 0x64
lw	$t1, dat+0x0($t3)
jal	jal_0
subu	$t1, $t1, $t5
beq	$t1, $zero, beq_1
jal_0:
ori	$t1, $t1, 0x64
addu	$t2, $t1, $t1
jr	$ra
beq_1:
nop
nop
beq	$t1, $zero, beq_1