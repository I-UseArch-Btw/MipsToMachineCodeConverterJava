GUI-based Java application that converts MIPS assembly code to machine code. Your program receives the source code via a multiple-line text field, translates each instruction into the machine code in binary or hexadecimal, and displays them with their associated address in an output Text Field. For Simplicity, only consider the following MIPS instructions:

ADD, SUB, AND, OR, SLL, SRL, SLLV, SRLV (R-type)

ADDI, ANDI, LW, SW (I-Type)

BEQ, BNE, BLEZ, BGTZ  (I-type)

J, JAL  (J-type)

The source code contains several instructions with different types, The target addresses of BEG, BNE, and J instructions are determined by labels in the source code. The registers are given with their convenient names(not their numbers) such as $a0, $v1, $s4, ...

The start Address of the program is 0x00400000

For example, consider the following source code:

![image](https://github.com/user-attachments/assets/8d0739a8-138e-4ef4-b01d-34b22aa40abe)

GUI-based program should be something as follows:

![image](https://github.com/user-attachments/assets/d2373a5c-6983-4881-9d39-7381076ce571)

