# Mockingbirds
Utils for Computer Composition course, BUAA

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

## Mars.jar
- OUTPUT FORMAT: change output to ise format
- BUGFIX: branch instructions now use signedext in bin code
- BUGFIX: save and load instructions now use signedext
- Output debug info in IO window.
- Add support for hex machine instructions

