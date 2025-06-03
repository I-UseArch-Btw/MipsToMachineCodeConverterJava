import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class MIPSConverterGUI {
/*
┌──────────┬───────┬───────────────┐
│ Register │ Number │ Binary Encoding │
├──────────┼───────┼───────────────┤
│   $t0    │  $8   │    01000      │
│   $sp    │  $29  │    11101      │
└──────────┴───────┴───────────────┘
 */

    // Register map with all 32 MIPS registers
    private static final Map<String, String> registerMap = new HashMap<>();
    private static final Map<String, Integer> labelMap = new HashMap<>();
    private static int currentAddress = 0x00400000;

    static {
        // $zero - $ra
        registerMap.put("$zero", "00000");
        registerMap.put("$at", "00001");
        registerMap.put("$v0", "00010");
        registerMap.put("$v1", "00011");
        registerMap.put("$a0", "00100");
        registerMap.put("$a1", "00101");
        registerMap.put("$a2", "00110");
        registerMap.put("$a3", "00111");
        registerMap.put("$t0", "01000");
        registerMap.put("$t1", "01001");
        registerMap.put("$t2", "01010");
        registerMap.put("$t3", "01011");
        registerMap.put("$t4", "01100");
        registerMap.put("$t5", "01101");
        registerMap.put("$t6", "01110");
        registerMap.put("$t7", "01111");
        registerMap.put("$s0", "10000");
        registerMap.put("$s1", "10001");
        registerMap.put("$s2", "10010");
        registerMap.put("$s3", "10011");
        registerMap.put("$s4", "10100");
        registerMap.put("$s5", "10101");
        registerMap.put("$s6", "10110");
        registerMap.put("$s7", "10111");
        registerMap.put("$t8", "11000");
        registerMap.put("$t9", "11001");
        registerMap.put("$k0", "11010");
        registerMap.put("$k1", "11011");
        registerMap.put("$gp", "11100");
        registerMap.put("$sp", "11101");
        registerMap.put("$fp", "11110");
        registerMap.put("$ra", "11111");

        // Add numeric aliases for all registers
        for (int i = 0; i < 32; i++) {
            registerMap.put("$" + i, String.format("%5s", Integer.toBinaryString(i)).replace(' ', '0'));
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MIPSConverterGUI::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("MIPS to Machine Code Converter");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);

        // Main panel with border layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Input panel
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(new JLabel("Enter MIPS Assembly Code:"), BorderLayout.NORTH);
        JTextArea inputTextArea = new JTextArea(10, 60);
        inputTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane inputScrollPane = new JScrollPane(inputTextArea);
        inputPanel.add(inputScrollPane, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel();
        JButton convertButton = new JButton("Convert to Machine Code");
        buttonPanel.add(convertButton);

        // Output panel
        JPanel outputPanel = new JPanel(new BorderLayout());
        outputPanel.add(new JLabel("Machine Code Output:"), BorderLayout.NORTH);
        JTextArea outputTextArea = new JTextArea(10, 60);
        outputTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        outputTextArea.setEditable(false);
        JScrollPane outputScrollPane = new JScrollPane(outputTextArea);
        outputPanel.add(outputScrollPane, BorderLayout.CENTER);

        // Add components to main panel
        mainPanel.add(inputPanel, BorderLayout.NORTH);
        mainPanel.add(buttonPanel, BorderLayout.CENTER);
        mainPanel.add(outputPanel, BorderLayout.SOUTH);

        // Add action listener to convert button
        convertButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String assemblyCode = inputTextArea.getText();
                String machineCode = convertAssemblyToMachineCode(assemblyCode);
                outputTextArea.setText(machineCode);
            }
        });

        frame.add(mainPanel);
        frame.pack();
        frame.setVisible(true);
    }

    private static String convertAssemblyToMachineCode(String assemblyCode) {
        // Reset address and label map for each conversion
        currentAddress = 0x00400000;
        labelMap.clear();

        // Split into lines and process in two passes
        String[] lines = assemblyCode.split("\\r?\\n");
        StringBuilder result = new StringBuilder();

        // First pass: collect labels
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Check for label (ends with colon)
            if (line.matches("^[a-zA-Z][a-zA-Z0-9_]*:.*$")) {
                int colonIndex = line.indexOf(':');
                String label = line.substring(0, colonIndex);
                labelMap.put(label, currentAddress);
                line = line.substring(colonIndex + 1).trim();
                if (line.isEmpty()) continue;
            }

            // Skip comments
            if (line.startsWith("#")) continue;

            // Increment address (4 bytes per instruction)
            currentAddress += 4;
        }

        // Second pass: process instructions
        currentAddress = 0x00400000;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Handle labels
            if (line.matches("^[a-zA-Z][a-zA-Z0-9_]*:.*$")) {
                int colonIndex = line.indexOf(':');
                line = line.substring(colonIndex + 1).trim();
                if (line.isEmpty()) continue;
            }

            // Skip comments
            if (line.startsWith("#")) continue;

            // Process instruction
            String binary = processInstruction(line);
            String hex = binaryToHex(binary);

            // Format output: address, instruction, binary, hex
            result.append(String.format("0x%08X: %-20s %s (0x%s)\n",
                    currentAddress, line, binary, hex));

            currentAddress += 4;
        }

        return result.toString();
    }

    private static String processInstruction(String line) {
/*
MIPS Assembly → Tokenization → Binary Mapping → Hex Conversion
           ↓              ↓               ↓
        Labels        Registers       Immediate Values
 */

        // Remove comments if any
        line = line.split("#")[0].trim();

        // Split into tokens (handle commas and parentheses)
        String[] tokens = line.replaceAll("[,$()]", " ").replace("$", " $").trim().split("\\s+");

        if (tokens.length == 0) return "Invalid instruction";

        String opcode = tokens[0].toLowerCase();

        try {
            switch (opcode) {
                // R-type instructions
                case "add":
                    return rType("000000", getRegister(tokens[2]), getRegister(tokens[3]),
                            getRegister(tokens[1]), "00000", "100000");
                case "sub":
                    return rType("000000", getRegister(tokens[2]), getRegister(tokens[3]),
                            getRegister(tokens[1]), "00000", "100010");
                case "and":
                    return rType("000000", getRegister(tokens[2]), getRegister(tokens[3]),
                            getRegister(tokens[1]), "00000", "100100");
                case "or":
                    return rType("000000", getRegister(tokens[2]), getRegister(tokens[3]),
                            getRegister(tokens[1]), "00000", "100101");
                case "sll":
                    return rType("000000", "00000", getRegister(tokens[2]),
                            getRegister(tokens[1]), getShamt(tokens[3]), "000000");
                case "srl":
                    return rType("000000", "00000", getRegister(tokens[2]),
                            getRegister(tokens[1]), getShamt(tokens[3]), "000010");
                case "sllv":
                    return rType("000000", getRegister(tokens[3]), getRegister(tokens[2]),
                            getRegister(tokens[1]), "00000", "000100");
                case "srlv":
                    return rType("000000", getRegister(tokens[3]), getRegister(tokens[2]),
                            getRegister(tokens[1]), "00000", "000110");

                // I-type instructions
                case "addi":
                    return iType("001000", getRegister(tokens[2]), getRegister(tokens[1]),
                            getImmediate(tokens[3]));
                case "andi":
                    return iType("001100", getRegister(tokens[2]), getRegister(tokens[1]),
                            getImmediate(tokens[3]));
                case "lw":
                    return iType("100011", getRegister(tokens[3]), getRegister(tokens[1]),
                            getImmediate(tokens[2]));
                case "sw":
                    return iType("101011", getRegister(tokens[3]), getRegister(tokens[1]),
                            getImmediate(tokens[2]));
                //branch type
                case "beq":
                    return branchType("000100", getRegister(tokens[1]), getRegister(tokens[2]),
                            tokens[3], currentAddress);
                case "bne":
                    return branchType("000101", getRegister(tokens[1]), getRegister(tokens[2]),
                            tokens[3], currentAddress);
                case "blez":
                    return branchType("000110", getRegister(tokens[1]), "00000",
                            tokens[2], currentAddress);
                case "bgtz":
                    return branchType("000111", getRegister(tokens[1]), "00000",
                            tokens[2], currentAddress);

                // J-type instructions
                case "j":
                    return jType("000010", tokens[1], currentAddress);
                case "jal":
                    return jType("000011", tokens[1], currentAddress);





                default:
                    return "Invalid instruction: " + opcode;
            }
        } catch (Exception e) {
            return "Error processing instruction: " + line + " - " + e.getMessage();
        }
    }

    private static String rType(String opcode, String rs, String rt, String rd, String shamt, String funct) {
        return opcode + rs + rt + rd + shamt + funct;
    }
/*
add $t0, $t1, $t2
000000 01001 01010 01000 00000 100000
│     │     │     │     │     │
│     │     │     │     │     └─ funct (ADD)
│     │     │     │     └─ shamt (0)
│     │     │     └─ rd ($t0)
│     │     └─ rt ($t2)
│     └─ rs ($t1)
└─ opcode (R-type)
 */

    private static String iType(String opcode, String rs, String rt, String immediate) {
        return opcode + rs + rt + immediate;
    }
/*
R-Type Format:
┌───────┬──────┬──────┬──────┬──────┬──────┐
│  op   │  rs  │  rt  │  rd  │ shamt│ funct│
└───────┴──────┴──────┴──────┴──────┴──────┘
  6-bit  5-bit  5-bit  5-bit  5-bit  6-bit

I-Type Format:
┌───────┬──────┬──────┬───────────────────┐
│  op   │  rs  │  rt  │      immediate    │
└───────┴──────┴──────┴───────────────────┘
  6-bit  5-bit  5-bit        16-bit
 */
    private static String branchType(String opcode, String rs, String rt, String label, int currentAddr) {
        if (!labelMap.containsKey(label)) {
            return "Undefined label: " + label;
        }
        int targetAddr = labelMap.get(label);
        int offset = (targetAddr - (currentAddr + 4)) / 4;
        String offsetBinary = String.format("%16s", Integer.toBinaryString(offset & 0xFFFF))
                .replace(' ', '0');
        return opcode + rs + rt + offsetBinary;
    }
/*


 */


    private static String jType(String opcode, String label, int currentAddr) {
        if (!labelMap.containsKey(label)) {
            return "Undefined label: " + label;
        }
        int targetAddr = labelMap.get(label);
        // J-type uses word address (divide by 4)
        String address = String.format("%26s", Integer.toBinaryString((targetAddr & 0x0FFFFFFF) / 4))
                .replace(' ', '0');
        return opcode + address;
    }

    private static String getRegister(String reg) {
        reg = reg.trim();
        if (!reg.startsWith("$")) {
            reg = "$" + reg; // Auto-add $ if missing
        }
        reg = reg.toLowerCase(); // Normalize to lowercase

        // Handle numeric registers ($0-$31)
        if (reg.matches("\\$\\d+")) {
            int regNum = Integer.parseInt(reg.substring(1));
            if (regNum < 0 || regNum > 31) {
                throw new IllegalArgumentException("Register number must be between 0 and 31");
            }
            return String.format("%5s", Integer.toBinaryString(regNum)).replace(' ', '0');
        }

        // Check if register exists in the map
        if (!registerMap.containsKey(reg)) {
            throw new IllegalArgumentException("Unknown register: " + reg +
                    ". Valid registers are: $zero-$ra or $0-$31");
        }

        return registerMap.get(reg);
    }
    private static String getShamt(String shamt) {
        try {
            int val = Integer.parseInt(shamt);
            if (val < 0 || val > 31) {
                throw new IllegalArgumentException("Shift amount must be between 0 and 31");
            }
            return String.format("%5s", Integer.toBinaryString(val)).replace(' ', '0');
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid shift amount: " + shamt);
        }
    }

    private static String getImmediate(String value) {
        try {
            int val = Integer.parseInt(value);
            return String.format("%16s", Integer.toBinaryString(val & 0xFFFF)).replace(' ', '0');
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid immediate value: " + value);
        }
    }

    private static String binaryToHex(String binary) {
        if (binary.length() != 32 && !binary.startsWith("Invalid") && !binary.startsWith("Error")) {
            return "Invalid binary length: " + binary.length();
        }

        if (binary.startsWith("Invalid") || binary.startsWith("Error")) {
            return binary;
        }

        try {
            long decimal = Long.parseLong(binary, 2);
            return String.format("%08X", decimal);
        } catch (NumberFormatException e) {
            return "Invalid binary: " + binary;
        }
    }
}
/*
#example code
main:
        add $t0, $t1, $t2       # R-type
        sub $s1, $s2, $s3       # R-type
        and $t3, $t4, $t5       # R-type
        or $t6, $t7, $t8        # R-type
        sll $t0, $t1, 2         # R-type with shift
        srl $t0, $t1, 3         # R-type with shift
        sllv $t1, $t2, $t3      # R-type variable shift
        srlv $t1, $t2, $t3      # R-type variable shift

        addi $s0, $s0, 10       # I-type
        andi $s1, $s1, 0xF0F0   # I-type
lw $t0, 4($sp)          # I-type
sw $t1, 8($sp)          # I-type

        beq $s0, $s1, skip      # Branch equal
        bne $s0, $s1, skip      # Branch not equal
        blez $s2, skip          # Branch less than or equal to zero
        bgtz $s3, skip          # Branch greater than zero

j end                   # J-type
jal main                # J-type

skip:
        addi $t0, $zero, 1      # Branch target

end:
        addi $v0, $zero, 10     # Exit syscall (not handled in your app but fine to test)
*/
