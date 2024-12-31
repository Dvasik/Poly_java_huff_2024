import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

class Node implements Comparable<Node> {
    char symbol;
    long frequency;
    boolean isLeaf;
    Node left, right;

    Node(char symbol, long frequency) {
        this.symbol = symbol;
        this.frequency = frequency;
        this.isLeaf = false;
    }

    Node(long frequency, Node left, Node right) {
        this.symbol = '\0';
        this.frequency = frequency;
        this.left = left;
        this.right = right;
        this.isLeaf = true;
    }

    @Override
    public int compareTo(Node other) {
        return Long.compare(this.frequency, other.frequency);
    }
}

public class Main {

    private static Map<Character, String> huffmanCodes = new HashMap<>();
    private static Map<String, Character> reverseHuffmanCodes = new HashMap<>();

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: java Main <c|d> <input file> <output file>");
            return;
        }

        String mode = args[0];
        String inputFile = args[1];
        String outputFile = args[2];

        if (mode.equals("c")) {
            compress(inputFile, outputFile);
        } else if (mode.equals("d")) {
            decompress(inputFile, outputFile);
        } else {
            System.out.println("Invalid mode. Use 'c' for compress or 'd' for decompress.");
        }
    }

    private static void compress(String inputFile, String outputFile) throws IOException {
        String content = new String(Files.readAllBytes(new File(inputFile).toPath()), StandardCharsets.ISO_8859_1);

        Map<Character, Long> frequencyMap = calculateFrequencies(content);
        Node root = buildHuffmanTree(frequencyMap);
        generateCodes(root, "");

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(outputFile))) {
            // Write frequency map size and the map entries
            dos.writeInt(frequencyMap.size());
            for (Map.Entry<Character, Long> entry : frequencyMap.entrySet()) {
                dos.writeChar(entry.getKey());
                dos.writeLong(entry.getValue());
            }

            // Create a bitset for encoded data
            BitSet bitSet = new BitSet();
            int bitIndex = 0;
            for (char c : content.toCharArray()) {
                String code = huffmanCodes.get(c);
                for (char bit : code.toCharArray()) {
                    bitSet.set(bitIndex++, bit == '1');
                }
            }
            // printCodes();

            int byteLength = (int) Math.ceil(bitIndex / 8.0);

            dos.writeInt(bitIndex);

            // Get the raw byte array from the BitSet
            byte[] rawByteArray = bitSet.toByteArray();

            // Create a new array with the required length and copy the raw bytes
            byte[] paddedByteArray = new byte[byteLength];
            System.arraycopy(rawByteArray, 0, paddedByteArray, 0, rawByteArray.length);

            // System.out.println(Arrays.toString(paddedByteArray));

            // Write the bitset itself
            dos.write(paddedByteArray);
        }
    }

    private static void decompress(String inputFile, String outputFile) throws IOException {
        try (DataInputStream dis = new DataInputStream(new FileInputStream(inputFile))) {
            // Read frequency map size and entries
            int size = dis.readInt();
            Map<Character, Long> frequencyMap = new HashMap<>();
            for (int i = 0; i < size; i++) {
                char symbol = dis.readChar();
                long frequency = dis.readLong();
                frequencyMap.put(symbol, frequency);
            }

            // Rebuild the Huffman tree
            Node root = buildHuffmanTree(frequencyMap);
            generateCodes(root, "");

            // printCodes();

            int bitLength = dis.readInt();

            // Read the encoded byte array
            byte[] encodedBytes = dis.readAllBytes();
            BitSet bitSet = new BitSet(encodedBytes.length * 8);

            // System.out.println(bitSet.toString());
            // System.out.println(bitSet.length());

            // Decode the data
            StringBuilder encodedData = new StringBuilder();
            for (int i = 0; i < encodedBytes.length; i++) {
                for (int j = 0; j < 8; j++) {
                    if (i * 8 + j < bitLength) {
                        encodedData.append(((encodedBytes[i] & (1 << j)) != 0) ? '1' : '0');
                    }
                }
            }

            char[] decodedData = decodeData(encodedData.toString(), root);
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.ISO_8859_1);
            writer.write(decodedData);
            writer.flush();
            writer.close();
        }
    }

    public static void printCodes() {
        System.out.println("Huffman Codes:");
        for (HashMap.Entry<Character, String> entry : huffmanCodes.entrySet()) {
            char symbol = entry.getKey();
            String code = entry.getValue();
            if (symbol < 32 || symbol > 126) {
                System.out.printf("Symbol: [0x%X] Code: %s%n", (int) symbol, code);
            } else {
                System.out.printf("Symbol: '%c' Code: %s%n", symbol, code);
            }
        }
    }

    private static Map<Character, Long> calculateFrequencies(String content) {
        Map<Character, Long> frequencyMap = new HashMap<>();
        for (char c : content.toCharArray()) {
            frequencyMap.put(c, frequencyMap.getOrDefault(c, 0L) + 1);
        }
        return frequencyMap;
    }

    private static Node buildHuffmanTree(Map<Character, Long> frequencyMap) {
        PriorityQueue<Node> priorityQueue = new PriorityQueue<>(Comparator.comparingLong(node -> node.frequency));

        // Создаем узлы для каждого символа и добавляем их в очередь
        for (HashMap.Entry<Character, Long> entry : frequencyMap.entrySet()) {
            priorityQueue.add(new Node(entry.getKey(), entry.getValue()));
        }

        // Построение дерева Хаффмана
        while (priorityQueue.size() > 1) {
            // Извлекаем два узла с минимальной частотой
            Node left = priorityQueue.poll();
            Node right = priorityQueue.poll();

            // Создаем новый внутренний узел с суммарной частотой
            Node parent = new Node(left.frequency + right.frequency, left, right);

            // Добавляем новый узел обратно в очередь
            priorityQueue.add(parent);
        }

        // Возвращаем корень дерева
        return priorityQueue.poll();
    }

    private static void generateCodes(Node node, String code) {
        if (node == null) return;


        if (!node.isLeaf) {
            if (Objects.equals(code, "") && node.left == null && node.right == null) {
                huffmanCodes.put(node.symbol, "0");
                return;
            }
            huffmanCodes.put(node.symbol, code);
        }


        generateCodes(node.left, code + "0");
        generateCodes(node.right, code + "1");
    }

    private static char[] decodeData(String encodedData, Node root) {
        char[] decodedData = new char[encodedData.length()];
        StringBuilder currentCode = new StringBuilder();

        for (HashMap.Entry<Character, String> entry : huffmanCodes.entrySet()) {
            reverseHuffmanCodes.put(entry.getValue(), entry.getKey());
        }

        int index = 0;
        for (char bit : encodedData.toCharArray()) {
            currentCode.append(bit);
            if (reverseHuffmanCodes.containsKey(currentCode.toString())) {
                decodedData[index++] = reverseHuffmanCodes.get(currentCode.toString());
                currentCode.setLength(0);
            }
        }

        return Arrays.copyOfRange(decodedData, 0, index);
    }
}
