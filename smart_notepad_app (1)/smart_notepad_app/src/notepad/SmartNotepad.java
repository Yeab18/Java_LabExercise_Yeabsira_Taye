package notepad;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

public class SmartNotepad extends JFrame {

    private JTextArea textArea;
    private JLabel statsLabel;

    public SmartNotepad() {

        setTitle("Smart Notepad");
        setSize(900, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        buildUI();

        setVisible(true);
    }

    private void buildUI() {

        textArea = new JTextArea();

        textArea.setFont(new Font("Consolas", Font.PLAIN, 18));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(textArea);

        JPanel topPanel = new JPanel();

        JButton saveButton = createButton("Save");
        JButton openButton = createButton("Open");
        JButton clearButton = createButton("Clear");
        JButton darkModeButton = createButton("Dark Mode");

        topPanel.add(saveButton);
        topPanel.add(openButton);
        topPanel.add(clearButton);
        topPanel.add(darkModeButton);

        statsLabel = new JLabel("Words: 0 | Characters: 0");

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(statsLabel, BorderLayout.SOUTH);

        saveButton.addActionListener(e -> saveFile());
        openButton.addActionListener(e -> openFile());

        clearButton.addActionListener(e -> {
            textArea.setText("");
            updateStats();
        });

        darkModeButton.addActionListener(e -> enableDarkMode());

        textArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                updateStats();
                autoEmojiFeature();
            }
        });
    }

    private JButton createButton(String text) {

        JButton button = new JButton(text);

        button.setFont(new Font("Arial", Font.BOLD, 16));
        button.setBackground(new Color(0, 120, 215));
        button.setForeground(Color.WHITE);

        return button;
    }

    private void saveFile() {

        JFileChooser chooser = new JFileChooser();

        int result = chooser.showSaveDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {

            File file = chooser.getSelectedFile();

            try (BufferedWriter writer =
                         new BufferedWriter(new FileWriter(file))) {

                writer.write(textArea.getText());

                JOptionPane.showMessageDialog(
                        this,
                        "File Saved Successfully"
                );

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void openFile() {

        JFileChooser chooser = new JFileChooser();

        int result = chooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {

            File file = chooser.getSelectedFile();

            try (BufferedReader reader =
                         new BufferedReader(new FileReader(file))) {

                textArea.read(reader, null);

                updateStats();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void enableDarkMode() {

        textArea.setBackground(new Color(30, 30, 30));
        textArea.setForeground(Color.WHITE);
        textArea.setCaretColor(Color.WHITE);
    }

    private void updateStats() {

        String text = textArea.getText();

        int characters = text.length();

        String trimmed = text.trim();

        int words = trimmed.isEmpty()
                ? 0
                : trimmed.split("\\\\s+").length;

        statsLabel.setText(
                "Words: " + words +
                " | Characters: " + characters
        );
    }

    /*
     * UNIQUE FEATURE:
     * Smart Emoji Replacer
     *
     * Automatically converts:
     * happy -> 😊
     * sad -> 😢
     * love -> ❤️
     * cool -> 😎
     */

    private void autoEmojiFeature() {

        String text = textArea.getText();

        text = text.replace("happy", "😊");
        text = text.replace("sad", "😢");
        text = text.replace("love", "❤️");
        text = text.replace("cool", "😎");

        textArea.setText(text);
    }

    public static void main(String[] args) {

        SwingUtilities.invokeLater(SmartNotepad::new);
    }
}
