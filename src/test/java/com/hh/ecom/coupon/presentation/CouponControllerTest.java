package com.hh.ecom.coupon.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hh.ecom.coupon.application.CouponService;
import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponStatus;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.exception.CouponErrorCode;
import com.hh.ecom.coupon.domain.exception.CouponException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CouponController.class)
@DisplayName("CouponController 단위 테스트")
class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CouponService couponService;

    @Test
    @DisplayName("GET /coupons - 발급 가능한 쿠폰 목록 조회 성공")
    void getAvailableCoupons_Success() throws Exception {
        // Given
        List<Coupon> coupons = List.of(
                createCoupon(1L, "신규회원 할인 쿠폰", BigDecimal.valueOf(5000), 100, 50),
                createCoupon(2L, "VIP 할인 쿠폰", BigDecimal.valueOf(10000), 50, 30)
        );

        given(couponService.getAvailableCoupons()).willReturn(coupons);

        // When & Then
        mockMvc.perform(get("/coupons"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons", hasSize(2)))
                .andExpect(jsonPath("$.coupons[0].id").value(1))
                .andExpect(jsonPath("$.coupons[0].name").value("신규회원 할인 쿠폰"))
                .andExpect(jsonPath("$.coupons[0].discountAmount").value(5000))
                .andExpect(jsonPath("$.coupons[0].availableQuantity").value(50))
                .andExpect(jsonPath("$.coupons[1].id").value(2))
                .andExpect(jsonPath("$.coupons[1].name").value("VIP 할인 쿠폰"));

        verify(couponService, times(1)).getAvailableCoupons();
    }

    @Test
    @DisplayName("GET /coupons - 발급 가능한 쿠폰이 없는 경우")
    void getAvailableCoupons_Empty() throws Exception {
        // Given
        given(couponService.getAvailableCoupons()).willReturn(List.of());

        // When & Then
        mockMvc.perform(get("/coupons"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons", hasSize(0)));

        verify(couponService, times(1)).getAvailableCoupons();
    }

    @Test
    @DisplayName("POST /coupons/{couponId}/issue - 쿠폰 발급 성공")
    void issueCoupon_Success() throws Exception {
        // Given
        Long userId = 1L;
        Long couponId = 10L;
        Long couponUserId = 100L;

        Coupon coupon = createCoupon(couponId, "테스트 쿠폰", BigDecimal.valueOf(5000), 100, 99);
        CouponUser couponUser = createCouponUser(couponUserId, userId, couponId, false);

        given(couponService.issueCoupon(userId, couponId)).willReturn(couponUser);
        given(couponService.getCoupon(couponId)).willReturn(coupon);

        // When & Then
        mockMvc.perform(post("/coupons/{couponId}/issue", couponId)
                        .header("userId", userId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(couponUserId))
                .andExpect(jsonPath("$.couponId").value(couponId))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.couponName").value("테스트 쿠폰"))
                .andExpect(jsonPath("$.discountAmount").value(5000))
                .andExpect(jsonPath("$.message").value("쿠폰이 발급되었습니다."));

        verify(couponService, times(1)).issueCoupon(userId, couponId);
        verify(couponService, times(1)).getCoupon(couponId);
    }

    @Test
    @DisplayName("POST /coupons/{couponId}/issue - userId 헤더 없이 요청")
    void issueCoupon_WithoutUserId() throws Exception {
        // Given
        Long couponId = 10L;

        // When & Then - userId 헤더가 없으면 null이 전달되어 NullPointerException 발생
        mockMvc.perform(post("/coupons/{couponId}/issue", couponId))
                .andDo(print())
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("POST /coupons/{couponId}/issue - 중복 발급 시도")
    void issueCoupon_AlreadyIssued() throws Exception {
        // Given
        Long userId = 1L;
        Long couponId = 10L;

        given(couponService.issueCoupon(userId, couponId))
                .willThrow(new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED));

        // When & Then
        mockMvc.perform(post("/coupons/{couponId}/issue", couponId)
                        .header("userId", userId))
                .andDo(print())
                .andExpect(status().isConflict());

        verify(couponService, times(1)).issueCoupon(userId, couponId);
    }

    @Test
    @DisplayName("POST /coupons/{couponId}/issue - 쿠폰 소진")
    void issueCoupon_SoldOut() throws Exception {
        // Given
        Long userId = 1L;
        Long couponId = 10L;

        given(couponService.issueCoupon(userId, couponId))
                .willThrow(new CouponException(CouponErrorCode.COUPON_SOLD_OUT));

        // When & Then
        mockMvc.perform(post("/coupons/{couponId}/issue", couponId)
                        .header("userId", userId))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(couponService, times(1)).issueCoupon(userId, couponId);
    }

    @Test
    @DisplayName("POST /coupons/{couponId}/issue - 존재하지 않는 쿠폰")
    void issueCoupon_CouponNotFound() throws Exception {
        // Given
        Long userId = 1L;
        Long couponId = 99999L;

        given(couponService.issueCoupon(userId, couponId))
                .willThrow(new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

        // When & Then
        mockMvc.perform(post("/coupons/{couponId}/issue", couponId)
                        .header("userId", userId))
                .andDo(print())
                .andExpect(status().isNotFound());

        verify(couponService, times(1)).issueCoupon(userId, couponId);
    }

    @Test
    @DisplayName("GET /coupons/my - 보유 쿠폰 목록 조회 성공")
    void getMyCoupons_Success() throws Exception {
        // Given
        Long userId = 1L;

        Coupon coupon1 = createCoupon(1L, "쿠폰A", BigDecimal.valueOf(3000), 100, 50);
        Coupon coupon2 = createCoupon(2L, "쿠폰B", BigDecimal.valueOf(5000), 50, 30);

        CouponUser couponUser1 = createCouponUser(10L, userId, 1L, false);
        CouponUser couponUser2 = createCouponUser(11L, userId, 2L, false);

        List<CouponService.CouponUserWithCoupon> myCoupons = List.of(
                CouponService.CouponUserWithCoupon.of(couponUser1, coupon1),
                CouponService.CouponUserWithCoupon.of(couponUser2, coupon2)
        );

        given(couponService.getMyCoupons(userId)).willReturn(myCoupons);

        // When & Then
        mockMvc.perform(get("/coupons/my")
                        .header("userId", userId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons", hasSize(2)))
                .andExpect(jsonPath("$.coupons[0].id").value(10))
                .andExpect(jsonPath("$.coupons[0].couponName").value("쿠폰A"))
                .andExpect(jsonPath("$.coupons[0].discountAmount").value(3000))
                .andExpect(jsonPath("$.coupons[0].isUsed").value(false))
                .andExpect(jsonPath("$.coupons[1].id").value(11))
                .andExpect(jsonPath("$.coupons[1].couponName").value("쿠폰B"));

        verify(couponService, times(1)).getMyCoupons(userId);
    }

    @Test
    @DisplayName("GET /coupons/my - 보유 쿠폰이 없는 경우")
    void getMyCoupons_Empty() throws Exception {
        // Given
        Long userId = 1L;
        given(couponService.getMyCoupons(userId)).willReturn(List.of());

        // When & Then
        mockMvc.perform(get("/coupons/my")
                        .header("userId", userId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons", hasSize(0)));

        verify(couponService, times(1)).getMyCoupons(userId);
    }


    // Helper methods
    private Coupon createCoupon(Long id, String name, BigDecimal discountAmount,
                                Integer totalQuantity, Integer availableQuantity) {
        return Coupon.builder()
                .id(id)
                .name(name)
                .discountAmount(discountAmount)
                .totalQuantity(totalQuantity)
                .availableQuantity(availableQuantity)
                .status(CouponStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .version(0L)
                .build();
    }

    private CouponUser createCouponUser(Long id, Long userId, Long couponId, boolean isUsed) {
        return CouponUser.builder()
                .id(id)
                .userId(userId)
                .couponId(couponId)
                .orderId(null)
                .issuedAt(LocalDateTime.now())
                .usedAt(null)
                .expireDate(LocalDateTime.now().plusDays(30))
                .isUsed(isUsed)
                .build();
    }
}
