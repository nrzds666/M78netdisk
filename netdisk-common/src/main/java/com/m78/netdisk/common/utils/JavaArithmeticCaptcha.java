package com.m78.netdisk.common.utils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 纯 Java 实现的算术验证码，不依赖 Nashorn JS 引擎。
 * 兼容 JDK 15+（Nashorn 已移除）。
 */
public class JavaArithmeticCaptcha {

    private final int width;
    private final int height;
    private final int len;
    private final Random random = ThreadLocalRandom.current();

    private String expression;
    private int result;

    public JavaArithmeticCaptcha(int width, int height) {
        this(width, height, 2);
    }

    public JavaArithmeticCaptcha(int width, int height, int len) {
        this.width = width;
        this.height = height;
        this.len = Math.max(1, Math.min(len, 3)); // 1~3 个数字
    }

    /**
     * 生成算术表达式并计算结果
     */
    private void generateExpression() {
        int num1, num2;

        // 根据位数生成不同难度的数字
        // len=1: 0-9 加减, len=2: 1-99 加减/乘, len=3: 混合运算(两个运算符)
        if (len == 1) {
            num1 = random.nextInt(10);
            num2 = random.nextInt(10);
        } else {
            num1 = random.nextInt(90) + 1;  // 1-90
            num2 = random.nextInt(90) + 1;  // 1-90
        }

        // 随机选择运算符
        char operator;
        if (len == 1) {
            // 一位数只用加减
            operator = random.nextBoolean() ? '+' : '-';
        } else {
            // 多位数用加减乘
            int op = random.nextInt(3);
            operator = (op == 0) ? '+' : (op == 1) ? '-' : '×';
        }

        switch (operator) {
            case '+':
                result = num1 + num2;
                expression = num1 + "+" + num2;
                break;
            case '-':
                // 确保结果非负
                if (num1 < num2) {
                    int tmp = num1;
                    num1 = num2;
                    num2 = tmp;
                }
                result = num1 - num2;
                expression = num1 + "-" + num2;
                break;
            case '×':
                // 乘数缩小范围，避免结果太大
                num2 = random.nextInt(9) + 1;  // 1-9
                result = num1 * num2;
                expression = num1 + "×" + num2;
                break;
            default:
                result = 0;
                expression = "0+0";
        }
    }

    /**
     * 绘制验证码图片，返回 base64 字符串
     */
    public String toBase64() {
        generateExpression();

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        try {
            // 抗锯齿
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 背景色（白色）
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);

            // 绘制干扰线（3条随机曲线）
            g.setColor(new Color(200, 200, 200));
            for (int i = 0; i < 3; i++) {
                int x1 = random.nextInt(width);
                int y1 = random.nextInt(height);
                int x2 = random.nextInt(width);
                int y2 = random.nextInt(height);
                g.drawLine(x1, y1, x2, y2);
            }

            // 绘制算术表达式文本
            String text = expression + "=?";
            Font font = new Font("Arial", Font.BOLD, Math.max(height - 10, 20));
            g.setFont(font);

            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            int x = (width - textWidth) / 2;
            int y = (height - fm.getAscent() - fm.getDescent()) / 2 + fm.getAscent();

            // 每个字符不同颜色
            for (int i = 0; i < text.length(); i++) {
                g.setColor(new Color(
                        random.nextInt(100),
                        random.nextInt(150) + 50,
                        random.nextInt(200)
                ));
                String ch = String.valueOf(text.charAt(i));
                int charX = x + fm.stringWidth(text.substring(0, i));
                g.drawString(ch, charX, y);
            }

            // 绘制噪点（50个随机点）
            g.setColor(new Color(180, 180, 180));
            for (int i = 0; i < 50; i++) {
                int px = random.nextInt(width);
                int py = random.nextInt(height);
                g.drawRect(px, py, 1, 1);
            }

            // 边框（浅灰色）
            g.setColor(new Color(220, 220, 220));
            g.drawRect(0, 0, width - 1, height - 1);

            g.dispose();

            // 输出为 JPEG base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                ImageIO.write(image, "jpeg", baos);
            } catch (IOException e) {
                throw new RuntimeException("验证码图片生成失败", e);
            }
            byte[] imageBytes = baos.toByteArray();
            String base64 = Base64.getEncoder().encodeToString(imageBytes);

            return "data:image/jpeg;base64," + base64;

        } finally {
            g.dispose();
        }
    }

    /**
     * 获取计算结果（字符串形式）
     */
    public String text() {
        return String.valueOf(result);
    }

    /**
     * 获取算数表达式
     */
    public String getExpression() {
        return expression;
    }
}
