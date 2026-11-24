package com.smartbillingpro.ui;

import com.smartbillingpro.db.AppException;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Message boxes. Confirmation is only used for important or destructive actions. */
public final class Msg {

    private Msg() {
    }

    public static void error(Component parent, Throwable e) {
        String text;
        if (e instanceof AppException || e instanceof IllegalArgumentException) {
            text = e.getMessage();
        } else {
            text = "Something went wrong: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                    + "\n\nYour data is safe - the last action was not saved.";
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            System.err.println(sw);
        }
        JOptionPane.showMessageDialog(parent, wrap(text), "Cannot continue", JOptionPane.WARNING_MESSAGE);
    }

    public static void error(Component parent, String text) {
        JOptionPane.showMessageDialog(parent, wrap(text), "Cannot continue", JOptionPane.WARNING_MESSAGE);
    }

    public static void info(Component parent, String title, String text) {
        JOptionPane.showMessageDialog(parent, wrap(text), title, JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component parent, String title, String text, String yesLabel) {
        Object[] options = {yesLabel, "Cancel"};
        int r = JOptionPane.showOptionDialog(parent, wrap(text), title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
        return r == 0;
    }

    /** Ask for a required line of text (e.g. a reason). Returns null when cancelled. */
    public static String ask(Component parent, String title, String prompt, String initial) {
        while (true) {
            Object r = JOptionPane.showInputDialog(parent, wrap(prompt), title, JOptionPane.PLAIN_MESSAGE, null, null,
                    initial == null ? "" : initial);
            if (r == null) {
                return null;
            }
            String s = r.toString().trim();
            if (!s.isEmpty()) {
                return s;
            }
            error(parent, "This cannot be empty.");
        }
    }

    private static String wrap(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String para : text.split("\n", -1)) {
            int col = 0;
            for (String word : para.split(" ")) {
                if (col + word.length() > 70 && col > 0) {
                    sb.append('\n');
                    col = 0;
                } else if (col > 0) {
                    sb.append(' ');
                    col++;
                }
                sb.append(word);
                col += word.length();
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }
}
