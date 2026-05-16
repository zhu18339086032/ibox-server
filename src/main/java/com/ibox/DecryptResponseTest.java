package com.ibox;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibox.utils.IboxCryptoUtil;

/**
 * 加密响应解析测试
 */
public class DecryptResponseTest {

    public static void main(String[] args) {
        try {

            String decryptedJson = IboxCryptoUtil.decryptResponse(
                    "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",
                    "ckA2epmoWCZBHVf61Z4ONt+96m9MBSpXcxw6NGZIp6Nbf7PGV6H5wZGM12bNDAe+ryn6E234KdtT5m9Lnz37dSgFBxOLT/0+8oA4HTElnNKDn2/fFk+/M3lJaZqKTY6TlQV0bPrsy9wN6X+zB/da57lSRiyw27d9jspo/NDmh3g=");

            System.out.println("解密后的JSON:\n" + decryptedJson);
            System.out.println("\n========== 解析完成 ==========");

        } catch (Exception e) {
            System.err.println("解析失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
