# Mockingbirds
Utils for Computer Composition course, BUAA

## Mars.jar

![Mars-Splash](https://i.imgur.com/mKwkjld.png)

- BUGFIX: Fix core loop bugs for continuations and interuption
- FEATURE: Add Lua Instruction Extension
- Display Fux: do not display HI/LO value
- Display Fix: use P6 lh/lb format
- Instruction Loader: Load instruction in lua script
- OUTPUT FORMAT: change output to ise format
- BUGFIX: branch instructions now use signedext in bin code
- BUGFIX: save and load instructions now use signedext
- Output debug info in IO window.
- Add support for hex machine instructions

## mockingbird.rb

### Require

gem install nokogiri

### Usage

*template file must have <appear> tag*

`ruby -w mockingbird.rb <modulename> <template-filename> <target-filename>`

## decoder.py

### Usage
`python decoder.py -h`

### Operation list

Op:
- j - jal - beq - bne - blez - bgtz - addi
- addiu - stli - stliu - andi - ori - xori
- lui - lb - lh - lwl - lw - lbu - lhu - lwr
- sb - sh - swl - sw - swr 

Func:
- nop - jr - addu - subu

Rd:
- bltz - bgez


