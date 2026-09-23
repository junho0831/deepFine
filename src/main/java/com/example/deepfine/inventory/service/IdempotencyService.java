package com.example.deepfine.inventory.service;

import com.example.deepfine.inventory.dto.ProductResponse;
import com.example.deepfine.inventory.exception.InventoryErrorCode;
import com.example.deepfine.inventory.exception.InventoryException;
import com.example.deepfine.inventory.repository.IdempotencyRequestRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final IdempotencyRequestRepository requests;

    // 호출한 입출고와 요청 키·응답을 같은 트랜잭션에서 확정한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public ProductResponse execute(String key, String requestContent, Supplier<ProductResponse> action) {
        if (key == null) return action.get();
        if (!key.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new InventoryException(InventoryErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
        String hash = fingerprint(requestContent);
        requests.insertIfAbsent(key, hash);
        var request = requests.findForUpdate(key)
                .orElseThrow(() -> new IllegalStateException("중복 방지 요청을 찾을 수 없습니다."));
        return request.replay(hash).orElseGet(() -> {
            ProductResponse response = action.get();
            request.complete(response);
            return response;
        });
    }

    private String fingerprint(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
